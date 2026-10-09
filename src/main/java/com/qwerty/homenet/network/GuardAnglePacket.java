package com.qwerty.homenet.network;

import com.qwerty.homenet.blockentity.GuardMasterBlockEntity;
import com.qwerty.homenet.intercom.Intercom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: KGP-70K 본체 각도 조절 (맨손 + Ctrl + / -) */
public record GuardAnglePacket(BlockPos pos, int delta) {

    public static void encode(GuardAnglePacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.delta);
    }

    public static GuardAnglePacket decode(FriendlyByteBuf buf) {
        return new GuardAnglePacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(GuardAnglePacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.level().isLoaded(p.pos) || !Intercom.inReach(player, p.pos)) return;
        if (!player.getMainHandItem().isEmpty()) return;
        if (player.level().getBlockEntity(p.pos) instanceof GuardMasterBlockEntity be) {
            be.adjustAngle(player, Math.max(-5, Math.min(5, p.delta)));
        }
    }
}
