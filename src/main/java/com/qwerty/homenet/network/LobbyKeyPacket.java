package com.qwerty.homenet.network;

import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.intercom.Intercom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 로비폰 키 입력 (텍스트는 통화 중 메시지일 때만) */
public record LobbyKeyPacket(BlockPos pos, int key, String text) {

    public static void encode(LobbyKeyPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.key);
        buf.writeUtf(p.text, 256);
    }

    public static LobbyKeyPacket decode(FriendlyByteBuf buf) {
        return new LobbyKeyPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(LobbyKeyPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.level().isLoaded(p.pos) || !Intercom.inReach(player, p.pos)) return;
        if (p.key < 0 || p.key > LobbyPhoneBlockEntity.KEY_MESSAGE) return;
        if (player.level().getBlockEntity(p.pos) instanceof LobbyPhoneBlockEntity be) {
            be.press(player, p.key, p.text);
        }
    }
}
