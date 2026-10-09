package com.qwerty.homenet.registry;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.item.HomeLinkerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HomeNet.MODID);

    public static final RegistryObject<Item> WALLPAD = ITEMS.register("wallpad",
            () -> new BlockItem(ModBlocks.WALLPAD.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOOR_STATION = ITEMS.register("door_station",
            () -> new BlockItem(ModBlocks.DOOR_STATION.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOOR_CAMERA_SILVER = ITEMS.register("door_camera_silver",
            () -> new BlockItem(ModBlocks.DOOR_CAMERA_SILVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOOR_CAMERA_SQUARE = ITEMS.register("door_camera_square",
            () -> new BlockItem(ModBlocks.DOOR_CAMERA_SQUARE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOBBY_PHONE = ITEMS.register("lobby_phone",
            () -> new BlockItem(ModBlocks.LOBBY_PHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DOOR_PHONE = ITEMS.register("door_phone",
            () -> new BlockItem(ModBlocks.DOOR_PHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> VIDEO_PHONE = ITEMS.register("video_phone",
            () -> new BlockItem(ModBlocks.VIDEO_PHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> INTERPHONE = ITEMS.register("interphone",
            () -> new BlockItem(ModBlocks.INTERPHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GUARD_CONSOLE = ITEMS.register("guard_console",
            () -> new BlockItem(ModBlocks.GUARD_CONSOLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONTROL_BLOCK = ITEMS.register("control_block",
            () -> new BlockItem(ModBlocks.CONTROL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SMART_LIGHT = ITEMS.register("smart_light",
            () -> new BlockItem(ModBlocks.SMART_LIGHT.get(), new Item.Properties()));

    /** 출입 카드 (RF 카드) */
    public static final RegistryObject<com.qwerty.homenet.item.RfCardItem> RF_CARD = ITEMS.register("rf_card",
            () -> new com.qwerty.homenet.item.RfCardItem(new Item.Properties().stacksTo(1)));

    /** 도어 링커: 로비폰/도어카메라/도어폰과 문을 연동하는 도구 */
    public static final RegistryObject<com.qwerty.homenet.item.DoorLinkerItem> DOOR_LINKER = ITEMS.register("door_linker",
            () -> new com.qwerty.homenet.item.DoorLinkerItem(new Item.Properties().stacksTo(1)));

    /** 홈네트워크 대시보드: 지도에서 단지(구역)를 정함 */
    public static final RegistryObject<com.qwerty.homenet.item.DashboardItem> DASHBOARD = ITEMS.register("dashboard",
            () -> new com.qwerty.homenet.item.DashboardItem(new Item.Properties().stacksTo(1)));

    /** 홈 링커: 월패드와 기기를 연결하는 도구 */
    public static final RegistryObject<HomeLinkerItem> HOME_LINKER = ITEMS.register("home_linker",
            () -> new HomeLinkerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
