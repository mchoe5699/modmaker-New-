package com.qwerty.homenet.network;

import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.blockentity.DeviceBlockEntity;
import com.qwerty.homenet.intercom.Intercom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라이언트 → 서버: 제어 블록 이름/종류 저장, 테스트 켜기/끄기 */
public record DeviceConfigPacket(BlockPos pos, String name, int type, boolean toggle) {

    public static void encode(DeviceConfigPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.name, 128);
        buf.writeVarInt(p.type);
        buf.writeBoolean(p.toggle);
    }

    public static DeviceConfigPacket decode(FriendlyByteBuf buf) {
        return new DeviceConfigPacket(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(DeviceConfigPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !player.level().isLoaded(p.pos) || !Intercom.inReach(player, p.pos)) return;
        if (player.level().getBlockEntity(p.pos) instanceof DeviceBlockEntity be) {
            be.configure(p.name, DeviceType.byId(p.type));
            if (p.toggle) be.setOn(!be.isOn());
        }
    }
}
