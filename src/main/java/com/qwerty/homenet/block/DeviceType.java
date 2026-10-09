package com.qwerty.homenet.block;

import com.qwerty.homenet.HomeNet;
import net.minecraft.network.chat.Component;

/** 월패드에 표시되는 기기 종류 */
public enum DeviceType {
    LIGHT("light", false),
    OUTLET("outlet", false),
    HEATING("heating", false),
    VENT("vent", false),
    GAS("gas", true),
    DOOR_LOCK("door_lock", true),
    CURTAIN("curtain", true),
    OTHER("other", false);

    private final String key;
    /** true면 상태를 켜짐/꺼짐 대신 열림/닫힘으로 표시 */
    private final boolean openClose;

    DeviceType(String key, boolean openClose) {
        this.key = key;
        this.openClose = openClose;
    }

    public Component displayName() {
        return Component.translatable("device_type." + HomeNet.MODID + "." + key);
    }

    public Component stateName(boolean on) {
        String s = openClose ? (on ? "open" : "closed") : (on ? "on" : "off");
        return Component.translatable("device_state." + HomeNet.MODID + "." + s);
    }

    public DeviceType next() {
        DeviceType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static DeviceType byId(int id) {
        DeviceType[] v = values();
        return id >= 0 && id < v.length ? v[id] : OTHER;
    }
}
