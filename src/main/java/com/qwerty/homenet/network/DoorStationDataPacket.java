package com.qwerty.homenet.network;

import com.qwerty.homenet.intercom.IntercomLine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** 서버 → 클라이언트: 인터폰 화면 데이터 */
public record DoorStationDataPacket(BlockPos pos, boolean open, boolean lobby, String linkedUnit,
                                    int status, String statusArg, boolean inCall, boolean connected,
                                    List<IntercomLine> log) {

    public static void encode(DoorStationDataPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.open);
        buf.writeBoolean(p.lobby);
        buf.writeUtf(p.linkedUnit, 64);
        buf.writeVarInt(p.status);
        buf.writeUtf(p.statusArg, 64);
        buf.writeBoolean(p.inCall);
        buf.writeBoolean(p.connected);
        buf.writeCollection(p.log, (b, l) -> l.write(b));
    }

    public static DoorStationDataPacket decode(FriendlyByteBuf buf) {
        return new DoorStationDataPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(64),
                buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(),
                buf.readList(IntercomLine::read));
    }

    public static void handle(DoorStationDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.qwerty.homenet.client.ClientPacketHandler.handleDoorStation(p));
    }
}
