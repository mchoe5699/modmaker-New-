package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.network.DeviceEntry;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 월패드: 수신기 기능 + 링크된 제어 블록 / 스마트 조명 원격 제어, 일괄소등.
 */
public class WallpadBlockEntity extends ReceiverBlockEntity {
    public static final int MAX_DEVICES = 32;
    public static final int LINK_RANGE = 64;

    private final List<BlockPos> devices = new ArrayList<>();

    public WallpadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALLPAD.get(), pos, state);
    }

    @Override
    public DeviceRegistry.Kind kind() {
        return DeviceRegistry.Kind.WALLPAD;
    }

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
            if (d != null) out.add(new DeviceEntry(p, d.getDeviceName(), d.getDeviceType().ordinal(), d.isOn(), true));
            else out.add(new DeviceEntry(p, "", DeviceType.OTHER.ordinal(), false, false));
        }
        return out;
    }

    @Override
    protected void handleExtra(ServerPlayer player, Action action, BlockPos target) {
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
            default -> {}
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        devices.clear();
        for (long l : tag.getLongArray("Devices")) devices.add(BlockPos.of(l));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Devices", new LongArrayTag(devices.stream().mapToLong(BlockPos::asLong).toArray()));
    }
}
