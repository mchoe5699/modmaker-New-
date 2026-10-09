package com.qwerty.homenet.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 월패드 화면에 표시되는 기기 한 개.
 * setTemp/roomTemp: 난방·에어컨 설정/현재 온도, level: 환기 풍량, away: 난방 외출
 */
public record DeviceEntry(BlockPos pos, String name, int type, boolean on, boolean online,
                          int setTemp, int roomTemp, int level, boolean away) {

    public DeviceEntry(BlockPos pos, String name, int type, boolean on, boolean online) {
        this(pos, name, type, on, online, 0, 0, 1, false);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, 128);
        buf.writeVarInt(type);
        buf.writeBoolean(on);
        buf.writeBoolean(online);
        buf.writeVarInt(setTemp);
        buf.writeInt(roomTemp);
        buf.writeVarInt(level);
        buf.writeBoolean(away);
    }

    public static DeviceEntry read(FriendlyByteBuf buf) {
        return new DeviceEntry(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean());
    }
}
