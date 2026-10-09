package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.network.DeviceEntry;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 월패드 (KOCOM KHN-893N 스타일).
 * 수신기 기능 + 세대통화 / 경비실 호출 + 링크된 기기 제어(가스밸브·조명·난방·환기·에어컨)
 * + 방범(비상·외출·경보) + 방문자 영상 기록 + 에너지 사용량 + 설정(소리·비밀번호·SMS·ARS·관리자).
 */
public class WallpadBlockEntity extends ReceiverBlockEntity {
    public static final int MAX_DEVICES = 32;
    public static final int LINK_RANGE = 64;
    public static final int MAX_VISITORS = 64;
    /** 비밀번호 분실 시 쓰는 번호 (설명서) */
    public static final String MASTER_PASSWORD = "15770051";
    /** 비밀번호를 정하기 전 설정 화면에 들어가는 기본 번호 (로비폰에서는 문이 열리지 않음) */
    public static final String DEFAULT_PASSWORD = "9999";

    /** 에너지 항목: 전기, 수도, 가스, 온수, 난방 */
    public static final int ENERGY_KINDS = 5;

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, String> PATTERNS = new HashMap<>();

    static {
        def("notify_notice", "1", "[01]");
        def("notify_parking", "0", "[01]");
        def("notify_parcel", "0", "[01]");
        def("touch_sound", "1", "[01]");
        def("vol_call", "10", "[1-9]|10");
        def("vol_ring", "5", "[1-9]|10");
        def("vol_system", "5", "[1-9]|10");
        for (int i = 1; i <= 5; i++) def("sms" + i, "", "\\d{0,15}");
        def("sms_on", "", "[01]{0,10}");
        for (int i = 1; i <= 3; i++) def("ars_phone" + i, "", "\\d{0,15}");
        def("ars_pw", "FFFF", "[0-9A-F]{1,8}");
        def("ars_auto", "1", "[01]");
        def("ars_use", "1", "[01]");
        def("ars_rings", "10", "[1-9]|1\\d|20");
        def("sec1", "0", "[01]");
        def("sec2", "0", "[01]");
        for (int i = 0; i < ENERGY_KINDS; i++) def("target_" + i, "0", "\\d{1,6}");
        def("admin_pw", "0000", "\\d{4,8}");
        def("guard_no", "", "\\d{0,6}");
    }

    private static void def(String key, String value, String pattern) {
        DEFAULTS.put(key, value);
        PATTERNS.put(key, pattern);
    }

    private final List<BlockPos> devices = new ArrayList<>();
    private final Map<String, String> settings = new HashMap<>();
    private final List<MissedCall> visitorLog = new ArrayList<>();
    /** 경비실기에서 등록한 택배 (경비실 이름|수령여부) */
    private final List<MissedCall> parcels = new ArrayList<>();

    private boolean emergency;
    private boolean outing;
    private int alarmTest;

    private final long[] energyCur = new long[ENERGY_KINDS];
    private final long[] energyPrev = new long[ENERGY_KINDS];
    private final long[] energyPrev2 = new long[ENERGY_KINDS];
    private long energyMonth = -1;
    private int overNotified;

    public WallpadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALLPAD.get(), pos, state);
    }

    @Override
    public DeviceRegistry.Kind kind() {
        return DeviceRegistry.Kind.WALLPAD;
    }

    public String setting(String key) {
        String v = settings.get(key);
        return v != null ? v : DEFAULTS.getOrDefault(key, "");
    }

    private int settingInt(String key) {
        try {
            return Integer.parseInt(setting(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 기기 링크

    public enum LinkResult { LINKED, UNLINKED, FULL, TOO_FAR }

    public LinkResult toggleDevice(BlockPos devicePos) {
        if (devices.remove(devicePos)) {
            setChanged();
            return LinkResult.UNLINKED;
        }
        if (devices.size() >= MAX_DEVICES) return LinkResult.FULL;
        if (!devicePos.closerThan(worldPosition, LINK_RANGE)) return LinkResult.TOO_FAR;
        devices.add(devicePos.immutable());
        setChanged();
        return LinkResult.LINKED;
    }

    @Nullable
    private DeviceBlockEntity device(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof DeviceBlockEntity d ? d : null;
    }

    private void pruneDevices() {
        if (level == null) return;
        boolean changed = false;
        Iterator<BlockPos> it = devices.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (level.isLoaded(p) && !(level.getBlockEntity(p) instanceof DeviceBlockEntity)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) setChanged();
    }

    @Override
    protected List<DeviceEntry> deviceEntries() {
        pruneDevices();
        List<DeviceEntry> out = new ArrayList<>();
        for (BlockPos p : devices) {
            DeviceBlockEntity d = device(p);
            if (d != null) {
                out.add(new DeviceEntry(p, d.getDeviceName(), d.getDeviceType().ordinal(), d.isOn(), true,
                        d.getSetTemp(), d.roomTemp(), d.getFanLevel(), d.isAway()));
            } else {
                out.add(new DeviceEntry(p, "", DeviceType.OTHER.ordinal(), false, false));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 화면 데이터

    @Override
    protected List<MissedCall> visitors() {
        return new ArrayList<>(visitorLog);
    }

    @Override
    protected Map<String, String> settingsForClient() {
        Map<String, String> m = new HashMap<>(DEFAULTS);
        m.putAll(settings);
        m.remove("admin_pw");
        m.put("emergency", emergency ? "1" : "0");
        m.put("outing", outing ? "1" : "0");
        m.put("alarm", emergency || alarmTest > 0 ? "1" : "0");
        m.put("pw_default", doorPassword.isEmpty() ? "1" : "0");
        return m;
    }

    @Override
    protected long[] energyForClient() {
        long[] out = new long[ENERGY_KINDS * 3];
        System.arraycopy(energyCur, 0, out, 0, ENERGY_KINDS);
        System.arraycopy(energyPrev, 0, out, ENERGY_KINDS, ENERGY_KINDS);
        System.arraycopy(energyPrev2, 0, out, ENERGY_KINDS * 2, ENERGY_KINDS);
        return out;
    }

    // ------------------------------------------------------------------ 조작

    @Override
    protected void handleExtra(ServerPlayer player, Action action, BlockPos target, String text) {
        String t = text == null ? "" : text.trim();
        switch (action) {
            case TOGGLE -> {
                DeviceBlockEntity d = linked(target);
                if (d != null) d.setOn(!d.isOn());
            }
            case SET_POWER -> {
                DeviceBlockEntity d = linked(target);
                if (d != null) d.setOn("1".equals(t));
            }
            case SET_TEMP -> {
                DeviceBlockEntity d = linked(target);
                if (d != null) d.adjustSetTemp(t.startsWith("-") ? -1 : 1);
            }
            case SET_LEVEL -> {
                DeviceBlockEntity d = linked(target);
                if (d != null) {
                    try {
                        d.setFanLevel(Integer.parseInt(t));
                        d.setOn(true);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            case SET_AWAY -> {
                DeviceBlockEntity d = linked(target);
                if (d != null) d.setAway("1".equals(t));
            }
            case ALL_LIGHTS_OFF, ALL_LIGHTS_ON -> {
                boolean on = action == Action.ALL_LIGHTS_ON;
                for (BlockPos p : devices) {
                    DeviceBlockEntity d = device(p);
                    if (d != null && d.getDeviceType() == DeviceType.LIGHT) d.setOn(on);
                }
                if (!on) player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".all_lights_off"), true);
            }
            case ALL_OFF -> {
                for (BlockPos p : devices) {
                    DeviceBlockEntity d = device(p);
                    if (d != null) d.setOn(false);
                }
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".all_off"), true);
            }
            case SET_SETTING -> {
                int eq = t.indexOf('=');
                if (eq <= 0) return;
                String key = t.substring(0, eq), value = t.substring(eq + 1).toUpperCase(java.util.Locale.ROOT);
                String pattern = PATTERNS.get(key);
                if (pattern == null || key.equals("admin_pw") || !value.matches(pattern)) return;
                settings.put(key, value);
                if (key.startsWith("target_")) overNotified = 0;
                setChanged();
            }
            case RESET_SOUND -> {
                for (String k : new String[]{"notify_notice", "notify_parking", "notify_parcel", "touch_sound", "vol_call", "vol_ring", "vol_system"}) {
                    settings.remove(k);
                }
                setChanged();
            }
            case EMERGENCY -> setEmergency(player, !emergency);
            case ALARM_STOP -> {
                if (emergency) setEmergency(player, false);
                alarmTest = 0;
            }
            case ALARM_TEST -> alarmTest = 60;
            case OUTING -> {
                outing = !outing;
                setChanged();
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + (outing ? ".outing_on" : ".outing_off")), true);
                if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5f, outing ? 1.4f : 1.0f);
            }
            case SAVE_VISITOR -> {
                if (callState != CallState.IDLE && !outgoing && !Intercom.isReceiverKey(incomingKey)) {
                    addVisitor(incomingKey);
                    noticeTo(player, "visitor_saved");
                }
            }
            case DELETE_VISITOR -> {
                try {
                    int i = Integer.parseInt(t);
                    if (i >= 0 && i < visitorLog.size()) visitorLog.remove(i);
                    setChanged();
                } catch (NumberFormatException ignored) {
                }
            }
            case CLEAR_VISITORS -> {
                visitorLog.clear();
                setChanged();
            }
            case UNLOCK -> {
                int c = t.indexOf(':');
                String scope = c < 0 ? "" : t.substring(0, c), pw = c < 0 ? t : t.substring(c + 1);
                boolean ok = switch (scope) {
                    case "pw" -> checkSettingsPassword(pw);
                    case "admin" -> pw.equals(setting("admin_pw")) || pw.equals(MASTER_PASSWORD);
                    default -> false;
                };
                noticeTo(player, ok ? "unlock_" + scope : "unlock_fail");
            }
            default -> {}
        }
    }

    @Nullable
    private DeviceBlockEntity linked(BlockPos target) {
        return devices.contains(target) ? device(target) : null;
    }

    private boolean checkSettingsPassword(String pw) {
        return pw.equals(doorPassword.isEmpty() ? DEFAULT_PASSWORD : doorPassword) || pw.equals(MASTER_PASSWORD);
    }

    /** 월패드에서는 "현재비밀번호|새비밀번호" 형식 */
    @Override
    protected void setDoorPassword(ServerPlayer player, String text) {
        String s = text == null ? "" : text.trim();
        int bar = s.indexOf('|');
        if (bar < 0 || !checkSettingsPassword(s.substring(0, bar))) {
            noticeTo(player, "unlock_fail");
            return;
        }
        String pw = s.substring(bar + 1);
        if (!pw.matches("\\d{4}")) {
            noticeTo(player, "pw_invalid");
            return;
        }
        doorPassword = pw.equals(DEFAULT_PASSWORD) ? "" : pw;
        setChanged();
        noticeTo(player, "pw_saved");
    }

    private void setEmergency(ServerPlayer player, boolean on) {
        if (!(level instanceof ServerLevel sl)) return;
        emergency = on;
        setChanged();
        if (on) {
            Set<BlockPos> guards = new LinkedHashSet<>(DeviceRegistry.get(sl).guards(sl, worldPosition, false));
            guards.addAll(DeviceRegistry.get(sl).guards(sl, worldPosition, true));
            for (BlockPos p : guards) {
                ReceiverBlockEntity r = receiverAt(p);
                if (r != null) r.addAlert(unit);
            }
            player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".emergency_on").withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".emergency_off"), true);
        }
    }

    // ------------------------------------------------------------------ 호출 / 방문자

    @Override
    protected void onIncoming(String key) {
        if (!Intercom.isReceiverKey(key)) addVisitor(key);
    }

    private void addVisitor(String key) {
        visitorLog.add(0, new MissedCall(key, System.currentTimeMillis()));
        while (visitorLog.size() > MAX_VISITORS) visitorLog.remove(visitorLog.size() - 1);
        setChanged();
    }

    /** 경비실기에서 유인택배 등록 → 무인택배 목록 + 안내 */
    public void addParcel(String guardLabel) {
        parcels.add(0, new MissedCall(guardLabel + "|0", System.currentTimeMillis()));
        while (parcels.size() > 30) parcels.remove(parcels.size() - 1);
        setChanged();
        pendingNotice = "parcel_arrived";
        syncScreens();
        if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    @Override
    protected List<MissedCall> records() {
        List<MissedCall> out = new ArrayList<>();
        for (MissedCall m : parcels) out.add(new MissedCall("parcel|" + m.caller(), m.dayTime()));
        return out;
    }

    @Override
    protected String preferredGuard() {
        return setting("guard_no");
    }

    @Override
    protected float ringVolume() {
        return settingInt("vol_ring") / 10f * 1.2f;
    }

    // ------------------------------------------------------------------ 틱

    @Override
    protected void tickExtra() {
        if (!(level instanceof ServerLevel sl)) return;
        long gt = sl.getGameTime();
        if (emergency || alarmTest > 0) {
            if (alarmTest > 0) alarmTest--;
            if (gt % 10 == 0) {
                float pitch = (gt / 10) % 2 == 0 ? 1.6f : 1.1f;
                sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0f, pitch);
            }
            if (alarmTest == 0 && !emergency && gt % 10 == 0) syncScreens();
        }
        if (Math.floorMod(gt + worldPosition.hashCode(), 20) != 0) return;

        long month = sl.getDayTime() / 24000L / 30L;
        if (energyMonth < 0) energyMonth = month;
        if (month != energyMonth) {
            long diff = Math.min(3, Math.max(1, month - energyMonth));
            for (long i = 0; i < diff; i++) {
                System.arraycopy(energyPrev, 0, energyPrev2, 0, ENERGY_KINDS);
                System.arraycopy(energyCur, 0, energyPrev, 0, ENERGY_KINDS);
                java.util.Arrays.fill(energyCur, 0);
            }
            energyMonth = month;
            overNotified = 0;
        }
        for (BlockPos p : devices) {
            DeviceBlockEntity d = device(p);
            if (d == null || !d.isOn()) continue;
            switch (d.getDeviceType()) {
                case LIGHT -> energyCur[0] += 6;
                case OUTLET, OTHER, DOOR_LOCK, CURTAIN -> energyCur[0] += 10;
                case VENT -> energyCur[0] += 3L * d.getFanLevel();
                case AIRCON -> energyCur[0] += 30;
                case HEATING -> {
                    if (!d.isAway()) {
                        energyCur[4] += 40;
                        energyCur[2] += 2;
                    } else {
                        energyCur[4] += 8;
                    }
                }
                case GAS -> energyCur[2] += 1;
            }
        }
        // 집에 사람이 있으면 물을 쓴다
        if (!Intercom.playersNear(sl, worldPosition, 12).isEmpty()) {
            energyCur[1] += 3;
            energyCur[3] += 1;
        }
        // 목표값 초과 알림
        for (int i = 0; i < ENERGY_KINDS; i++) {
            int target = settingInt("target_" + i);
            if (target > 0 && energyCur[i] / 1000 >= target && (overNotified & (1 << i)) == 0) {
                overNotified |= 1 << i;
                pendingNotice = "energy_over:" + i;
                syncScreens();
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ 저장

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        devices.clear();
        for (long l : tag.getLongArray("Devices")) devices.add(BlockPos.of(l));
        settings.clear();
        CompoundTag s = tag.getCompound("Settings");
        for (String k : s.getAllKeys()) if (DEFAULTS.containsKey(k)) settings.put(k, s.getString(k));
        visitorLog.clear();
        ListTag v = tag.getList("Visitors", Tag.TAG_COMPOUND);
        for (int i = 0; i < v.size(); i++) visitorLog.add(MissedCall.load(v.getCompound(i)));
        parcels.clear();
        ListTag pl = tag.getList("Parcels", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) parcels.add(MissedCall.load(pl.getCompound(i)));
        emergency = tag.getBoolean("Emergency");
        outing = tag.getBoolean("Outing");
        copy(tag.getLongArray("EnergyCur"), energyCur);
        copy(tag.getLongArray("EnergyPrev"), energyPrev);
        copy(tag.getLongArray("EnergyPrev2"), energyPrev2);
        energyMonth = tag.contains("EnergyMonth") ? tag.getLong("EnergyMonth") : -1;
        overNotified = tag.getInt("OverNotified");
    }

    private static void copy(long[] from, long[] to) {
        java.util.Arrays.fill(to, 0);
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Devices", new LongArrayTag(devices.stream().mapToLong(BlockPos::asLong).toArray()));
        CompoundTag s = new CompoundTag();
        settings.forEach(s::putString);
        tag.put("Settings", s);
        ListTag v = new ListTag();
        for (MissedCall m : visitorLog) v.add(m.save());
        tag.put("Visitors", v);
        ListTag pl = new ListTag();
        for (MissedCall m : parcels) pl.add(m.save());
        tag.put("Parcels", pl);
        tag.putBoolean("Emergency", emergency);
        tag.putBoolean("Outing", outing);
        tag.put("EnergyCur", new LongArrayTag(energyCur.clone()));
        tag.put("EnergyPrev", new LongArrayTag(energyPrev.clone()));
        tag.put("EnergyPrev2", new LongArrayTag(energyPrev2.clone()));
        tag.putLong("EnergyMonth", energyMonth);
        tag.putInt("OverNotified", overNotified);
    }
}
