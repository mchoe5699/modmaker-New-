package com.qwerty.homenet.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 서버 → 클라이언트: 제어 블록 설정 화면 열기 */
public record DeviceConfigOpenPacket(BlockPos pos, String name, int type, boolean on) {

    public static void encode(DeviceConfigOpenPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.name, 128);
        buf.writeVarInt(p.type);
        buf.writeBoolean(p.on);
    }

    public static DeviceConfigOpenPacket decode(FriendlyByteBuf buf) {
        return new DeviceConfigOpenPacket(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(DeviceConfigOpenPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.qwerty.homenet.client.ClientPacketHandler.handleDeviceConfig(p));
    }
}
