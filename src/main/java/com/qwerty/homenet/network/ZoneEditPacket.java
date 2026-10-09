package com.qwerty.homenet.network;

import com.qwerty.homenet.data.ZoneData;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.item.DashboardItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 단지 추가 / 수정 / 삭제 */
public record ZoneEditPacket(int action, int id, String name, int color, int x1, int z1, int x2, int z2) {
    public static final int ADD = 0, UPDATE = 1, DELETE = 2;
    /** 너무 큰 구역 방지 (한 변 최대) */
    public static final int MAX_SIDE = 4096;

    public static void encode(ZoneEditPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.id);
        buf.writeUtf(p.name, 64);
        buf.writeInt(p.color);
        buf.writeInt(p.x1);
        buf.writeInt(p.z1);
        buf.writeInt(p.x2);
        buf.writeInt(p.z2);
    }

    public static ZoneEditPacket decode(FriendlyByteBuf buf) {
        return new ZoneEditPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(ZoneEditPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !(player.getMainHandItem().getItem() instanceof DashboardItem
                || player.getOffhandItem().getItem() instanceof DashboardItem)) return;
        ZoneData data = ZoneData.get(player.serverLevel());
        String name = Intercom.sanitize(p.name, 32);
        if (name.isEmpty()) name = "단지";
        int color = p.color & 0xFFFFFF;
        boolean sizeOk = Math.abs(p.x2 - p.x1) <= MAX_SIDE && Math.abs(p.z2 - p.z1) <= MAX_SIDE;
        switch (p.action) {
            case ADD -> {
                if (sizeOk) data.add(name, color, p.x1, p.z1, p.x2, p.z2);
            }
            case UPDATE -> {
                if (sizeOk && data.byId(p.id) != null) data.update(new ZoneData.Zone(p.id, name, color, p.x1, p.z1, p.x2, p.z2));
            }
            case DELETE -> data.remove(p.id);
            default -> {}
        }
        DashboardItem.sendData(player, false);
    }
}
