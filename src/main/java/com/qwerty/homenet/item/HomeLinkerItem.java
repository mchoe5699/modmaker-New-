package com.qwerty.homenet.item;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.DeviceBlockEntity;
import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import com.qwerty.homenet.blockentity.ReceiverBlockEntity;
import com.qwerty.homenet.blockentity.WallpadBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 홈 링커.
 * 1) 월패드를 우클릭 → 월패드 선택
 * 2) 제어 블록 / 스마트 조명 우클릭 → 선택한 월패드에 연결 (다시 누르면 해제)
 * 3) 인터폰 우클릭 → 세대현관 도어폰으로 연결 (같은 월패드로 다시 누르면 해제 → 로비폰)
 * 웅크리고 허공 우클릭 → 선택 초기화
 */
public class HomeLinkerItem extends Item {
    private static final String TAG_POS = "Wallpad";
    private static final String TAG_DIM = "Dim";

    public HomeLinkerItem(Properties props) {
        super(props);
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("msg." + HomeNet.MODID + "." + key, args);
    }

    @Nullable
    private static BlockPos selected(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POS)) return null;
        if (!tag.getString(TAG_DIM).equals(level.dimension().location().toString())) return null;
        return BlockPos.of(tag.getLong(TAG_POS));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ReceiverBlockEntity) && !(be instanceof DeviceBlockEntity) && !(be instanceof DoorStationBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();

        if (be instanceof ReceiverBlockEntity wp) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(TAG_POS, pos.asLong());
            tag.putString(TAG_DIM, level.dimension().location().toString());
            String unit = wp.getUnit().isEmpty() ? "-" : wp.getUnit();
            player.displayClientMessage(msg("linker.selected", unit, pos.toShortString()).withStyle(ChatFormatting.AQUA), true);
            ding(level, pos, 1.5f);
            return InteractionResult.CONSUME;
        }

        BlockPos wpPos = selected(stack, level);
        ReceiverBlockEntity receiver = wpPos != null && level.isLoaded(wpPos) && level.getBlockEntity(wpPos) instanceof ReceiverBlockEntity r ? r : null;
        if (receiver == null) {
            player.displayClientMessage(msg("linker.no_wallpad").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (be instanceof DeviceBlockEntity) {
            if (!(receiver instanceof WallpadBlockEntity wallpad)) {
                player.displayClientMessage(msg("linker.need_wallpad").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            switch (wallpad.toggleDevice(pos)) {
                case LINKED -> {
                    player.displayClientMessage(msg("linker.device_linked").withStyle(ChatFormatting.GREEN), true);
                    ding(level, pos, 1.8f);
                }
                case UNLINKED -> {
                    player.displayClientMessage(msg("linker.device_unlinked").withStyle(ChatFormatting.YELLOW), true);
                    ding(level, pos, 0.8f);
                }
                case FULL -> player.displayClientMessage(msg("linker.full", WallpadBlockEntity.MAX_DEVICES).withStyle(ChatFormatting.RED), true);
                case TOO_FAR -> player.displayClientMessage(msg("linker.too_far", WallpadBlockEntity.LINK_RANGE).withStyle(ChatFormatting.RED), true);
            }
            wallpad.syncScreens();
            return InteractionResult.CONSUME;
        }

        // 도어카메라 / 도어폰: 선택한 수신기의 세대 번호로 지정 (같은 번호면 해제)
        DoorStationBlockEntity door = (DoorStationBlockEntity) be;
        String unit = receiver.getUnit();
        if (unit.isEmpty()) {
            player.displayClientMessage(msg("linker.no_unit").withStyle(ChatFormatting.RED), true);
        } else if (com.qwerty.homenet.data.DeviceRegistry.normalize(unit).equals(com.qwerty.homenet.data.DeviceRegistry.normalize(door.getUnit()))) {
            door.setUnit("");
            player.displayClientMessage(msg("linker.door_unlinked").withStyle(ChatFormatting.YELLOW), true);
            ding(level, pos, 0.8f);
        } else {
            door.setUnit(unit);
            player.displayClientMessage(msg("linker.door_linked", unit).withStyle(ChatFormatting.GREEN), true);
            ding(level, pos, 1.8f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                stack.setTag(null);
                player.displayClientMessage(msg("linker.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void ding(Level level, BlockPos pos, float pitch) {
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, pitch);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG_POS);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_POS)) {
            tooltip.add(msg("linker.tooltip_selected", BlockPos.of(tag.getLong(TAG_POS)).toShortString()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(msg("linker.tooltip_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(msg("linker.tooltip_2").withStyle(ChatFormatting.GRAY));
        tooltip.add(msg("linker.tooltip_3").withStyle(ChatFormatting.GRAY));
    }
}
