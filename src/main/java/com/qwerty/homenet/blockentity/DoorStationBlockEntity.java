package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.block.DoorPhoneBlock;
import com.qwerty.homenet.data.DeviceRegistry;
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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;

/**
 * 세대 현관 바깥 호출기.
 * - 도어카메라 (door_station 블록): 화면이 있어서 호출 상태와 메시지를 볼 수 있음
 * - 도어폰 (door_phone 블록): 버튼만 있는 현관 초인종, 누르면 바로 호출
 * 세대 번호는 홈 링커로 수신기(월패드 등)를 고른 뒤 이 기기를 우클릭해서 지정한다.
 */
public class DoorStationBlockEntity extends CallerBlockEntity {
    private String unit = "";
    private DoorStatus status = DoorStatus.IDLE;
    private String statusArg = "";

    public DoorStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOOR_STATION.get(), pos, state);
    }

    public boolean isDoorPhone() {
        return getBlockState().getBlock() instanceof DoorPhoneBlock;
    }

    @Override
    public DeviceRegistry.Kind kind() {
        return isDoorPhone() ? DeviceRegistry.Kind.DOOR_PHONE : DeviceRegistry.Kind.DOOR_CAMERA;
    }

    @Override
    public String callerKey() {
        return "front_door";
    }

    @Override
    public String registeredUnit() {
        return unit;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String u) {
        unit = u == null ? "" : u;
        setChanged();
        registerDevice();
        syncScreens();
    }

    // ------------------------------------------------------------------ 화면

    public void openFor(ServerPlayer player) {
        ModNetwork.sendTo(player, buildPacket(true));
    }

    @Override
    public void syncScreens() {
        if (!(level instanceof ServerLevel sl)) return;
        DoorStationDataPacket pkt = buildPacket(false);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) ModNetwork.sendTo(p, pkt);
    }

    private DoorStationDataPacket buildPacket(boolean open) {
        return new DoorStationDataPacket(worldPosition, open, false, unit, status.ordinal(), statusArg,
                inCall, isConnected(), new ArrayList<>(log));
    }

    // ------------------------------------------------------------------ 조작

    public enum Action {
        CALL, HANG_UP, SEND_MESSAGE;

        public static Action byId(int id) {
            Action[] v = values();
            return id >= 0 && id < v.length ? v[id] : HANG_UP;
        }
    }

    public void handleAction(ServerPlayer player, Action action, String text) {
        switch (action) {
            case CALL -> call();
            case HANG_UP -> {
                if (inCall) {
                    hangUpFromCaller();
                    setStatus(DoorStatus.ENDED);
                }
            }
            case SEND_MESSAGE -> {
                if (isConnected()) say(player, callerKey(), text);
            }
        }
        syncScreens();
    }

    /** 호출 버튼 (도어폰은 블록 우클릭) */
    public void call() {
        if (!(level instanceof ServerLevel)) return;
        if (inCall) {
            setStatus(DoorStatus.BUSY);
            return;
        }
        if (DeviceRegistry.normalize(unit).isEmpty()) {
            setStatus(DoorStatus.NO_UNIT);
            notifyNearby();
            return;
        }
        switch (startCall(unit)) {
            case OK -> {
                setStatus(DoorStatus.CALLING);
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            case NO_UNIT -> setStatus(DoorStatus.NO_UNIT);
            case NO_SIGNAL -> setStatus(DoorStatus.NO_SIGNAL);
            case BUSY -> setStatus(DoorStatus.BUSY);
        }
        notifyNearby();
        syncScreens();
    }

    private void setStatus(DoorStatus s) {
        status = s;
        statusArg = unit;
    }

    @Override
    protected void onConnected() {
        setStatus(DoorStatus.CONNECTED);
        notifyNearby();
    }

    @Override
    protected void onEnded(DoorStatus reason) {
        setStatus(reason);
        notifyNearby();
    }

    @Override
    protected void onDoorOpenedByReceiver() {
        setStatus(DoorStatus.OPENED);
        notifyNearby();
    }

    private void notifyNearby() {
        if (!(level instanceof ServerLevel sl)) return;
        var msg = status.display(statusArg).copy().withStyle(ChatFormatting.GREEN);
        for (ServerPlayer p : Intercom.playersNear(sl, worldPosition, Intercom.NEAR_RANGE)) p.displayClientMessage(msg, true);
    }

    @Override
    public void addLine(IntercomLine line) {
        super.addLine(line);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoorStationBlockEntity be) {
        be.tickCaller();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        unit = tag.getString("UnitNo");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("UnitNo", unit);
    }
}
