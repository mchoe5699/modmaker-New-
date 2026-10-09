package com.qwerty.homenet.item;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.data.ZoneData;
import com.qwerty.homenet.network.DashboardDataPacket;
import com.qwerty.homenet.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 홈네트워크 대시보드 (MTR 대시보드처럼).
 * 우클릭 → 지도에서 드래그로 단지(구역)를 그리고 이름을 붙인다.
 * 같은 단지 안의 기기들끼리만 연동된다.
 */
public class DashboardItem extends Item {
    public DashboardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) sendData(sp, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void sendData(ServerPlayer player, boolean open) {
        ServerLevel level = player.serverLevel();
        ModNetwork.sendTo(player, new DashboardDataPacket(open,
                new ArrayList<>(ZoneData.get(level).all()), DeviceRegistry.get(level).all()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item." + HomeNet.MODID + ".dashboard.tip").withStyle(ChatFormatting.GRAY));
    }
}
