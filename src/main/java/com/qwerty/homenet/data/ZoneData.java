package com.qwerty.homenet.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 대시보드에서 그린 단지(구역). 차원별로 저장.
 * 로비폰 · 도어카메라 · 도어폰 · 월패드 · 비디오폰 · 인터폰 · 경비실기는 같은 구역 안에서만 연동된다.
 * 어느 구역에도 속하지 않은 기기들은 "구역 없음"끼리 연동된다.
 */
public class ZoneData extends SavedData {
    private static final String NAME = "qwertys_homenet_zones";

    public record Zone(int id, String name, int color, int x1, int z1, int x2, int z2) {
        public Zone {
            int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
            int az = Math.min(z1, z2), bz = Math.max(z1, z2);
            x1 = ax; x2 = bx; z1 = az; z2 = bz;
        }

        public boolean contains(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        public long area() {
            return (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(name, 64);
            buf.writeInt(color);
            buf.writeInt(x1);
            buf.writeInt(z1);
            buf.writeInt(x2);
            buf.writeInt(z2);
        }

        public static Zone read(FriendlyByteBuf buf) {
            return new Zone(buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    private int nextId = 1;

    public static ZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ZoneData::load, ZoneData::new, NAME);
    }

    public static ZoneData load(CompoundTag tag) {
        ZoneData d = new ZoneData();
        d.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompound(i);
            d.zones.add(new Zone(z.getInt("Id"), z.getString("Name"), z.getInt("Color"),
                    z.getInt("X1"), z.getInt("Z1"), z.getInt("X2"), z.getInt("Z2")));
            d.nextId = Math.max(d.nextId, z.getInt("Id") + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Zone z : zones) {
            CompoundTag c = new CompoundTag();
            c.putInt("Id", z.id());
            c.putString("Name", z.name());
            c.putInt("Color", z.color());
            c.putInt("X1", z.x1());
            c.putInt("Z1", z.z1());
            c.putInt("X2", z.x2());
            c.putInt("Z2", z.z2());
            list.add(c);
        }
        tag.put("Zones", list);
        return tag;
    }

    public List<Zone> all() {
        return zones;
    }

    @Nullable
    public Zone byId(int id) {
        for (Zone z : zones) if (z.id() == id) return z;
        return null;
    }

    /** 위치가 속한 구역 id (여러 개면 가장 작은 구역), 없으면 0 */
    public int zoneAt(BlockPos pos) {
        Zone best = null;
        for (Zone z : zones) {
            if (z.contains(pos.getX(), pos.getZ()) && (best == null || z.area() < best.area())) best = z;
        }
        return best == null ? 0 : best.id();
    }

    public Zone add(String name, int color, int x1, int z1, int x2, int z2) {
        Zone z = new Zone(nextId++, name, color, x1, z1, x2, z2);
        zones.add(z);
        setDirty();
        return z;
    }

    public void update(Zone zone) {
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).id() == zone.id()) {
                zones.set(i, zone);
                setDirty();
                return;
            }
        }
    }

    public void remove(int id) {
        if (zones.removeIf(z -> z.id() == id)) setDirty();
    }
}
