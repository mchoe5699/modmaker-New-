package com.qwerty.homenet.registry;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.DeviceBlockEntity;
import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.ReceiverBlockEntity;
import com.qwerty.homenet.blockentity.WallpadBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HomeNet.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<WallpadBlockEntity>> WALLPAD = BLOCK_ENTITIES.register("wallpad",
            () -> BlockEntityType.Builder.of(WallpadBlockEntity::new, ModBlocks.WALLPAD.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DoorStationBlockEntity>> DOOR_STATION = BLOCK_ENTITIES.register("door_station",
            () -> BlockEntityType.Builder.of(DoorStationBlockEntity::new, ModBlocks.DOOR_STATION.get(), ModBlocks.DOOR_PHONE.get(),
                    ModBlocks.DOOR_CAMERA_SILVER.get(), ModBlocks.DOOR_CAMERA_SQUARE.get()).build(null));

    /** 비디오폰 / 인터폰 / 경비실기 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ReceiverBlockEntity>> RECEIVER = BLOCK_ENTITIES.register("receiver",
            () -> BlockEntityType.Builder.of(ReceiverBlockEntity::new,
                    ModBlocks.VIDEO_PHONE.get(), ModBlocks.INTERPHONE.get(), ModBlocks.GUARD_CONSOLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LobbyPhoneBlockEntity>> LOBBY_PHONE = BLOCK_ENTITIES.register("lobby_phone",
            () -> BlockEntityType.Builder.of(LobbyPhoneBlockEntity::new, ModBlocks.LOBBY_PHONE.get()).build(null));

    /** 제어 블록과 스마트 조명이 공유하는 기기 블록엔티티 */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<DeviceBlockEntity>> DEVICE = BLOCK_ENTITIES.register("device",
            () -> BlockEntityType.Builder.of(DeviceBlockEntity::new,
                    ModBlocks.CONTROL_BLOCK.get(), ModBlocks.SMART_LIGHT.get()).build(null));

    private ModBlockEntities() {}
}
