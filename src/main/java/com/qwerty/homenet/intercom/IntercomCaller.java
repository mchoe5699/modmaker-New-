package com.qwerty.homenet.intercom;

import net.minecraft.core.BlockPos;

/**
 * 호출하는 기기 (공동현관 로비폰, 도어카메라, 도어폰).
 * 한 번 호출하면 같은 구역·같은 세대의 수신기(월패드/비디오폰/인터폰/경비실기)가 모두 울리고,
 * 가장 먼저 응답한 수신기와 통화한다.
 */
public interface IntercomCaller {
    BlockPos getBlockPos();

    /** 월패드에 표시되는 발신 위치 키 ("lobby", "front_door") */
    String callerKey();

    /** 수신기가 응답 버튼을 누름. 이미 다른 수신기가 받았으면 false */
    boolean onAnswered(BlockPos receiver);

    /** 수신기가 문열림을 누름 → 연동된 문 열기, 통화 종료 */
    void onDoorOpened(BlockPos receiver);

    /** 수신기가 거절하거나 통화를 끊음 */
    void onReceiverHangUp(BlockPos receiver, DoorStatus reason);

    void addLine(IntercomLine line);

    void syncScreens();
}
