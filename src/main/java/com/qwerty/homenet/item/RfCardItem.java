package com.qwerty.homenet.item;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 출입 카드 (RF 카드, 13.56MHz).
 * 카드마다 고유 번호가 있고, 로비폰에 대면(우클릭) 로비폰에 등록된 정보에 따라 문이 열리거나
 * 마스터/등록용 카드로 동작한다. 등록은 로비폰 관리자 메뉴 '0'번 RF 카드 설정에서 한다.
 */
public class RfCardItem extends Item {
    private static final String TAG_ID = "CardId";
    private static final Random RANDOM = new Random();

    public RfCardItem(Properties props) {
        super(props);
    }

    /** 카드 번호 (없으면 null) */
    @Nullable
    public static String cardId(ItemStack stack) {
        if (!(stack.getItem() instanceof RfCardItem)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ID)) {
            // 아직 번호가 없으면 지금 발급
            String id = String.format(Locale.ROOT, "%08X", RANDOM.nextInt());
            stack.getOrCreateTag().putString(TAG_ID, id);
            return id;
        }
        return tag.getString(TAG_ID);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && (stack.getTag() == null || !stack.getTag().contains(TAG_ID))) {
            cardId(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_ID)) {
            tooltip.add(Component.translatable("item." + HomeNet.MODID + ".rf_card.number", tag.getString(TAG_ID))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("item." + HomeNet.MODID + ".rf_card.tip").withStyle(ChatFormatting.GRAY));
    }

    /** 로비폰에 카드를 댐 */
    public static boolean tap(LobbyPhoneBlockEntity be, ItemStack stack) {
        String id = cardId(stack);
        if (id == null) return false;
        be.tapCard(id);
        return true;
    }
}
