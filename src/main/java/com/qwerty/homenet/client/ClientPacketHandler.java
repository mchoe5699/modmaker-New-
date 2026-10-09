package com.qwerty.homenet.client;

import com.qwerty.homenet.network.DeviceConfigOpenPacket;
import com.qwerty.homenet.network.DoorStationDataPacket;
import com.qwerty.homenet.network.WallpadDataPacket;
import net.minecraft.client.Minecraft;

/** 클라이언트 전용 패킷 처리 (서버에서 로드되지 않도록 분리) */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleWallpad(WallpadDataPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WallpadScreen s && s.getPos().equals(p.pos())) {
            s.update(p);
        } else if (p.open()) {
            mc.setScreen(new WallpadScreen(p));
        }
    }

    public static void handleDoorStation(DoorStationDataPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DoorStationScreen s && s.getPos().equals(p.pos())) {
            s.update(p);
        } else if (p.open()) {
            mc.setScreen(new DoorStationScreen(p));
        }
    }

    public static void handleDashboard(com.qwerty.homenet.network.DashboardDataPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.qwerty.homenet.client.dashboard.DashboardScreen s) {
            s.update(p);
        } else if (p.open()) {
            mc.setScreen(new com.qwerty.homenet.client.dashboard.DashboardScreen(p));
        }
    }

    public static void handleDeviceConfig(DeviceConfigOpenPacket p) {
        Minecraft.getInstance().setScreen(new DeviceConfigScreen(p));
    }
}
