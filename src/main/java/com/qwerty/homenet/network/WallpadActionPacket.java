package com.qwerty.homenet.network;

import com.qwerty.homenet.blockentity.ReceiverBlockEntity;
import com.qwerty.homenet.intercom.Intercom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 월패드 버튼 조작 */
public record WallpadActionPacket(BlockPos pos, int action, BlockPos target, String text) {

    public WallpadActionPacket(BlockPos pos, ReceiverBlockEntity.Action action) {
        this(pos, action.ordinal(), BlockPos.ZERO, "");
    }

    public static void encode(WallpadActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeBlockPos(p.target);
        buf.writeUtf(p.text, 256);
    }

    public static WallpadActionPacket decode(FriendlyByteBuf buf) {
        return new WallpadActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos(), buf.readUtf(256));
    }

    public static void handle(WallpadActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.level().isLoaded(p.pos) || !Intercom.inReach(player, p.pos)) return;
        if (player.level().getBlockEntity(p.pos) instanceof ReceiverBlockEntity be) {
            be.handleAction(player, ReceiverBlockEntity.Action.byId(p.action), p.target, p.text);
        }
    }
}
