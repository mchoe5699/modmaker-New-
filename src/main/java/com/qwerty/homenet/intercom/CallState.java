package com.qwerty.homenet.intercom;

/** 월패드 통화 상태 */
public enum CallState {
    IDLE,
    RINGING,
    CONNECTED,
    /** 이 기기에서 다른 세대/경비실을 호출하는 중 */
    DIALING;

    public static CallState byId(int id) {
        CallState[] v = values();
        return id >= 0 && id < v.length ? v[id] : IDLE;
    }
}
