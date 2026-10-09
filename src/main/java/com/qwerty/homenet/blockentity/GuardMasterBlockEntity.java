package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.GuardMasterBlock;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KOCOM ASTRO KGP-70K 경비실기 (설명서 기능).
 * - 세대 번호 = 경비 주소(00~99, 관리자모드에서 설정, 99 = 통합관리실)
 * - 세대 / 경비실·관리실 / 공동현관 호출, 문열림(통화 중 공동현관 문, 아니면 DC 접점 = 레드스톤)
 * - 부재 설정: 부재 중 걸려온 호출을 지정한 경비실로 넘김 / 우회 등록: 통화 중일 때 넘김
 * - 방범목록 (세대 비상, 최대 100), 통화목록, 유인택배 (세대 월패드로 도착 알림)
 */
public class GuardMasterBlockEntity extends ReceiverBlockEntity {
    public static final String ADMIN_PASSWORD = "56266";
    public static final int MAX_HISTORY = 40, MAX_ALERTS = 100, MAX_PARCELS = 40;

    /** 본체 기울기 (도) - 맨손 + Ctrl + / - 로 1도씩 */
    public static final int MIN_ANGLE = 0, MAX_ANGLE = 60, DEFAULT_ANGLE = 22;
    private int angle = DEFAULT_ANGLE;

    private boolean absent;
    private String absentFwd = "";
    private String busyFwd = "";
    private final Map<String, String> settings = new HashMap<>();
    /** "in|키", "out|키", "miss|키", "fwd|키" */
    private final List<MissedCall> history = new ArrayList<>();
    /** 세대번호|확인여부 */
    private final List<MissedCall> alerts = new ArrayList<>();
    /** 세대번호|수령여부 */
    private final List<MissedCall> parcels = new ArrayList<>();

    public GuardMasterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUARD_MASTER.get(), pos, state);
        unit = "1";
    }

    @Override
    public DeviceRegistry.Kind kind() {
        return DeviceRegistry.Kind.GUARD_CONSOLE;
    }

    @Override
    public String callerKey() {
        return "guard:" + guardLabel();
    }

    /** "50번 경비실" / "통합관리실" */
    public String guardLabel() {
        String d = DeviceRegistry.digits(unit);
        if (d.isEmpty()) return unit;
        if (d.equals("99")) return "통합관리실";
        return d + "번 경비실";
    }

    // ------------------------------------------------------------------ 호출 받기 (부재 / 통화중 우회)

    @Nullable
    @Override
    protected BlockPos ring(BlockPos from, String key, int depth) {
        if (!(level instanceof ServerLevel sl)) return null;
        String to = absent ? absentFwd : isBusy() ? busyFwd : "";
        if (!to.isEmpty() && depth < 4) {
            for (BlockPos p : DeviceRegistry.get(sl).guardsByNumber(sl, worldPosition, to)) {
                if (p.equals(worldPosition) || p.equals(from)) continue;
                ReceiverBlockEntity r = receiverAt(p);
                BlockPos at = r == null ? null : r.ring(from, key, depth + 1);
                if (at != null) {
                    addHistory("fwd|" + key);
                    return at;
                }
            }
        }
        if (absent) {
            addHistory("miss|" + key);
            missed.add(0, new MissedCall(key, sl.getDayTime()));
            while (missed.size() > MAX_MISSED) missed.remove(missed.size() - 1);
            setChanged();
            return null;
        }
        return super.ring(from, key, depth);
    }

    @Override
    protected void onIncoming(String key) {
        addHistory("in|" + key);
    }

    @Override
    protected void onCallStarted(String label) {
        addHistory("out|" + label);
    }

    @Override
    protected void addMissed() {
        super.addMissed();
        addHistory("miss|" + incomingKey);
    }

    @Override
    public void addAlert(String fromUnit) {
        alerts.add(0, new MissedCall(fromUnit + "|0", System.currentTimeMillis()));
        while (alerts.size() > MAX_ALERTS) alerts.remove(alerts.size() - 1);
        super.addAlert(fromUnit);
    }

    private void addHistory(String entry) {
        history.add(0, new MissedCall(entry.length() > 60 ? entry.substring(0, 60) : entry, System.currentTimeMillis()));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        setChanged();
    }

    // ------------------------------------------------------------------ 화면 데이터

    @Override
    protected Map<String, String> settingsForClient() {
        Map<String, String> m = new HashMap<>();
        m.put("vol_system", "5");
        m.put("vol_call", "8");
        m.putAll(settings);
        m.put("absent", absent ? "1" : "0");
        m.put("absent_fwd", absentFwd);
        m.put("busy_fwd", busyFwd);
        m.put("guard_label", guardLabel());
        m.put("pw_default", doorPassword.isEmpty() ? "1" : "0");
        return m;
    }

    @Override
    protected List<MissedCall> records() {
        List<MissedCall> out = new ArrayList<>();
        for (MissedCall m : history) out.add(new MissedCall("hist|" + m.caller(), m.dayTime()));
        for (MissedCall m : alerts) out.add(new MissedCall("alert|" + m.caller(), m.dayTime()));
        for (MissedCall m : parcels) out.add(new MissedCall("parcel|" + m.caller(), m.dayTime()));
        return out;
    }

    // ------------------------------------------------------------------ 조작

    @Override
    protected void handleExtra(ServerPlayer player, Action action, BlockPos target, String text) {
        String t = text == null ? "" : text.trim();
        String num = t.replaceAll("[^0-9]", "");
        switch (action) {
            case PICKUP -> {
                // 송수화기: 울리면 받고, 통화 중이면 내려놓기
                if (callState == CallState.RINGING && !outgoing) answer();
                else if (callState != CallState.IDLE) hangUp();
            }
            case GUARD_DOOR -> {
                if (callState == CallState.CONNECTED && !outgoing && !Intercom.isReceiverKey(incomingKey)) {
                    openDoor();
                } else if (level != null) {
                    // DC 접점: 3초간 레드스톤
                    BlockState s = getBlockState();
                    if (s.hasProperty(GuardMasterBlock.POWERED)) {
                        level.setBlock(worldPosition, s.setValue(GuardMasterBlock.POWERED, true), Block.UPDATE_ALL);
                        level.scheduleTick(worldPosition, s.getBlock(), 60);
                    }
                    level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5f, 1.6f);
                }
            }
            case ABSENT_SET -> {
                if (num.equals(DeviceRegistry.digits(unit))) {
                    noticeTo(player, "fwd_self");
                    return;
                }
                absent = true;
                absentFwd = num;
                setChanged();
                noticeTo(player, "absent_on");
            }
            case ABSENT_CLEAR -> {
                absent = false;
                setChanged();
                noticeTo(player, "absent_off");
            }
            case BUSY_FWD_SET -> {
                if (num.isEmpty() || num.equals(DeviceRegistry.digits(unit))) {
                    noticeTo(player, num.isEmpty() ? "fwd_need" : "fwd_self");
                    return;
                }
                busyFwd = num;
                setChanged();
                noticeTo(player, "busy_fwd_on");
            }
            case BUSY_FWD_CLEAR -> {
                busyFwd = "";
                setChanged();
                noticeTo(player, "busy_fwd_off");
            }
            case CONFIRM_ALERT -> {
                int i = parse(t);
                if (i >= 0 && i < alerts.size()) {
                    MissedCall m = alerts.get(i);
                    String u = m.caller().substring(0, Math.max(0, m.caller().lastIndexOf('|')));
                    alerts.set(i, new MissedCall(u + "|1", m.dayTime()));
                    setChanged();
                }
            }
            case CLEAR_RECORDS -> {
                switch (t) {
                    case "hist" -> history.clear();
                    case "alert" -> alerts.clear();
                    case "parcel" -> parcels.clear();
                    default -> {}
                }
                setChanged();
            }
            case PARCEL_ADD -> addParcel(player, t);
            case PARCEL_DONE -> {
                int i = parse(t);
                if (i >= 0 && i < parcels.size()) {
                    MissedCall m = parcels.get(i);
                    String u = m.caller().substring(0, Math.max(0, m.caller().lastIndexOf('|')));
                    boolean done = m.caller().endsWith("|1");
                    parcels.set(i, new MissedCall(u + (done ? "|0" : "|1"), m.dayTime()));
                    setChanged();
                }
            }
            case SET_SETTING -> {
                int eq = t.indexOf('=');
                if (eq <= 0) return;
                String k = t.substring(0, eq), v = t.substring(eq + 1);
                if ((k.equals("vol_system") || k.equals("vol_call")) && v.matches("[1-9]|10")) {
                    settings.put(k, v);
                    setChanged();
                }
            }
            case RESET_SOUND -> {
                settings.remove("vol_system");
                settings.remove("vol_call");
                setChanged();
            }
            case UNLOCK -> {
                boolean ok = t.equals("admin:" + ADMIN_PASSWORD)
                        || t.equals("pw:" + (doorPassword.isEmpty() ? "9999" : doorPassword));
                noticeTo(player, ok ? (t.startsWith("admin:") ? "unlock_admin" : "unlock_pw") : "unlock_fail");
            }
            default -> {}
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 경비 주소 (00~99) */
    @Override
    public void setUnit(ServerPlayer player, String raw) {
        String d = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (d.isEmpty() || d.length() > 2) {
            noticeTo(player, "addr_invalid");
            return;
        }
        super.setUnit(player, String.valueOf(Integer.parseInt(d)));
    }

    /** "현재비밀번호|새비밀번호" (설명서: 해당 경비실의 공동현관 비밀번호) */
    @Override
    protected void setDoorPassword(ServerPlayer player, String text) {
        String s = text == null ? "" : text.trim();
        int bar = s.indexOf('|');
        String cur = doorPassword.isEmpty() ? "9999" : doorPassword;
        if (bar < 0 || !s.substring(0, bar).equals(cur)) {
            noticeTo(player, "unlock_fail");
            return;
        }
        String pw = s.substring(bar + 1);
        if (!pw.matches("\\d{4}")) {
            noticeTo(player, "pw_invalid");
            return;
        }
        doorPassword = pw.equals("9999") ? "" : pw;
        setChanged();
        noticeTo(player, "pw_saved");
    }

    private void addParcel(ServerPlayer player, String raw) {
        String u = Intercom.sanitize(raw, MAX_UNIT);
        if (!(level instanceof ServerLevel sl) || DeviceRegistry.normalize(u).isEmpty()) {
            noticeTo(player, "bad_unit");
            return;
        }
        parcels.add(0, new MissedCall(u + "|0", System.currentTimeMillis()));
        while (parcels.size() > MAX_PARCELS) parcels.remove(parcels.size() - 1);
        setChanged();
        int sent = 0;
        for (BlockPos p : DeviceRegistry.get(sl).receivers(sl, worldPosition, u)) {
            ReceiverBlockEntity r = receiverAt(p);
            if (r instanceof WallpadBlockEntity w) {
                w.addParcel(guardLabel());
                sent++;
            }
        }
        noticeTo(player, sent > 0 ? "parcel_sent" : "parcel_saved");
    }

    // ------------------------------------------------------------------ 틱

    @Override
    protected float ringVolume() {
        try {
            return Integer.parseInt(settings.getOrDefault("vol_system", "5")) / 10f * 1.2f;
        } catch (NumberFormatException e) {
            return 0.6f;
        }
    }

    @Override
    protected void tickExtra() {
        if (level == null || level.getGameTime() % 5 != 0) return;
        BlockState s = getBlockState();
        boolean off = callState == CallState.CONNECTED || callState == CallState.DIALING;
        if (s.hasProperty(GuardMasterBlock.OFFHOOK) && s.getValue(GuardMasterBlock.OFFHOOK) != off) {
            level.setBlock(worldPosition, s.setValue(GuardMasterBlock.OFFHOOK, off), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ 각도

    public int getAngle() {
        return angle;
    }

    public void adjustAngle(ServerPlayer player, int delta) {
        int a = Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, angle + delta));
        if (a != angle && level != null) {
            angle = a;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.4f, 1.4f);
        }
        player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".guard_angle", angle), true);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Angle", angle);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains("Angle")) angle = tag.getInt("Angle");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null && tag.contains("Angle")) angle = tag.getInt("Angle");
    }

    // ------------------------------------------------------------------ 저장

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        angle = tag.contains("Angle") ? tag.getInt("Angle") : DEFAULT_ANGLE;
        absent = tag.getBoolean("Absent");
        absentFwd = tag.getString("AbsentFwd");
        busyFwd = tag.getString("BusyFwd");
        settings.clear();
        CompoundTag s = tag.getCompound("Settings");
        for (String k : s.getAllKeys()) settings.put(k, s.getString(k));
        readList(tag, "History", history);
        readList(tag, "Alerts", alerts);
        readList(tag, "Parcels", parcels);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Angle", angle);
        tag.putBoolean("Absent", absent);
        tag.putString("AbsentFwd", absentFwd);
        tag.putString("BusyFwd", busyFwd);
        CompoundTag s = new CompoundTag();
        settings.forEach(s::putString);
        tag.put("Settings", s);
        writeList(tag, "History", history);
        writeList(tag, "Alerts", alerts);
        writeList(tag, "Parcels", parcels);
    }

    private static void readList(CompoundTag tag, String key, List<MissedCall> out) {
        out.clear();
        ListTag l = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(MissedCall.load(l.getCompound(i)));
    }

    private static void writeList(CompoundTag tag, String key, List<MissedCall> in) {
        ListTag l = new ListTag();
        for (MissedCall m : in) l.add(m.save());
        tag.put(key, l);
    }

}
