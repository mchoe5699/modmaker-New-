package com.qwerty.homenet.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** 월패드 화면에 표시되는 기기 한 개 */
public record DeviceEntry(BlockPos pos, String name, int type, boolean on, boolean online) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, 128);
        buf.writeVarInt(type);
        buf.writeBoolean(on);
        buf.writeBoolean(online);
    }

    public static DeviceEntry read(FriendlyByteBuf buf) {
        return new DeviceEntry(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }
}
