package com.qwerty.homenet.network;

import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 서버 → 클라이언트: 월패드 / 비디오폰 / 인터폰 / 경비실기 화면 데이터.
 * open=true 이면 화면을 연다, false면 이미 열려 있을 때만 갱신.
 *
 * @param callerKey 걸려온 호출의 발신 키 (lobby, front_door, unit:101-1203, guard:경비실)
 * @param outgoing  이 기기가 건 호출이면 true (callState = DIALING 또는 CONNECTED)
 * @param peer      이 기기가 호출한 대상 (세대 번호 / 경비실 / 관리실)
 * @param notice    한 번만 띄우는 안내 팝업 키 (비어 있으면 없음)
 * @param settings  월패드 설정값 (소리, 방범, 외출, 비상 등)
 * @param records   경비실기 통화목록·방범목록·택배 / 월패드 택배 ("종류|내용", 시간)
 * @param energy    월패드 에너지 사용량 [이번달 5, 지난달 5, 전전달 5] (단위 1/1000)
 */
public record WallpadDataPacket(BlockPos pos, boolean open, int kind, String unit, boolean hasDoorPassword, int callState,
                                String callerKey, boolean outgoing, String peer, String notice,
                                List<DeviceEntry> devices, List<MissedCall> missed, List<IntercomLine> log,
                                List<MissedCall> visitors, Map<String, String> settings, long[] energy, List<MissedCall> records) {

    public WallpadDataPacket withNotice(String n) {
        return new WallpadDataPacket(pos, open, kind, unit, hasDoorPassword, callState, callerKey, outgoing, peer, n,
                devices, missed, log, visitors, settings, energy, records);
    }

    public String setting(String key, String def) {
        String v = settings.get(key);
        return v == null ? def : v;
    }

    public boolean flag(String key) {
        return "1".equals(settings.get(key));
    }

    public static void encode(WallpadDataPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.open);
        buf.writeVarInt(p.kind);
        buf.writeUtf(p.unit, 64);
        buf.writeBoolean(p.hasDoorPassword);
        buf.writeVarInt(p.callState);
        buf.writeUtf(p.callerKey, 64);
        buf.writeBoolean(p.outgoing);
        buf.writeUtf(p.peer, 64);
        buf.writeUtf(p.notice, 128);
        buf.writeCollection(p.devices, (b, e) -> e.write(b));
        buf.writeCollection(p.missed, (b, m) -> m.write(b));
        buf.writeCollection(p.log, (b, l) -> l.write(b));
        buf.writeCollection(p.visitors, (b, m) -> m.write(b));
        buf.writeMap(p.settings, (b, k) -> b.writeUtf(k, 32), (b, v) -> b.writeUtf(v, 64));
        buf.writeLongArray(p.energy);
        buf.writeCollection(p.records, (b, m) -> m.write(b));
    }

    public static WallpadDataPacket decode(FriendlyByteBuf buf) {
        return new WallpadDataPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(),
                buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(64), buf.readUtf(128),
                buf.readList(DeviceEntry::read), buf.readList(MissedCall::read), buf.readList(IntercomLine::read),
                buf.readList(MissedCall::read), buf.readMap(b -> b.readUtf(32), b -> b.readUtf(64)), buf.readLongArray(),
                buf.readList(MissedCall::read));
    }

    public static void handle(WallpadDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.qwerty.homenet.client.ClientPacketHandler.handleWallpad(p));
    }
}
