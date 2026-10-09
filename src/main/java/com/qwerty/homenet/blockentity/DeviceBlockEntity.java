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
    /** 난방 / 에어컨 설정 온도 (0 = 기본값) */
    private int setTemp;
    /** 환기 풍량 1~3 */
    private int fanLevel = 1;
    /** 난방 외출 모드 */
    private boolean away;

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

    public int getSetTemp() {
        if (setTemp != 0) return setTemp;
        return getDeviceType() == DeviceType.AIRCON ? 26 : 24;
    }

    public void adjustSetTemp(int delta) {
        boolean ac = getDeviceType() == DeviceType.AIRCON;
        setTemp = Math.max(ac ? 18 : 10, Math.min(ac ? 30 : 40, getSetTemp() + delta));
        setChanged();
    }

    public int getFanLevel() {
        return fanLevel;
    }

    public void setFanLevel(int level) {
        fanLevel = Math.max(1, Math.min(3, level));
        setChanged();
    }

    public boolean isAway() {
        return away;
    }

    public void setAway(boolean away) {
        this.away = away;
        setChanged();
    }

    /** 바이옴 기온으로 계산한 바깥 기온 (°C) */
    public static int outdoorTemp(net.minecraft.world.level.Level level, BlockPos pos) {
        float base = level.getBiome(pos).value().getBaseTemperature();
        int t = Math.round(base * 25f);
        if (level.isRaining()) t -= 3;
        return Math.max(-15, Math.min(40, t));
    }

    /** 실내 현재 온도 (난방·에어컨이 켜져 있으면 설정 온도 쪽으로) */
    public int roomTemp() {
        if (level == null) return 20;
        int t = Math.max(8, Math.min(34, outdoorTemp(level, worldPosition)));
        if (isOn() && getDeviceType() == DeviceType.HEATING && !away) t = Math.max(t, getSetTemp());
        if (isOn() && getDeviceType() == DeviceType.AIRCON) t = Math.min(t, getSetTemp());
        return t;
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
        setTemp = tag.getInt("SetTemp");
        fanLevel = tag.contains("Fan") ? tag.getInt("Fan") : 1;
        away = tag.getBoolean("Away");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Name", name);
        if (type != null) tag.putInt("Type", type.ordinal());
        tag.putInt("SetTemp", setTemp);
        tag.putInt("Fan", fanLevel);
        tag.putBoolean("Away", away);
    }
}
