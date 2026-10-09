package com.qwerty.homenet.network;

import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import com.qwerty.homenet.intercom.Intercom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 인터폰 조작 (호출 / 종료 / 메시지) */
public record DoorStationActionPacket(BlockPos pos, int action, String text) {

    public static void encode(DoorStationActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeUtf(p.text, 256);
    }

    public static DoorStationActionPacket decode(FriendlyByteBuf buf) {
        return new DoorStationActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(DoorStationActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.level().isLoaded(p.pos) || !Intercom.inReach(player, p.pos)) return;
        if (player.level().getBlockEntity(p.pos) instanceof DoorStationBlockEntity be) {
            be.handleAction(player, DoorStationBlockEntity.Action.byId(p.action), p.text);
        }
    }
}
