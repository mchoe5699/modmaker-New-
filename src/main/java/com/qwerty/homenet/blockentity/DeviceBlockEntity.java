package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.block.ControlBlock;
import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.network.DeviceConfigOpenPacket;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 제어 블록 / 스마트 조명 공용 블록엔티티 (이름, 기기 종류 저장) */
public class DeviceBlockEntity extends BlockEntity {
    public static final int MAX_NAME = 20;

    private String name = "";
    @Nullable
    private DeviceType type;

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEVICE.get(), pos, state);
    }

    public String getDeviceName() {
        return name;
    }

    public DeviceType getDeviceType() {
        if (type != null) return type;
        return getBlockState().getBlock() instanceof ControlBlock cb ? cb.defaultType() : DeviceType.OTHER;
    }

    public boolean isOn() {
        BlockState s = getBlockState();
        return s.hasProperty(ControlBlock.POWERED) && s.getValue(ControlBlock.POWERED);
    }

    public void setOn(boolean on) {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        if (s.hasProperty(ControlBlock.POWERED) && s.getValue(ControlBlock.POWERED) != on) {
            level.setBlock(worldPosition, s.setValue(ControlBlock.POWERED, on), Block.UPDATE_ALL);
        }
    }

    public void configure(String newName, DeviceType newType) {
        this.name = Intercom.sanitize(newName, MAX_NAME);
        this.type = newType;
        setChanged();
    }

    public void openConfig(ServerPlayer player) {
        ModNetwork.sendTo(player, new DeviceConfigOpenPacket(worldPosition, name, getDeviceType().ordinal(), isOn()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        name = tag.getString("Name");
        type = tag.contains("Type") ? DeviceType.byId(tag.getInt("Type")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Name", name);
        if (type != null) tag.putInt("Type", type.ordinal());
    }
}
