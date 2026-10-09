package com.qwerty.homenet.intercom;

import com.qwerty.homenet.HomeNet;
import net.minecraft.network.chat.Component;

/** 인터폰 화면에 표시되는 상태 */
public enum DoorStatus {
    IDLE,
    CALLING,
    CONNECTED,
    OPENED,
    NO_UNIT,
    NO_SIGNAL,
    BUSY,
    NO_ANSWER,
    REJECTED,
    ENDED,
    TIMEOUT;

    public Component display(String arg) {
        return Component.translatable("door_status." + HomeNet.MODID + "." + name().toLowerCase(java.util.Locale.ROOT), arg);
    }

    public static DoorStatus byId(int id) {
        DoorStatus[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
