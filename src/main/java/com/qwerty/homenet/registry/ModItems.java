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
    public static final RegistryObject<Item> LOBBY_PHONE = ITEMS.register("lobby_phone",
            () -> new BlockItem(ModBlocks.LOBBY_PHONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONTROL_BLOCK = ITEMS.register("control_block",
            () -> new BlockItem(ModBlocks.CONTROL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SMART_LIGHT = ITEMS.register("smart_light",
            () -> new BlockItem(ModBlocks.SMART_LIGHT.get(), new Item.Properties()));

    /** 홈 링커: 월패드와 기기를 연결하는 도구 */
    public static final RegistryObject<HomeLinkerItem> HOME_LINKER = ITEMS.register("home_linker",
            () -> new HomeLinkerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
