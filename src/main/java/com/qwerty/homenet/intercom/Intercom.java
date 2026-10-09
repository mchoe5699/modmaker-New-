package com.qwerty.homenet.intercom;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import com.qwerty.homenet.blockentity.WallpadBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 인터폰 공용 유틸 */
public final class Intercom {
    /** 화면 동기화 / 소리 알림 범위 (블록) */
    public static final double NEAR_RANGE = 8.0;
    /** 호출 알림(액션바)을 받는 범위 */
    public static final double NOTIFY_RANGE = 24.0;
    /** 플레이어가 기기를 조작할 수 있는 최대 거리 */
    public static final double USE_RANGE = 8.0;

    private Intercom() {}

    public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double range) {
        Vec3 c = Vec3.atCenterOf(pos);
        double r2 = range * range;
        return level.players().stream().filter(p -> p.distanceToSqr(c) <= r2).collect(Collectors.toList());
    }

    public static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= USE_RANGE * USE_RANGE;
    }

    public static Component sideName(String side) {
        return Component.translatable("caller." + HomeNet.MODID + "." + side);
    }

    public static String sanitize(String text, int max) {
        String t = text == null ? "" : text.replaceAll("[\\p{Cntrl}§]", "").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * 통화 중 메시지 전달. 양쪽 기기 로그에 남기고, 양쪽 근처 플레이어에게 채팅으로 보낸다.
     */
    public static void say(ServerLevel level, WallpadBlockEntity wallpad, DoorStationBlockEntity door,
                           ServerPlayer sender, String side, String rawText) {
        String text = sanitize(rawText, IntercomLine.MAX_TEXT);
        if (text.isEmpty()) return;
        IntercomLine line = new IntercomLine(side, sender.getGameProfile().getName(), text);
        wallpad.addLine(line);
        door.addLine(line);

        Component msg = Component.translatable("chat." + HomeNet.MODID + ".intercom",
                        sideName(side), line.name(), line.text())
                .withStyle(ChatFormatting.AQUA);
        Set<ServerPlayer> targets = new LinkedHashSet<>();
        targets.addAll(playersNear(level, wallpad.getBlockPos(), NEAR_RANGE));
        targets.addAll(playersNear(level, door.getBlockPos(), NEAR_RANGE));
        for (ServerPlayer p : targets) p.sendSystemMessage(msg);

        wallpad.syncScreens();
        door.syncScreens();
    }
}
