package com.qwerty.homenet.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 차원(월드)별 세대 번호 → 월패드 위치 등록부. 로비폰에서 번호로 호출할 때 사용.
 */
public class UnitRegistry extends SavedData {
    private static final String NAME = "qwertys_homenet_units";
    private final Map<String, BlockPos> units = new HashMap<>();

    public static UnitRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(UnitRegistry::load, UnitRegistry::new, NAME);
    }

    public static UnitRegistry load(CompoundTag tag) {
        UnitRegistry reg = new UnitRegistry();
        CompoundTag map = tag.getCompound("Units");
        for (String key : map.getAllKeys()) {
            reg.units.put(key, BlockPos.of(map.getLong(key)));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag map = new CompoundTag();
        units.forEach((k, v) -> map.putLong(k, v.asLong()));
        tag.put("Units", map);
        return tag;
    }

    @Nullable
    public BlockPos lookup(String unit) {
        return units.get(normalize(unit));
    }

    public void put(String unit, BlockPos pos) {
        units.put(normalize(unit), pos.immutable());
        setDirty();
    }

    public void putIfAbsent(String unit, BlockPos pos) {
        if (!units.containsKey(normalize(unit))) put(unit, pos);
    }

    /** 해당 번호가 이 위치를 가리킬 때만 삭제 */
    public void removeIfAt(String unit, BlockPos pos) {
        String key = normalize(unit);
        if (pos.equals(units.get(key))) {
            units.remove(key);
            setDirty();
        }
    }

    /** "101-1203", "101동 1203호", "1011203" 등을 같은 키로 취급하기 위해 숫자/영문만 남긴다 */
    public static String normalize(String unit) {
        return unit.replaceAll("[^0-9A-Za-z가-힣]", "").replace("동", "").replace("호", "").toUpperCase(java.util.Locale.ROOT);
    }
}
