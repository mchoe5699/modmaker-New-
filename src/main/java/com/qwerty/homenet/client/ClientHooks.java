package com.qwerty.homenet.client;

import com.qwerty.homenet.client.lobby.LobbyPhoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** 공용 코드에서 호출하는 클라이언트 전용 동작 */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openLobbyPhone(BlockPos pos) {
        Minecraft.getInstance().setScreen(new LobbyPhoneScreen(pos));
    }
}
