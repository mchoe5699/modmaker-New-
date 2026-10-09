package com.qwerty.homenet.intercom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 부재중 호출 기록.
 * @param caller  "lobby", "front_door", "unit:101-1203", "guard:경비실", "emergency:101-1203"
 * @param dayTime 호출 당시 월드 시간 (level.getDayTime())
 */
public record MissedCall(String caller, long dayTime) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Caller", caller);
        tag.putLong("Time", dayTime);
        return tag;
    }

    public static MissedCall load(CompoundTag tag) {
        return new MissedCall(tag.getString("Caller"), tag.getLong("Time"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(caller, 64);
        buf.writeLong(dayTime);
    }

    public static MissedCall read(FriendlyByteBuf buf) {
        return new MissedCall(buf.readUtf(64), buf.readLong());
    }
}
