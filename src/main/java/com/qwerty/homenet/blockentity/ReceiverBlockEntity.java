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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 수신기: 월패드 / 비디오폰 / 인터폰 / 경비실기 공통.
 * - 걸려온 호출: 같은 구역에서 이 세대를 호출하면 울린다. 응답 / 문열림 / 거절 / 메시지 / 부재중 기록.
 * - 거는 호출: 세대 번호로 다른 세대를 호출하거나(세대통화), 경비실·관리실을 호출한다.
 *   경비실기는 세대 번호로 월패드·비디오폰·인터폰을 호출한다.
 */
public class ReceiverBlockEntity extends BlockEntity implements IntercomCaller {
    public static final int MAX_UNIT = 16;
    public static final int MAX_MISSED = 6;
    public static final int MAX_LOG = 8;
    /** 로비폰 쪽 시간 제한(30초)보다 조금 길게 */
    public static final int RING_TIMEOUT = 20 * 35;
    /** 이 기기에서 건 호출을 아무도 받지 않을 때 (30초) */
    public static final int DIAL_TIMEOUT = 20 * 30;
    /** 통화 3분 후 자동 종료 */
    public static final int TALK_TIMEOUT = 20 * 180;

    protected String unit = "";
    /** 로비폰에서 세대 비밀번호로 문을 열 때 쓰는 4자리 (비면 사용 안 함) */
    protected String doorPassword = "";
    protected final List<MissedCall> missed = new ArrayList<>();

    protected CallState callState = CallState.IDLE;
    /** 걸려온 호출의 발신 기기 */
    @Nullable
    protected BlockPos caller;
    protected String incomingKey = "lobby";
    protected int callTicks;
    protected final List<IntercomLine> log = new ArrayList<>();

    // 거는 호출
    protected boolean outgoing;
    protected String peerLabel = "";
    protected final List<BlockPos> outRinging = new ArrayList<>();
    @Nullable
    protected BlockPos outPeer;
    /** 다음 화면 갱신 때 한 번 띄울 안내 */
    protected String pendingNotice = "";

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
    /** 서버 전용: 로비폰이 경비 출입 비밀번호를 확인할 때 */
    public String getDoorPassword() { return doorPassword; }
    public boolean checkDoorPassword(String input) { return !doorPassword.isEmpty() && doorPassword.equals(input); }
    public boolean isBusy() { return callState != CallState.IDLE; }
    public CallState getCallState() { return callState; }
    /** 걸려온 호출의 발신 기기 (호출기 쪽에서 아직 이 수신기와 연결돼 있는지 확인할 때 씀) */
    @Nullable public BlockPos getCaller() { return outgoing ? null : caller; }

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

    /** 한 플레이어에게만 안내 팝업 */
    public void noticeTo(ServerPlayer player, String notice) {
        ModNetwork.sendTo(player, buildPacket(false).withNotice(notice));
    }

    @Override
    public void syncScreens() {
        if (!(level instanceof ServerLevel sl)) return;
        WallpadDataPacket pkt = buildPacket(false);
        if (!pendingNotice.isEmpty()) {
            pkt = pkt.withNotice(pendingNotice);
            pendingNotice = "";
        }
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) ModNetwork.sendTo(p, pkt);
    }

    protected List<DeviceEntry> deviceEntries() {
        return new ArrayList<>();
    }

    protected List<MissedCall> visitors() {
        return new ArrayList<>();
    }

    protected Map<String, String> settingsForClient() {
        return new HashMap<>();
    }

    protected long[] energyForClient() {
        return new long[0];
    }

    /** 경비실기 통화목록 / 방범목록 / 택배, 월패드 택배 */
    protected List<MissedCall> records() {
        return new ArrayList<>();
    }

    protected WallpadDataPacket buildPacket(boolean open) {
        return new WallpadDataPacket(worldPosition, open, kind().ordinal(), unit, hasDoorPassword(), callState.ordinal(), incomingKey,
                outgoing, peerLabel, "", deviceEntries(), new ArrayList<>(missed), new ArrayList<>(log),
                visitors(), settingsForClient(), energyForClient(), records());
    }

    // ------------------------------------------------------------------ 조작

    /** 순서를 바꾸면 안 됨 (패킷에 번호로 전송) */
    public enum Action {
        REFRESH, TOGGLE, ALL_LIGHTS_OFF, ALL_OFF, SET_UNIT, ANSWER, OPEN_DOOR, HANG_UP, SEND_MESSAGE, CLEAR_MISSED, SET_DOOR_PASSWORD,
        // 호출
        DIAL, CALL_GUARD,
        // 월패드 전용
        SET_SETTING, EMERGENCY, OUTING, SAVE_VISITOR, DELETE_VISITOR, CLEAR_VISITORS, UNLOCK, SET_TEMP, SET_LEVEL, SET_POWER,
        ALARM_STOP, ALARM_TEST, ALL_LIGHTS_ON, SET_AWAY, RESET_SOUND,
        // 경비실기 (KGP-70K)
        CALL_LOBBY, ABSENT_SET, ABSENT_CLEAR, BUSY_FWD_SET, BUSY_FWD_CLEAR, CONFIRM_ALERT, CLEAR_RECORDS, PARCEL_ADD, PARCEL_DONE,
        GUARD_DOOR, PICKUP;

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
            case DIAL -> dial(player, text);
            case CALL_GUARD -> callGuard(player, text == null || text.isEmpty() ? "guard" : text);
            case CALL_LOBBY -> callLobby(player, text == null ? "" : text);
            case SEND_MESSAGE -> sendMessage(player, text);
            case SET_DOOR_PASSWORD -> setDoorPassword(player, text);
            case CLEAR_MISSED -> {
                missed.clear();
                setChanged();
            }
            default -> handleExtra(player, action, target, text);
        }
        syncScreens();
    }

    protected void setDoorPassword(ServerPlayer player, String text) {
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

    /** 월패드 기기 제어 등 */
    protected void handleExtra(ServerPlayer player, Action action, BlockPos target, String text) {
    }

    protected void sendMessage(ServerPlayer player, String text) {
        if (callState != CallState.CONNECTED || !(level instanceof ServerLevel sl)) return;
        if (outgoing) {
            ReceiverBlockEntity peer = receiverAt(outPeer);
            if (peer != null) Intercom.say(sl, peer, this, player, callerKey(), text);
        } else {
            IntercomCaller c = callerBe();
            if (c == null) return;
            // 다른 세대/경비실에서 걸려온 통화면 내 이름으로, 공동현관·현관이면 "세대"로 표시
            String side = Intercom.isReceiverKey(incomingKey) ? callerKey() : "unit";
            Intercom.say(sl, this, c, player, side, text);
        }
    }

    // ------------------------------------------------------------------ 걸려온 호출

    @Nullable
    protected IntercomCaller callerBe() {
        if (outgoing || caller == null || level == null || !level.isLoaded(caller)) return null;
        return level.getBlockEntity(caller) instanceof IntercomCaller c ? c : null;
    }

    @Nullable
    protected ReceiverBlockEntity receiverAt(@Nullable BlockPos pos) {
        if (pos == null || level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ReceiverBlockEntity r ? r : null;
    }

    /** 호출이 들어옴 → 실제로 울리는 기기 위치 (경비실 부재·통화중 우회면 다른 경비실), 못 받으면 null */
    @Nullable
    public BlockPos ring(BlockPos from, String key) {
        return ring(from, key, 0);
    }

    @Nullable
    protected BlockPos ring(BlockPos from, String key, int depth) {
        return startRinging(from, key) ? worldPosition : null;
    }

    /** 호출이 들어옴. 통화 중이면 false */
    public boolean startRinging(BlockPos from, String key) {
        if (isBusy() || !(level instanceof ServerLevel sl)) return false;
        callState = CallState.RINGING;
        outgoing = false;
        caller = from.immutable();
        incomingKey = key;
        callTicks = 0;
        log.clear();
        setRingingState(true);
        onIncoming(key);
        Component note = Component.translatable("msg." + HomeNet.MODID + ".incoming", Intercom.sideName(key),
                unit.isEmpty() ? "-" : unit).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NOTIFY_RANGE)) p.displayClientMessage(note, true);
        syncScreens();
        return true;
    }

    /** 호출이 들어왔을 때 (월패드: 방문자 영상 자동 저장) */
    protected void onIncoming(String key) {
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
        if (callState == CallState.IDLE || outgoing) return;
        IntercomCaller c = callerBe();
        resetCall();
        if (c != null) c.onDoorOpened(worldPosition);
        if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
    }

    public void hangUp() {
        if (callState == CallState.IDLE) return;
        if (outgoing) {
            if (callState == CallState.DIALING) {
                stopOutRinging(true);
            } else {
                ReceiverBlockEntity peer = receiverAt(outPeer);
                if (peer != null && worldPosition.equals(peer.getCaller())) peer.endCall();
            }
            resetCall();
            return;
        }
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
        if (callState == CallState.IDLE || outgoing) return;
        resetCall();
        syncScreens();
    }

    protected void addMissed() {
        if (level == null) return;
        missed.add(0, new MissedCall(incomingKey, level.getDayTime()));
        while (missed.size() > MAX_MISSED) missed.remove(missed.size() - 1);
        setChanged();
    }

    /** 경비실기: 세대에서 비상 버튼을 누름 */
    public void addAlert(String fromUnit) {
        if (!(level instanceof ServerLevel sl)) return;
        missed.add(0, new MissedCall("emergency:" + fromUnit, level.getDayTime()));
        while (missed.size() > MAX_MISSED) missed.remove(missed.size() - 1);
        setChanged();
        Component msg = Component.translatable("msg." + HomeNet.MODID + ".emergency_alert", fromUnit.isEmpty() ? "-" : fromUnit)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NOTIFY_RANGE)) p.sendSystemMessage(msg);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 0.7f);
        syncScreens();
    }

    protected void resetCall() {
        callState = CallState.IDLE;
        caller = null;
        callTicks = 0;
        outgoing = false;
        outPeer = null;
        outRinging.clear();
        peerLabel = "";
        setRingingState(false);
    }

    @Override
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

    // ------------------------------------------------------------------ 거는 호출 (세대통화 / 경비실 호출)

    /** 화면에 보이는 발신자 키: 경비실기는 "guard:이름", 나머지는 "unit:세대번호" */
    @Override
    public String callerKey() {
        return kind() == DeviceRegistry.Kind.GUARD_CONSOLE ? "guard:" + unit : "unit:" + unit;
    }

    /** 세대 번호로 호출 (세대통화 / 경비실기 → 세대) */
    public void dial(ServerPlayer player, String raw) {
        String target = Intercom.sanitize(raw, MAX_UNIT);
        if (!(level instanceof ServerLevel sl)) return;
        if (isBusy()) {
            noticeTo(player, "busy_self");
            return;
        }
        String key = DeviceRegistry.normalize(target);
        if (key.isEmpty() || (!unit.isEmpty() && key.equals(DeviceRegistry.normalize(unit)) && kind() == DeviceRegistry.Kind.WALLPAD)) {
            noticeTo(player, "bad_unit");
            return;
        }
        startOutgoing(player, DeviceRegistry.get(sl).receivers(sl, worldPosition, target), target);
    }

    /**
     * 경비실 / 관리실 호출.
     * mode = "guard" (관할 경비실 우선, 없거나 통화 중이면 나머지 경비실), "office" (관리실), "no:50" (50번 경비실)
     */
    public void callGuard(ServerPlayer player, String mode) {
        if (!(level instanceof ServerLevel sl)) return;
        if (isBusy()) {
            noticeTo(player, "busy_self");
            return;
        }
        DeviceRegistry reg = DeviceRegistry.get(sl);
        if (mode.startsWith("no:")) {
            String no = mode.substring(3).replaceAll("[^0-9]", "");
            List<BlockPos> list = no.isEmpty() ? reg.guards(sl, worldPosition, false) : reg.guardsByNumber(sl, worldPosition, no);
            if (list.isEmpty()) {
                noticeTo(player, "no_guard");
                return;
            }
            startOutgoing(player, list, no.isEmpty() ? "#guard" : "#guard:" + no);
            return;
        }
        boolean office = "office".equals(mode);
        String pref = office ? "" : preferredGuard();
        if (!pref.isEmpty()) {
            List<BlockPos> list = reg.guardsByNumber(sl, worldPosition, pref);
            if (!list.isEmpty() && tryOutgoing(null, list, "#guard:" + pref)) return;
            // 관할 경비실이 없거나 통화 중이면 같은 단지의 다른 경비실로
        }
        // 화면에서 "#guard" / "#office" 를 경비실 / 관리실로 번역해서 보여줌
        startOutgoing(player, reg.guards(sl, worldPosition, office), office ? "#office" : "#guard");
    }

    /** 경비실 호출 버튼이 부를 경비실 번호 (비면 같은 구역의 모든 경비실) */
    protected String preferredGuard() {
        return "";
    }

    protected void startOutgoing(ServerPlayer player, List<BlockPos> registered, String label) {
        tryOutgoing(player, registered, label);
    }

    /** 호출 시작. player 가 null 이면 실패해도 안내하지 않음 (우선 경비실 → 나머지 경비실 순서로 시도할 때) */
    protected boolean tryOutgoing(@Nullable ServerPlayer player, List<BlockPos> registered, String label) {
        registered = new ArrayList<>(registered);
        registered.removeIf(p -> p.equals(worldPosition));
        if (registered.isEmpty()) {
            if (player != null) noticeTo(player, "no_unit");
            return false;
        }
        List<ReceiverBlockEntity> loaded = new ArrayList<>();
        for (BlockPos p : registered) {
            ReceiverBlockEntity r = receiverAt(p);
            if (r != null) loaded.add(r);
        }
        if (loaded.isEmpty()) {
            if (player != null) noticeTo(player, "no_signal");
            return false;
        }
        outRinging.clear();
        String key = callerKey();
        for (ReceiverBlockEntity r : loaded) {
            BlockPos at = r.ring(worldPosition, key);
            if (at != null && !at.equals(worldPosition) && !outRinging.contains(at)) outRinging.add(at);
        }
        if (outRinging.isEmpty()) {
            if (player != null) noticeTo(player, "busy");
            return false;
        }
        callState = CallState.DIALING;
        outgoing = true;
        outPeer = null;
        peerLabel = label;
        caller = null;
        callTicks = 0;
        log.clear();
        setChanged();
        onCallStarted(label);
        return true;
    }

    /** 통화 기록용 (경비실기) */
    protected void onCallStarted(String label) {
    }

    /** 경비실기: 공동현관기(로비폰) 번호로 호출 → 로비폰이 핸즈프리로 바로 연결 */
    public void callLobby(ServerPlayer player, String number) {
        if (!(level instanceof ServerLevel sl)) return;
        if (isBusy()) {
            noticeTo(player, "busy_self");
            return;
        }
        boolean found = false;
        for (BlockPos p : DeviceRegistry.get(sl).lobbies(sl, worldPosition)) {
            if (!sl.isLoaded(p) || !(sl.getBlockEntity(p) instanceof com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity lp)) continue;
            if (!lp.matchesNumber(number)) continue;
            found = true;
            if (lp.acceptGuardCall(worldPosition)) {
                callState = CallState.CONNECTED;
                outgoing = false;
                caller = p.immutable();
                incomingKey = "lobby";
                callTicks = 0;
                log.clear();
                setRingingState(false);
                onCallStarted("#lobby:" + number);
                syncScreens();
                return;
            }
        }
        noticeTo(player, found ? "busy" : "no_lobby");
    }

    protected void stopOutRinging(boolean missedCall) {
        for (BlockPos p : outRinging) {
            ReceiverBlockEntity r = receiverAt(p);
            if (r != null && worldPosition.equals(r.getCaller())) r.stopRinging(missedCall);
        }
        outRinging.clear();
    }

    @Override
    public boolean onAnswered(BlockPos receiver) {
        if (!outgoing || callState != CallState.DIALING || !outRinging.contains(receiver)) return false;
        outRinging.remove(receiver);
        stopOutRinging(false);
        outPeer = receiver.immutable();
        callState = CallState.CONNECTED;
        callTicks = 0;
        syncScreens();
        return true;
    }

    @Override
    public void onDoorOpened(BlockPos receiver) {
        // 수신기끼리는 문이 없으므로 통화 종료로 처리
        onReceiverHangUp(receiver, DoorStatus.ENDED);
    }

    @Override
    public void onReceiverHangUp(BlockPos receiver, DoorStatus reason) {
        if (!outgoing) return;
        if (receiver.equals(outPeer)) {
            resetCall();
            pendingNotice = reason == DoorStatus.TIMEOUT ? "timeout" : "ended";
        } else if (outRinging.remove(receiver)) {
            if (reason == DoorStatus.REJECTED) {
                stopOutRinging(false);
                resetCall();
                pendingNotice = "rejected";
            } else if (outRinging.isEmpty() && outPeer == null) {
                resetCall();
                pendingNotice = "no_answer";
            }
        }
        syncScreens();
    }

    // ------------------------------------------------------------------ 틱

    protected float ringVolume() {
        return kind() == DeviceRegistry.Kind.INTERPHONE ? 0.8f : 1.0f;
    }

    /** 월패드 에너지 집계, 비상 경보 등 */
    protected void tickExtra() {
    }

    public void onBroken() {
        if (!(level instanceof ServerLevel sl)) return;
        if (outgoing) {
            hangUp();
        } else if (callState != CallState.IDLE) {
            IntercomCaller c = callerBe();
            CallState st = callState;
            resetCall();
            if (c != null) c.onReceiverHangUp(worldPosition, st == CallState.RINGING ? DoorStatus.NO_ANSWER : DoorStatus.NO_SIGNAL);
        }
        DeviceRegistry.get(sl).remove(worldPosition);
    }

    public static <T extends ReceiverBlockEntity> void serverTick(Level level, BlockPos pos, BlockState state, T be) {
        be.tickExtra();
        if (be.callState == CallState.IDLE) {
            if (state.hasProperty(WallpadBlock.RINGING) && state.getValue(WallpadBlock.RINGING)) be.setRingingState(false);
            return;
        }
        be.callTicks++;
        if (be.outgoing) {
            be.tickOutgoing(level, pos);
            return;
        }
        if (be.callTicks % 20 == 0 && be.callerBe() == null) {
            be.resetCall();
            be.syncScreens();
            return;
        }
        if (be.callState == CallState.RINGING) {
            int t = be.callTicks % 40;
            float vol = be.ringVolume();
            if (vol > 0) {
                if (t == 1) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, vol, 1.19f);
                if (t == 9) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, vol, 0.94f);
            }
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

    protected void tickOutgoing(Level level, BlockPos pos) {
        if (callState == CallState.DIALING) {
            // 호출 연결음
            if (callTicks % 40 == 1) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.35f, 0.8f);
            if (callTicks % 20 == 0) {
                outRinging.removeIf(p -> {
                    ReceiverBlockEntity r = receiverAt(p);
                    return r == null || !worldPosition.equals(r.getCaller());
                });
                if (outRinging.isEmpty()) {
                    resetCall();
                    pendingNotice = "no_answer";
                    syncScreens();
                    return;
                }
            }
            if (callTicks >= DIAL_TIMEOUT) {
                stopOutRinging(true);
                resetCall();
                pendingNotice = "no_answer";
                syncScreens();
            }
        } else if (callState == CallState.CONNECTED) {
            if (callTicks % 20 == 0) {
                ReceiverBlockEntity peer = receiverAt(outPeer);
                if (peer == null || !worldPosition.equals(peer.getCaller()) || peer.getCallState() != CallState.CONNECTED) {
                    resetCall();
                    pendingNotice = "ended";
                    syncScreens();
                    return;
                }
            }
            if (callTicks >= TALK_TIMEOUT + 20) {
                ReceiverBlockEntity peer = receiverAt(outPeer);
                if (peer != null && worldPosition.equals(peer.getCaller())) peer.endCall();
                resetCall();
                pendingNotice = "timeout";
                syncScreens();
            }
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
