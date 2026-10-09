package com.qwerty.homenet.item;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.CallerBlockEntity;
import com.qwerty.homenet.intercom.DoorControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3f;

import java.util.List;

/**
 * 도어 링커.
 * 1) 로비폰 / 도어카메라 / 도어폰을 우클릭 → 선택 (연동된 문 위치가 잠깐 표시됨)
 * 2) 문을 우클릭 → 연동 / 다시 누르면 해제
 *    "열림" 상태가 있는 블록이면 모두 됨: 나무·철 문, 다른 모드의 문, 다락문, 울타리 문 등
 * 3) 웅크리고 기기를 우클릭 → 그 기기의 연동 문 전부 해제
 * 4) 웅크리고 허공 우클릭 → 선택 초기화
 * 연동된 문은 세대(월패드·비디오폰·인터폰·경비실기)에서 문열림을 누르거나,
 * 로비폰에서 비밀번호/카드로 문이 열릴 때 문열림 시간 동안 열린다.
 */
public class DoorLinkerItem extends Item {
    private static final String TAG_POS = "Caller";
    private static final String TAG_DIM = "Dim";

    public DoorLinkerItem(Properties props) {
        super(props);
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("msg." + HomeNet.MODID + ".door_linker." + key, args);
    }

    @Nullable
    private static BlockPos selected(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POS)) return null;
        if (!tag.getString(TAG_DIM).equals(level.dimension().location().toString())) return null;
        return BlockPos.of(tag.getLong(TAG_POS));
    }

    /** 문/기기의 우클릭 동작보다 먼저 처리 (문이 열리거나 화면이 뜨지 않게) */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        return link(ctx);
    }

    private InteractionResult link(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        boolean door = DoorControl.isDoor(level.getBlockState(pos));
        if (!(be instanceof CallerBlockEntity) && !door) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();

        if (be instanceof CallerBlockEntity caller) {
            if (player.isShiftKeyDown()) {
                caller.clearDoors();
                player.displayClientMessage(msg("cleared_doors").withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.CONSUME;
            }
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(TAG_POS, pos.asLong());
            tag.putString(TAG_DIM, level.dimension().location().toString());
            player.displayClientMessage(msg("selected", caller.getLinkedDoors().size()).withStyle(ChatFormatting.AQUA), true);
            showDoors((ServerLevel) level, caller);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.5f);
            return InteractionResult.CONSUME;
        }

        BlockPos callerPos = selected(stack, level);
        CallerBlockEntity caller = callerPos != null && level.isLoaded(callerPos)
                && level.getBlockEntity(callerPos) instanceof CallerBlockEntity c ? c : null;
        if (caller == null) {
            player.displayClientMessage(msg("no_caller").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        switch (caller.toggleDoor(pos)) {
            case LINKED -> {
                player.displayClientMessage(msg("linked", caller.getLinkedDoors().size()).withStyle(ChatFormatting.GREEN), true);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.8f);
            }
            case UNLINKED -> {
                player.displayClientMessage(msg("unlinked").withStyle(ChatFormatting.YELLOW), true);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 0.8f);
            }
            case FULL -> player.displayClientMessage(msg("full", CallerBlockEntity.MAX_DOORS).withStyle(ChatFormatting.RED), true);
            case TOO_FAR -> player.displayClientMessage(msg("too_far", CallerBlockEntity.DOOR_RANGE).withStyle(ChatFormatting.RED), true);
            case NOT_DOOR -> player.displayClientMessage(msg("not_door").withStyle(ChatFormatting.RED), true);
        }
        showDoors((ServerLevel) level, caller);
        return InteractionResult.CONSUME;
    }

    /** 연동된 문 위치에 초록 입자 표시 */
    private static void showDoors(ServerLevel level, CallerBlockEntity caller) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.4f), 1.2f);
        for (BlockPos p : caller.getLinkedDoors()) {
            level.sendParticles(dust, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 12, 0.25, 0.5, 0.25, 0);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                stack.setTag(null);
                player.displayClientMessage(msg("cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG_POS);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_POS)) {
            tooltip.add(msg("tooltip_selected", BlockPos.of(tag.getLong(TAG_POS)).toShortString()).withStyle(ChatFormatting.AQUA));
        }
        for (int i = 1; i <= 4; i++) tooltip.add(msg("tooltip_" + i).withStyle(ChatFormatting.GRAY));
    }
}
