package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.block.DoorStationBlock;
import com.qwerty.homenet.data.UnitRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.network.DoorStationDataPacket;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 인터폰 (공동현관 로비폰 / 세대현관 도어폰).
 */
public class DoorStationBlockEntity extends BlockEntity {
    /** 월패드에 링크되면 세대현관 도어폰 */
    @Nullable
    private BlockPos linkedWallpad;

    // 통화 상태 (저장하지 않음)
    @Nullable
    private BlockPos target;
    private String targetUnit = "";
    private boolean connected;
    private DoorStatus status = DoorStatus.IDLE;
    private String statusArg = "";
    private int ticks;
    private final List<IntercomLine> log = new ArrayList<>();

    public DoorStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOOR_STATION.get(), pos, state);
    }

    public boolean isLobby() {
        return linkedWallpad == null;
    }

    /** 월패드에 표시되는 발신 위치 키 */
    public String callerKey() {
        return isLobby() ? "lobby" : "front_door";
    }

    @Nullable
    public BlockPos getLinkedWallpad() {
        return linkedWallpad;
    }

    public void setLinkedWallpad(@Nullable BlockPos pos) {
        this.linkedWallpad = pos == null ? null : pos.immutable();
        setChanged();
    }

    @Nullable
    private WallpadBlockEntity wallpadAt(@Nullable BlockPos pos) {
        if (pos == null || level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof WallpadBlockEntity w ? w : null;
    }

    // ------------------------------------------------------------------ 화면

    public void openFor(ServerPlayer player) {
        ModNetwork.sendTo(player, buildPacket(true));
    }

    public void syncScreens() {
        if (!(level instanceof ServerLevel sl)) return;
        DoorStationDataPacket pkt = buildPacket(false);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) {
            ModNetwork.sendTo(p, pkt);
        }
    }

    private DoorStationDataPacket buildPacket(boolean open) {
        String linkedUnit = "";
        if (!isLobby()) {
            WallpadBlockEntity w = wallpadAt(linkedWallpad);
            if (w != null) linkedUnit = w.getUnit();
        }
        return new DoorStationDataPacket(worldPosition, open, isLobby(), linkedUnit,
                status.ordinal(), statusArg, target != null, connected, new ArrayList<>(log));
    }

    // ------------------------------------------------------------------ 플레이어 조작

    public enum Action {
        CALL, HANG_UP, SEND_MESSAGE;

        public static Action byId(int id) {
            Action[] v = values();
            return id >= 0 && id < v.length ? v[id] : HANG_UP;
        }
    }

    public void handleAction(ServerPlayer player, Action action, String text) {
        switch (action) {
            case CALL -> call(text);
            case HANG_UP -> {
                if (target != null) {
                    WallpadBlockEntity w = wallpadAt(target);
                    if (w != null && worldPosition.equals(w.getCaller())) w.endFromDoor();
                    endLocal(DoorStatus.ENDED);
                }
            }
            case SEND_MESSAGE -> {
                WallpadBlockEntity w = wallpadAt(target);
                if (connected && w != null && level instanceof ServerLevel sl) {
                    Intercom.say(sl, w, this, player, callerKey(), text);
                }
            }
        }
        syncScreens();
    }

    private void call(String unitInput) {
        if (!(level instanceof ServerLevel sl)) return;
        if (target != null) {
            setStatus(DoorStatus.BUSY, targetUnit);
            return;
        }

        BlockPos wpPos;
        String unit;
        if (isLobby()) {
            unit = Intercom.sanitize(unitInput, WallpadBlockEntity.MAX_UNIT);
            if (UnitRegistry.normalize(unit).isEmpty()) {
                setStatus(DoorStatus.NO_UNIT, unit);
                return;
            }
            wpPos = UnitRegistry.get(sl).lookup(unit);
            if (wpPos == null) {
                setStatus(DoorStatus.NO_UNIT, unit);
                return;
            }
        } else {
            wpPos = linkedWallpad;
            WallpadBlockEntity linked = wallpadAt(wpPos);
            unit = linked != null ? linked.getUnit() : "";
        }

        WallpadBlockEntity w = wallpadAt(wpPos);
        if (w == null) {
            setStatus(DoorStatus.NO_SIGNAL, unit);
            return;
        }
        if (!w.startRinging(worldPosition, callerKey())) {
            setStatus(DoorStatus.BUSY, unit);
            return;
        }
        target = wpPos;
        targetUnit = w.getUnit();
        connected = false;
        ticks = 0;
        log.clear();
        setStatus(DoorStatus.CALLING, targetUnit);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.5f, 1.4f);
    }

    private void setStatus(DoorStatus s, String arg) {
        status = s;
        statusArg = arg == null ? "" : arg;
    }

    // ------------------------------------------------------------------ 월패드에서 오는 이벤트

    public void onAnswered() {
        connected = true;
        setStatus(DoorStatus.CONNECTED, targetUnit);
        notifyNearby();
        syncScreens();
    }

    public void onDoorOpened() {
        if (level instanceof ServerLevel sl) {
            DoorStationBlock.pulse(sl, worldPosition);
            sl.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        endLocal(DoorStatus.OPENED);
    }

    public void onCallEnded(DoorStatus reason) {
        endLocal(reason);
    }

    private void endLocal(DoorStatus reason) {
        target = null;
        connected = false;
        setStatus(reason, targetUnit);
        notifyNearby();
        syncScreens();
    }

    private void notifyNearby() {
        if (!(level instanceof ServerLevel sl)) return;
        var msg = status.display(statusArg).copy().withStyle(ChatFormatting.GREEN);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) {
            p.displayClientMessage(msg, true);
        }
    }

    public void addLine(IntercomLine line) {
        log.add(line);
        while (log.size() > WallpadBlockEntity.MAX_LOG) log.remove(0);
    }

    /** 블록이 부서질 때 */
    public void onBroken() {
        if (target != null) {
            WallpadBlockEntity w = wallpadAt(target);
            if (w != null && worldPosition.equals(w.getCaller())) w.endFromDoor();
            target = null;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoorStationBlockEntity be) {
        if (be.target == null) return;
        if (++be.ticks % 20 != 0) return;
        // 월패드 쪽 청크가 언로드됐거나 다른 통화로 넘어갔으면 정리
        WallpadBlockEntity w = be.wallpadAt(be.target);
        if (w == null || !pos.equals(w.getCaller()) || w.getCallState() == CallState.IDLE) {
            be.endLocal(be.connected ? DoorStatus.ENDED : DoorStatus.NO_ANSWER);
        }
    }

    // ------------------------------------------------------------------ 저장

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkedWallpad = tag.contains("Wallpad") ? BlockPos.of(tag.getLong("Wallpad")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (linkedWallpad != null) tag.putLong("Wallpad", linkedWallpad.asLong());
    }
}
