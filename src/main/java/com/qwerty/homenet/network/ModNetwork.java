package com.qwerty.homenet.network;

import com.qwerty.homenet.HomeNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            HomeNet.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static int id = 0;

    public static void register() {
        // 서버 → 클라이언트
        CHANNEL.messageBuilder(WallpadDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WallpadDataPacket::encode).decoder(WallpadDataPacket::decode)
                .consumerMainThread(WallpadDataPacket::handle).add();
        CHANNEL.messageBuilder(DoorStationDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DoorStationDataPacket::encode).decoder(DoorStationDataPacket::decode)
                .consumerMainThread(DoorStationDataPacket::handle).add();
        CHANNEL.messageBuilder(DeviceConfigOpenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DeviceConfigOpenPacket::encode).decoder(DeviceConfigOpenPacket::decode)
                .consumerMainThread(DeviceConfigOpenPacket::handle).add();

        // 클라이언트 → 서버
        CHANNEL.messageBuilder(WallpadActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WallpadActionPacket::encode).decoder(WallpadActionPacket::decode)
                .consumerMainThread(WallpadActionPacket::handle).add();
        CHANNEL.messageBuilder(DoorStationActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DoorStationActionPacket::encode).decoder(DoorStationActionPacket::decode)
                .consumerMainThread(DoorStationActionPacket::handle).add();
        CHANNEL.messageBuilder(DeviceConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeviceConfigPacket::encode).decoder(DeviceConfigPacket::decode)
                .consumerMainThread(DeviceConfigPacket::handle).add();
        CHANNEL.messageBuilder(DashboardDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DashboardDataPacket::encode).decoder(DashboardDataPacket::decode)
                .consumerMainThread(DashboardDataPacket::handle).add();
        CHANNEL.messageBuilder(ZoneEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ZoneEditPacket::encode).decoder(ZoneEditPacket::decode)
                .consumerMainThread(ZoneEditPacket::handle).add();
        CHANNEL.messageBuilder(GuardAnglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GuardAnglePacket::encode).decoder(GuardAnglePacket::decode)
                .consumerMainThread(GuardAnglePacket::handle).add();
        CHANNEL.messageBuilder(LobbyKeyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LobbyKeyPacket::encode).decoder(LobbyKeyPacket::decode)
                .consumerMainThread(LobbyKeyPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private ModNetwork() {}
}
