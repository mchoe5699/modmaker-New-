package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.DoorControl;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomCaller;
import com.qwerty.homenet.intercom.IntercomLine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 호출기 공통 (공동현관 로비폰 / 도어카메라 / 도어폰).
 * - 같은 구역·같은 세대의 수신기를 모두 울리고, 먼저 응답한 수신기와 통화
 * - 도어 링커로 연동한 문(문/다락문/울타리 문/다른 모드의 문)을 문열림 시간 동안 열었다가 닫음
 */
public abstract class CallerBlockEntity extends BlockEntity implements IntercomCaller {
    public static final int MAX_DOORS = 16;
    public static final int DOOR_RANGE = 32;
    public static final int CALLER_RING_TICKS = 20 * 30;

    public enum CallResult { OK, NO_UNIT, NO_SIGNAL, BUSY }

    protected final List<BlockPos> linkedDoors = new ArrayList<>();
    private final Map<BlockPos, Long> closeAt = new HashMap<>();

    protected final List<BlockPos> ringing = new ArrayList<>();
    @Nullable
    protected BlockPos connectedTo;
    protected boolean inCall;
    protected long callStarted;
    protected final List<IntercomLine> log = new ArrayList<>();

    protected CallerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract DeviceRegistry.Kind kind();

    /** 수신기 화면에 보일 세대 번호 (도어카메라/도어폰은 지정된 세대, 로비폰은 없음) */
    public String registeredUnit() {
        return "";
    }

    /** 문열림 시간 (틱) */
    protected int openTicks() {
        return 60;
    }

    protected long now() {
        return level == null ? 0 : level.getGameTime();
    }

    protected void registerDevice() {
        if (level instanceof ServerLevel sl) DeviceRegistry.get(sl).register(worldPosition, kind(), registeredUnit());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerDevice();
    }

    // ------------------------------------------------------------------ 이벤트 (하위 클래스)

    protected void onConnected() {}
    protected void onEnded(DoorStatus reason) {}
    protected void onDoorOpenedByReceiver() {}

    // ------------------------------------------------------------------ 호출

    public boolean isInCall() { return inCall; }
    public boolean isConnected() { return connectedTo != null; }
    public List<IntercomLine> getLog() { return log; }

    @Nullable
    protected ReceiverBlockEntity receiverAt(@Nullable BlockPos pos) {
        if (pos == null || level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ReceiverBlockEntity r ? r : null;
    }

    /** 같은 구역의 해당 세대 수신기들 (로드된 것만) */
    public List<ReceiverBlockEntity> receiversOf(String unit) {
        List<ReceiverBlockEntity> out = new ArrayList<>();
        if (!(level instanceof ServerLevel sl)) return out;
        for (BlockPos p : DeviceRegistry.get(sl).receivers(sl, worldPosition, unit)) {
            ReceiverBlockEntity r = receiverAt(p);
            if (r != null && DeviceRegistry.normalize(r.getUnit()).equals(DeviceRegistry.normalize(unit))) out.add(r);
        }
        return out;
    }

    public CallResult startCall(String unit) {
        if (!(level instanceof ServerLevel sl) || inCall) return CallResult.BUSY;
        List<BlockPos> registered = DeviceRegistry.get(sl).receivers(sl, worldPosition, unit);
        if (registered.isEmpty()) return CallResult.NO_UNIT;
        List<ReceiverBlockEntity> loaded = receiversOf(unit);
        if (loaded.isEmpty()) return CallResult.NO_SIGNAL;
        ringing.clear();
        for (ReceiverBlockEntity r : loaded) {
            if (r.startRinging(worldPosition, callerKey())) ringing.add(r.getBlockPos());
        }
        if (ringing.isEmpty()) return CallResult.BUSY;
        inCall = true;
        connectedTo = null;
        callStarted = now();
        log.clear();
        return CallResult.OK;
    }

    /** 호출기 쪽에서 끊음 */
    public void hangUpFromCaller() {
        stopAllRinging(true);
        ReceiverBlockEntity r = receiverAt(connectedTo);
        if (r != null && worldPosition.equals(r.getCaller())) r.endCall();
        resetSession();
    }

    protected void stopAllRinging(boolean missed) {
        for (BlockPos p : ringing) {
            ReceiverBlockEntity r = receiverAt(p);
            if (r != null && worldPosition.equals(r.getCaller())) r.stopRinging(missed);
        }
        ringing.clear();
    }

    protected void resetSession() {
        inCall = false;
        connectedTo = null;
        ringing.clear();
    }

    // ------------------------------------------------------------------ IntercomCaller

    @Override
    public boolean onAnswered(BlockPos receiver) {
        if (!inCall || connectedTo != null || !ringing.contains(receiver)) return false;
        ringing.remove(receiver);
        stopAllRinging(false);
        connectedTo = receiver.immutable();
        callStarted = now();
        onConnected();
        syncScreens();
        return true;
    }

    @Override
    public void onDoorOpened(BlockPos receiver) {
        if (!inCall) return;
        ringing.remove(receiver);
        stopAllRinging(false);
        resetSession();
        openDoors();
        onDoorOpenedByReceiver();
        syncScreens();
    }

    @Override
    public void onReceiverHangUp(BlockPos receiver, DoorStatus reason) {
        if (!inCall) return;
        if (receiver.equals(connectedTo)) {
            resetSession();
            onEnded(reason == DoorStatus.TIMEOUT ? DoorStatus.TIMEOUT : DoorStatus.ENDED);
        } else if (ringing.remove(receiver)) {
            if (reason == DoorStatus.REJECTED) {
                // 한 곳에서 거절하면 통화 전체 종료
                stopAllRinging(false);
                resetSession();
                onEnded(DoorStatus.REJECTED);
            } else if (ringing.isEmpty() && connectedTo == null) {
                resetSession();
                onEnded(reason);
            }
        }
        syncScreens();
    }

    @Override
    public void addLine(IntercomLine line) {
        log.add(line);
        while (log.size() > ReceiverBlockEntity.MAX_LOG) log.remove(0);
    }

    /** 통화 중 메시지 */
    public void say(ServerPlayer player, String side, String text) {
        ReceiverBlockEntity r = receiverAt(connectedTo);
        if (r != null && level instanceof ServerLevel sl) Intercom.say(sl, r, this, player, side, text);
    }

    // ------------------------------------------------------------------ 문

    public List<BlockPos> getLinkedDoors() {
        return linkedDoors;
    }

    public enum DoorLinkResult { LINKED, UNLINKED, FULL, TOO_FAR, NOT_DOOR }

    public DoorLinkResult toggleDoor(BlockPos rawPos) {
        if (level == null) return DoorLinkResult.NOT_DOOR;
        BlockState s = level.getBlockState(rawPos);
        if (!DoorControl.isDoor(s)) return DoorLinkResult.NOT_DOOR;
        BlockPos pos = DoorControl.normalize(s, rawPos);
        if (linkedDoors.remove(pos)) {
            setChanged();
            return DoorLinkResult.UNLINKED;
        }
        if (linkedDoors.size() >= MAX_DOORS) return DoorLinkResult.FULL;
        if (!pos.closerThan(worldPosition, DOOR_RANGE)) return DoorLinkResult.TOO_FAR;
        linkedDoors.add(pos);
        setChanged();
        return DoorLinkResult.LINKED;
    }

    public void clearDoors() {
        linkedDoors.clear();
        setChanged();
    }

    /** 연동된 문을 열고 문열림 시간 뒤에 닫음 + 레드스톤 신호 */
    public void openDoors() {
        if (!(level instanceof ServerLevel sl)) return;
        int ticks = openTicks();
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
            level.scheduleTick(worldPosition, state.getBlock(), Math.max(20, ticks));
        }
        long close = now() + ticks;
        for (BlockPos p : linkedDoors) {
            if (!sl.isLoaded(p)) continue;
            if (!DoorControl.isOpen(sl, p)) {
                DoorControl.setOpen(sl, p, true);
                closeAt.put(p, close);
            } else if (closeAt.containsKey(p)) {
                closeAt.put(p, close);
            }
        }
    }

    /** 매 틱: 문 닫기 + 통화 상대 확인 */
    protected void tickCaller() {
        if (!(level instanceof ServerLevel sl)) return;
        long t = now();
        if (!closeAt.isEmpty()) {
            Iterator<Map.Entry<BlockPos, Long>> it = closeAt.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (t >= e.getValue()) {
                    DoorControl.setOpen(sl, e.getKey(), false);
                    it.remove();
                }
            }
        }
        if (!inCall || t % 20 != 5) return;
        // 울리는 수신기가 사라졌으면 목록에서 정리
        ringing.removeIf(p -> {
            ReceiverBlockEntity r = receiverAt(p);
            return r == null || !worldPosition.equals(r.getCaller());
        });
        if (connectedTo != null) {
            ReceiverBlockEntity r = receiverAt(connectedTo);
            if (r == null || !worldPosition.equals(r.getCaller())) {
                resetSession();
                onEnded(DoorStatus.ENDED);
                syncScreens();
            }
        } else if (ringing.isEmpty()) {
            resetSession();
            onEnded(DoorStatus.NO_ANSWER);
            syncScreens();
        } else if (t - callStarted >= CALLER_RING_TICKS) {
            stopAllRinging(true);
            resetSession();
            onEnded(DoorStatus.NO_ANSWER);
            syncScreens();
        }
    }

    public void onBroken() {
        if (inCall) hangUpFromCaller();
        if (level instanceof ServerLevel sl) {
            for (BlockPos p : closeAt.keySet()) DoorControl.setOpen(sl, p, false);
            DeviceRegistry.get(sl).remove(worldPosition);
        }
        closeAt.clear();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkedDoors.clear();
        for (long l : tag.getLongArray("Doors")) linkedDoors.add(BlockPos.of(l));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Doors", new LongArrayTag(linkedDoors.stream().mapToLong(BlockPos::asLong).toArray()));
    }
}
