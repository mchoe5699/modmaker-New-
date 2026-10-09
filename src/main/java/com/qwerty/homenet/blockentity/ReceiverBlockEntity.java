package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.ReceiverBlock;
import com.qwerty.homenet.block.WallpadBlock;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomCaller;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 수신기: 월패드 / 비디오폰 / 인터폰 / 경비실기 공통.
 * 세대 번호를 가지고, 같은 구역에서 그 세대를 호출하면 울린다. 응답 / 문열림 / 거절 / 메시지 / 부재중 기록.
 */
public class ReceiverBlockEntity extends BlockEntity {
    public static final int MAX_UNIT = 16;
    public static final int MAX_MISSED = 6;
    public static final int MAX_LOG = 8;
    /** 로비폰 쪽 시간 제한(30초)보다 조금 길게 */
    public static final int RING_TIMEOUT = 20 * 35;
    public static final int TALK_TIMEOUT = 20 * 185;

    protected String unit = "";
    /** 로비폰에서 세대 비밀번호로 문을 열 때 쓰는 4자리 (비면 사용 안 함) */
    protected String doorPassword = "";
    protected final List<MissedCall> missed = new ArrayList<>();

    protected CallState callState = CallState.IDLE;
    @Nullable
    protected BlockPos caller;
    protected String callerKey = "lobby";
    protected int callTicks;
    protected final List<IntercomLine> log = new ArrayList<>();

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.RECEIVER.get(), pos, state);
    }

    protected ReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (kind() == DeviceRegistry.Kind.GUARD_CONSOLE) unit = "경비실";
    }

    public DeviceRegistry.Kind kind() {
        return getBlockState().getBlock() instanceof ReceiverBlock rb ? rb.kind() : DeviceRegistry.Kind.WALLPAD;
    }

    public String getUnit() { return unit; }
    public boolean hasDoorPassword() { return !doorPassword.isEmpty(); }
    public boolean checkDoorPassword(String input) { return !doorPassword.isEmpty() && doorPassword.equals(input); }
    public boolean isBusy() { return callState != CallState.IDLE; }
    public CallState getCallState() { return callState; }
    @Nullable public BlockPos getCaller() { return caller; }

    protected void register() {
        if (level instanceof ServerLevel sl) DeviceRegistry.get(sl).register(worldPosition, kind(), unit);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    public void setUnit(ServerPlayer player, String raw) {
        String u = Intercom.sanitize(raw, MAX_UNIT);
        if (DeviceRegistry.normalize(u).isEmpty()) u = "";
        unit = u;
        setChanged();
        register();
        player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".unit_set", unit.isEmpty() ? "-" : unit), true);
        syncScreens();
    }

    // ------------------------------------------------------------------ 화면

    public void openFor(ServerPlayer player) {
        ModNetwork.sendTo(player, buildPacket(true));
    }

    public void syncScreens() {
        if (!(level instanceof ServerLevel sl)) return;
        WallpadDataPacket pkt = buildPacket(false);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) ModNetwork.sendTo(p, pkt);
    }

    protected List<DeviceEntry> deviceEntries() {
        return new ArrayList<>();
    }

    protected WallpadDataPacket buildPacket(boolean open) {
        return new WallpadDataPacket(worldPosition, open, kind().ordinal(), unit, hasDoorPassword(), callState.ordinal(), callerKey,
                deviceEntries(), new ArrayList<>(missed), new ArrayList<>(log));
    }

    // ------------------------------------------------------------------ 조작

    public enum Action {
        REFRESH, TOGGLE, ALL_LIGHTS_OFF, ALL_OFF, SET_UNIT, ANSWER, OPEN_DOOR, HANG_UP, SEND_MESSAGE, CLEAR_MISSED, SET_DOOR_PASSWORD;

        public static Action byId(int id) {
            Action[] v = values();
            return id >= 0 && id < v.length ? v[id] : REFRESH;
        }
    }

    public void handleAction(ServerPlayer player, Action action, BlockPos target, String text) {
        switch (action) {
            case SET_UNIT -> setUnit(player, text);
            case ANSWER -> answer();
            case OPEN_DOOR -> openDoor();
            case HANG_UP -> hangUp();
            case SEND_MESSAGE -> {
                IntercomCaller c = callerBe();
                if (callState == CallState.CONNECTED && c != null && level instanceof ServerLevel sl) {
                    Intercom.say(sl, this, c, player, "unit", text);
                }
            }
            case SET_DOOR_PASSWORD -> {
                String pw = text == null ? "" : text.trim();
                if (pw.isEmpty() || pw.matches("\\d{4}")) {
                    doorPassword = pw;
                    setChanged();
                    player.displayClientMessage(Component.translatable(pw.isEmpty()
                            ? "msg." + HomeNet.MODID + ".door_pw_cleared" : "msg." + HomeNet.MODID + ".door_pw_set"), true);
                } else {
                    player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".door_pw_invalid")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            case CLEAR_MISSED -> {
                missed.clear();
                setChanged();
            }
            default -> handleExtra(player, action, target);
        }
        syncScreens();
    }

    /** 월패드 기기 제어 등 */
    protected void handleExtra(ServerPlayer player, Action action, BlockPos target) {
    }

    // ------------------------------------------------------------------ 통화

    @Nullable
    protected IntercomCaller callerBe() {
        if (caller == null || level == null || !level.isLoaded(caller)) return null;
        return level.getBlockEntity(caller) instanceof IntercomCaller c ? c : null;
    }

    /** 호출이 들어옴. 통화 중이면 false */
    public boolean startRinging(BlockPos from, String key) {
        if (isBusy() || !(level instanceof ServerLevel sl)) return false;
        callState = CallState.RINGING;
        caller = from.immutable();
        callerKey = key;
        callTicks = 0;
        log.clear();
        setRingingState(true);
        Component note = Component.translatable("msg." + HomeNet.MODID + ".incoming", Intercom.sideName(key),
                unit.isEmpty() ? "-" : unit).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NOTIFY_RANGE)) p.displayClientMessage(note, true);
        syncScreens();
        return true;
    }

    public void answer() {
        if (callState != CallState.RINGING) return;
        IntercomCaller c = callerBe();
        if (c == null || !c.onAnswered(worldPosition)) {
            resetCall();
            return;
        }
        callState = CallState.CONNECTED;
        callTicks = 0;
        setRingingState(false);
    }

    public void openDoor() {
        if (callState == CallState.IDLE) return;
        IntercomCaller c = callerBe();
        resetCall();
        if (c != null) c.onDoorOpened(worldPosition);
        if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
    }

    public void hangUp() {
        if (callState == CallState.IDLE) return;
        DoorStatus reason = callState == CallState.RINGING ? DoorStatus.REJECTED : DoorStatus.ENDED;
        IntercomCaller c = callerBe();
        resetCall();
        if (c != null) c.onReceiverHangUp(worldPosition, reason);
    }

    /** 호출기 쪽에서 울림을 멈춤 (다른 수신기가 받았거나, 호출기가 끊었거나, 응답 없음) */
    public void stopRinging(boolean addMissed) {
        if (callState != CallState.RINGING) return;
        if (addMissed) addMissed();
        resetCall();
        syncScreens();
    }

    /** 호출기 쪽에서 통화를 끝냄 */
    public void endCall() {
        if (callState == CallState.IDLE) return;
        resetCall();
        syncScreens();
    }

    protected void addMissed() {
        if (level == null) return;
        missed.add(0, new MissedCall(callerKey, level.getDayTime()));
        while (missed.size() > MAX_MISSED) missed.remove(missed.size() - 1);
        setChanged();
    }

    protected void resetCall() {
        callState = CallState.IDLE;
        caller = null;
        callTicks = 0;
        setRingingState(false);
    }

    public void addLine(IntercomLine line) {
        log.add(line);
        while (log.size() > MAX_LOG) log.remove(0);
    }

    protected void setRingingState(boolean ringing) {
        if (level == null) return;
        BlockState s = getBlockState();
        if (s.hasProperty(WallpadBlock.RINGING) && s.getValue(WallpadBlock.RINGING) != ringing) {
            level.setBlock(worldPosition, s.setValue(WallpadBlock.RINGING, ringing), Block.UPDATE_CLIENTS);
        }
    }

    public void onBroken() {
        if (!(level instanceof ServerLevel sl)) return;
        if (callState != CallState.IDLE) {
            IntercomCaller c = callerBe();
            CallState st = callState;
            resetCall();
            if (c != null) c.onReceiverHangUp(worldPosition, st == CallState.RINGING ? DoorStatus.NO_ANSWER : DoorStatus.NO_SIGNAL);
        }
        DeviceRegistry.get(sl).remove(worldPosition);
    }

    public static <T extends ReceiverBlockEntity> void serverTick(Level level, BlockPos pos, BlockState state, T be) {
        if (be.callState == CallState.IDLE) {
            if (state.hasProperty(WallpadBlock.RINGING) && state.getValue(WallpadBlock.RINGING)) be.setRingingState(false);
            return;
        }
        be.callTicks++;
        if (be.callTicks % 20 == 0 && be.callerBe() == null) {
            be.resetCall();
            be.syncScreens();
            return;
        }
        if (be.callState == CallState.RINGING) {
            int t = be.callTicks % 40;
            float vol = be.kind() == DeviceRegistry.Kind.INTERPHONE ? 0.8f : 1.0f;
            if (t == 1) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, vol, 1.19f);
            if (t == 9) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, vol, 0.94f);
            if (be.callTicks >= RING_TIMEOUT) {
                IntercomCaller c = be.callerBe();
                be.addMissed();
                be.resetCall();
                if (c != null) c.onReceiverHangUp(pos, DoorStatus.NO_ANSWER);
                be.syncScreens();
            }
        } else if (be.callState == CallState.CONNECTED && be.callTicks >= TALK_TIMEOUT) {
            IntercomCaller c = be.callerBe();
            be.resetCall();
            if (c != null) c.onReceiverHangUp(pos, DoorStatus.TIMEOUT);
            be.syncScreens();
        }
    }

    // ------------------------------------------------------------------ 저장

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Unit")) unit = tag.getString("Unit");
        doorPassword = tag.getString("DoorPassword");
        missed.clear();
        ListTag list = tag.getList("Missed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) missed.add(MissedCall.load(list.getCompound(i)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Unit", unit);
        tag.putString("DoorPassword", doorPassword);
        ListTag list = new ListTag();
        for (MissedCall m : missed) list.add(m.save());
        tag.put("Missed", list);
    }
}
