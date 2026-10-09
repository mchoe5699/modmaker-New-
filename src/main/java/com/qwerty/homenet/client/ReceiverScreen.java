package com.qwerty.homenet.client;

import com.qwerty.homenet.network.WallpadDataPacket;
import net.minecraft.core.BlockPos;

/** 수신기 화면 (월패드 / 비디오폰 / 인터폰 / 경비실기) 공통 */
public interface ReceiverScreen {
    BlockPos getPos();

    void update(WallpadDataPacket data);
}
