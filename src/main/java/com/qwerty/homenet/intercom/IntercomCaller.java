package com.qwerty.homenet.intercom;

import net.minecraft.core.BlockPos;

/**
 * 월패드를 호출할 수 있는 기기 (세대현관 도어폰, 공동현관 로비폰).
 */
public interface IntercomCaller {
    BlockPos getBlockPos();

    /** 월패드에 표시되는 발신 위치 키 ("lobby", "front_door") */
    String callerKey();

    /** 세대에서 응답함 */
    void onAnswered();

    /** 세대에서 문열기를 누름 (이후 통화는 종료된 것으로 간주) */
    void onDoorOpened();

    /** 통화 종료 / 거절 / 무응답 등 */
    void onCallEnded(DoorStatus reason);

    void addLine(IntercomLine line);

    void syncScreens();
}
