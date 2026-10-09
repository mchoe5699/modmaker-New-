package com.qwerty.homenet.network;

import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * 서버 → 클라이언트: 월패드 화면 데이터.
 * open=true 이면 화면을 연다, false면 이미 열려 있을 때만 갱신.
 */
public record WallpadDataPacket(BlockPos pos, boolean open, int kind, String unit, boolean hasDoorPassword, int callState, String callerKey,
                                List<DeviceEntry> devices, List<MissedCall> missed, List<IntercomLine> log) {

    public static void encode(WallpadDataPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.open);
        buf.writeVarInt(p.kind);
        buf.writeUtf(p.unit, 64);
        buf.writeBoolean(p.hasDoorPassword);
        buf.writeVarInt(p.callState);
        buf.writeUtf(p.callerKey, 32);
        buf.writeCollection(p.devices, (b, e) -> e.write(b));
        buf.writeCollection(p.missed, (b, m) -> m.write(b));
        buf.writeCollection(p.log, (b, l) -> l.write(b));
    }

    public static WallpadDataPacket decode(FriendlyByteBuf buf) {
        return new WallpadDataPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readVarInt(),
                buf.readUtf(32), buf.readList(DeviceEntry::read), buf.readList(MissedCall::read),
                buf.readList(IntercomLine::read));
    }

    public static void handle(WallpadDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.qwerty.homenet.client.ClientPacketHandler.handleWallpad(p));
    }
}
