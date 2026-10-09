package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.block.WallpadBlock;
import com.qwerty.homenet.data.UnitRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.network.DeviceEntry;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.network.WallpadDataPacket;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 월패드.
 * - 세대 번호 등록 (로비폰에서 이 번호로 호출)
 * - 링크된 제어 블록 / 스마트 조명 원격 제어, 일괄소등
 * - 인터폰 수신: 응답 / 문열기 / 거절 / 통화 중 메시지 / 부재중 기록
 */
public class WallpadBlockEntity extends BlockEntity {
    public static final int MAX_DEVICES = 32;
    public static final int MAX_UNIT = 16;
    public static final int MAX_MISSED = 6;
    public static final int MAX_LOG = 8;
    public static final int RING_TIMEOUT = 20 * 30;
    public static final int TALK_TIMEOUT = 20 * 180;
    /** 기기 링크 최대 거리 */
    public static final int LINK_RANGE = 64;

    private String unit = "";
    private final List<BlockPos> devices = new ArrayList<>();
    private final List<MissedCall> missed = new ArrayList<>();

    // 통화 상태 (저장하지 않음)
    private CallState callState = CallState.IDLE;
    @Nullable
    private BlockPos caller;
    private String callerKey = "lobby";
    private int callTicks;
    private final List<IntercomLine> log = new ArrayList<>();

    public WallpadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALLPAD.get(), pos, state);
    }

    // ------------------------------------------------------------------ 기본 정보

    public String getUnit() {
        return unit;
    }

    public boolean isBusy() {
        return callState != CallState.IDLE;
    }

    public CallState getCallState() {
        return callState;
    }

    @Nullable
    public BlockPos getCaller() {
        return caller;
    }

    /** 세대 번호 설정. 이미 다른 월패드가 쓰는 번호면 거부. */
    public void setUnit(ServerPlayer player, String raw) {
        if (!(level instanceof ServerLevel sl)) return;
        String newUnit = Intercom.sanitize(raw, MAX_UNIT);
        UnitRegistry reg = UnitRegistry.get(sl);

        if (!newUnit.isEmpty() && !UnitRegistry.normalize(newUnit).isEmpty()) {
            BlockPos existing = reg.lookup(newUnit);
            if (existing != null && !existing.equals(worldPosition) && isStillRegistered(sl, existing, newUnit)) {
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".unit_taken", newUnit)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        } else {
            newUnit = "";
        }

        if (!unit.isEmpty()) reg.removeIfAt(unit, worldPosition);
        unit = newUnit;
        if (!unit.isEmpty()) reg.put(unit, worldPosition);
        setChanged();
        player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".unit_set",
                unit.isEmpty() ? "-" : unit), true);
        syncScreens();
    }

    private static boolean isStillRegistered(ServerLevel level, BlockPos pos, String unit) {
        if (!level.isLoaded(pos)) return true; // 로드 안 된 곳은 유효하다고 가정
        return level.getBlockEntity(pos) instanceof WallpadBlockEntity w
                && UnitRegistry.normalize(w.unit).equals(UnitRegistry.normalize(unit));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && !unit.isEmpty()) {
            UnitRegistry.get(sl).putIfAbsent(unit, worldPosition);
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

    /** 부서진 기기를 목록에서 정리 */
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

    private List<DeviceEntry> deviceEntries() {
        pruneDevices();
        List<DeviceEntry> out = new ArrayList<>();
        for (BlockPos p : devices) {
            DeviceBlockEntity d = device(p);
            if (d != null) {
                out.add(new DeviceEntry(p, d.getDeviceName(), d.getDeviceType().ordinal(), d.isOn(), true));
            } else {
                out.add(new DeviceEntry(p, "", DeviceType.OTHER.ordinal(), false, false));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 화면

    public void openFor(ServerPlayer player) {
        ModNetwork.sendTo(player, buildPacket(true));
    }

    /** 근처에서 월패드 화면을 보고 있을 수 있는 플레이어들에게 새 데이터 전송 */
    public void syncScreens() {
        if (!(level instanceof ServerLevel sl)) return;
        WallpadDataPacket pkt = buildPacket(false);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) {
            ModNetwork.sendTo(p, pkt);
        }
    }

    private WallpadDataPacket buildPacket(boolean open) {
        return new WallpadDataPacket(worldPosition, open, unit, callState.ordinal(), callerKey,
                deviceEntries(), new ArrayList<>(missed), new ArrayList<>(log));
    }

    // ------------------------------------------------------------------ 플레이어 조작

    public enum Action {
        REFRESH, TOGGLE, ALL_LIGHTS_OFF, ALL_OFF, SET_UNIT, ANSWER, OPEN_DOOR, HANG_UP, SEND_MESSAGE, CLEAR_MISSED;

        public static Action byId(int id) {
            Action[] v = values();
            return id >= 0 && id < v.length ? v[id] : REFRESH;
        }
    }

    public void handleAction(ServerPlayer player, Action action, BlockPos target, String text) {
        switch (action) {
            case TOGGLE -> {
                if (devices.contains(target)) {
                    DeviceBlockEntity d = device(target);
                    if (d != null) d.setOn(!d.isOn());
                }
            }
            case ALL_LIGHTS_OFF -> {
                for (BlockPos p : devices) {
                    DeviceBlockEntity d = device(p);
                    if (d != null && d.getDeviceType() == DeviceType.LIGHT) d.setOn(false);
                }
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".all_lights_off"), true);
            }
            case ALL_OFF -> {
                for (BlockPos p : devices) {
                    DeviceBlockEntity d = device(p);
                    if (d != null) d.setOn(false);
                }
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".all_off"), true);
            }
            case SET_UNIT -> setUnit(player, text);
            case ANSWER -> answer();
            case OPEN_DOOR -> openDoor();
            case HANG_UP -> hangUp(callState == CallState.RINGING ? DoorStatus.REJECTED : DoorStatus.ENDED);
            case SEND_MESSAGE -> {
                DoorStationBlockEntity door = door();
                if (callState == CallState.CONNECTED && door != null && level instanceof ServerLevel sl) {
                    Intercom.say(sl, this, door, player, "unit", text);
                }
            }
            case CLEAR_MISSED -> {
                missed.clear();
                setChanged();
            }
            default -> {}
        }
        syncScreens();
    }

    // ------------------------------------------------------------------ 통화

    @Nullable
    private DoorStationBlockEntity door() {
        if (caller == null || level == null || !level.isLoaded(caller)) return null;
        return level.getBlockEntity(caller) instanceof DoorStationBlockEntity d ? d : null;
    }

    /** 인터폰에서 호출이 들어옴. 통화 중이면 false. */
    public boolean startRinging(BlockPos doorPos, String key) {
        if (isBusy() || !(level instanceof ServerLevel sl)) return false;
        callState = CallState.RINGING;
        caller = doorPos.immutable();
        callerKey = key;
        callTicks = 0;
        log.clear();
        setRingingState(true);

        Component note = Component.translatable("msg." + HomeNet.MODID + ".incoming", Intercom.sideName(key),
                unit.isEmpty() ? "-" : unit).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NOTIFY_RANGE)) {
            p.displayClientMessage(note, true);
        }
        syncScreens();
        return true;
    }

    public void answer() {
        if (callState != CallState.RINGING) return;
        DoorStationBlockEntity door = door();
        if (door == null) {
            resetCall();
            return;
        }
        callState = CallState.CONNECTED;
        callTicks = 0;
        setRingingState(false);
        door.onAnswered();
    }

    public void openDoor() {
        if (callState == CallState.IDLE) return;
        DoorStationBlockEntity door = door();
        if (door != null) door.onDoorOpened();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        resetCall();
    }

    /** 월패드 쪽에서 통화 종료 / 거절 */
    public void hangUp(DoorStatus reasonForDoor) {
        if (callState == CallState.IDLE) return;
        DoorStationBlockEntity door = door();
        if (door != null) door.onCallEnded(reasonForDoor);
        resetCall();
    }

    /** 인터폰 쪽에서 끊음 */
    public void endFromDoor() {
        if (callState == CallState.RINGING) addMissed();
        resetCall();
        syncScreens();
    }

    private void addMissed() {
        if (level == null) return;
        missed.add(0, new MissedCall(callerKey, level.getDayTime()));
        while (missed.size() > MAX_MISSED) missed.remove(missed.size() - 1);
        setChanged();
    }

    private void resetCall() {
        callState = CallState.IDLE;
        caller = null;
        callTicks = 0;
        setRingingState(false);
    }

    public void addLine(IntercomLine line) {
        log.add(line);
        while (log.size() > MAX_LOG) log.remove(0);
    }

    private void setRingingState(boolean ringing) {
        if (level == null) return;
        BlockState s = getBlockState();
        if (s.hasProperty(WallpadBlock.RINGING) && s.getValue(WallpadBlock.RINGING) != ringing) {
            level.setBlock(worldPosition, s.setValue(WallpadBlock.RINGING, ringing), Block.UPDATE_CLIENTS);
        }
    }

    /** 블록이 부서질 때 */
    public void onBroken() {
        if (!(level instanceof ServerLevel sl)) return;
        if (callState != CallState.IDLE) {
            DoorStationBlockEntity door = door();
            if (door != null) door.onCallEnded(DoorStatus.NO_SIGNAL);
        }
        if (!unit.isEmpty()) UnitRegistry.get(sl).removeIfAt(unit, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WallpadBlockEntity be) {
        if (be.callState == CallState.IDLE) {
            // 저장 도중 꺼진 경우 등으로 남은 벨 상태 정리
            if (state.getValue(WallpadBlock.RINGING)) be.setRingingState(false);
            return;
        }
        be.callTicks++;

        // 상대 인터폰이 사라졌는지 1초마다 확인
        if (be.callTicks % 20 == 0 && be.door() == null) {
            be.resetCall();
            be.syncScreens();
            return;
        }

        if (be.callState == CallState.RINGING) {
            // "딩-동" 벨소리
            int t = be.callTicks % 40;
            if (t == 1) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.19f);
            if (t == 9) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 0.94f);

            if (be.callTicks >= RING_TIMEOUT) {
                DoorStationBlockEntity door = be.door();
                if (door != null) door.onCallEnded(DoorStatus.NO_ANSWER);
                be.addMissed();
                be.resetCall();
                be.syncScreens();
            }
        } else if (be.callState == CallState.CONNECTED && be.callTicks >= TALK_TIMEOUT) {
            be.hangUp(DoorStatus.TIMEOUT);
            be.syncScreens();
        }
    }

    // ------------------------------------------------------------------ 저장

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        unit = tag.getString("Unit");
        devices.clear();
        for (long l : tag.getLongArray("Devices")) devices.add(BlockPos.of(l));
        missed.clear();
        ListTag list = tag.getList("Missed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) missed.add(MissedCall.load(list.getCompound(i)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Unit", unit);
        tag.put("Devices", new LongArrayTag(devices.stream().mapToLong(BlockPos::asLong).toArray()));
        ListTag list = new ListTag();
        for (MissedCall m : missed) list.add(m.save());
        tag.put("Missed", list);
    }
}
