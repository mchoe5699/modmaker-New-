package com.qwerty.homenet.intercom;

/** 월패드 통화 상태 */
public enum CallState {
    IDLE,
    RINGING,
    CONNECTED;

    public static CallState byId(int id) {
        CallState[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
