package com.qwerty.homenet.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 차원별 홈네트워크 기기 목록 (위치 → 종류, 세대 번호).
 * 호출 경로 찾기와 대시보드 표시에 쓴다.
 */
public class DeviceRegistry extends SavedData {
    private static final String NAME = "qwertys_homenet_devices";

    /** 기기 종류 */
    public enum Kind {
        WALLPAD(true), VIDEO_PHONE(true), INTERPHONE(true), GUARD_CONSOLE(true),
        LOBBY_PHONE(false), DOOR_CAMERA(false), DOOR_PHONE(false);

        public final boolean receiver;

        Kind(boolean receiver) {
            this.receiver = receiver;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind byId(int id) {
            Kind[] v = values();
            return id >= 0 && id < v.length ? v[id] : WALLPAD;
        }
    }

    public record Entry(BlockPos pos, Kind kind, String unit) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(kind.ordinal());
            buf.writeUtf(unit, 64);
        }

        public static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readBlockPos(), Kind.byId(buf.readVarInt()), buf.readUtf(64));
        }
    }

    private final Map<Long, Entry> devices = new LinkedHashMap<>();

    public static DeviceRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DeviceRegistry::load, DeviceRegistry::new, NAME);
    }

    public static DeviceRegistry load(CompoundTag tag) {
        DeviceRegistry r = new DeviceRegistry();
        ListTag list = tag.getList("Devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            BlockPos p = BlockPos.of(c.getLong("Pos"));
            r.devices.put(p.asLong(), new Entry(p, Kind.byId(c.getInt("Kind")), c.getString("Unit")));
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : devices.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", e.pos().asLong());
            c.putInt("Kind", e.kind().ordinal());
            c.putString("Unit", e.unit());
            list.add(c);
        }
        tag.put("Devices", list);
        return tag;
    }

    public void register(BlockPos pos, Kind kind, String unit) {
        Entry old = devices.get(pos.asLong());
        Entry e = new Entry(pos.immutable(), kind, unit == null ? "" : unit);
        if (!e.equals(old)) {
            devices.put(pos.asLong(), e);
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (devices.remove(pos.asLong()) != null) setDirty();
    }

    public List<Entry> all() {
        return new ArrayList<>(devices.values());
    }

    /** 같은 구역에서 세대 번호가 같은 수신기(월패드/비디오폰/인터폰/경비실기) 위치 */
    public List<BlockPos> receivers(ServerLevel level, BlockPos from, String unit) {
        ZoneData zones = ZoneData.get(level);
        int zone = zones.zoneAt(from);
        String key = normalize(unit);
        List<BlockPos> out = new ArrayList<>();
        if (key.isEmpty()) return out;
        for (Entry e : devices.values()) {
            if (e.kind().receiver && normalize(e.unit()).equals(key) && zones.zoneAt(e.pos()) == zone) out.add(e.pos());
        }
        return out;
    }

    /**
     * 같은 구역의 경비실기 위치.
     * office=true → 세대 번호에 "관리"가 들어간 경비실기(관리실), false → 나머지(경비실).
     * 해당하는 기기가 하나도 없으면 같은 구역의 모든 경비실기.
     */
    public List<BlockPos> guards(ServerLevel level, BlockPos from, boolean office) {
        ZoneData zones = ZoneData.get(level);
        int zone = zones.zoneAt(from);
        List<BlockPos> all = new ArrayList<>(), match = new ArrayList<>();
        for (Entry e : devices.values()) {
            if (e.kind() != Kind.GUARD_CONSOLE || zones.zoneAt(e.pos()) != zone) continue;
            all.add(e.pos());
            if (e.unit().contains("관리") == office) match.add(e.pos());
        }
        return match.isEmpty() ? all : match;
    }

    /** "101-1203", "101동 1203호", "1011203" 을 같은 세대로 본다 */
    public static String normalize(String unit) {
        return unit.replaceAll("[^0-9A-Za-z가-힣]", "").replace("동", "").replace("호", "").toUpperCase(Locale.ROOT);
    }
}
