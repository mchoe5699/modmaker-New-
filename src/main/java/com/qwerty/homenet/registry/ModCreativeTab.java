package com.qwerty.homenet.registry;

import com.qwerty.homenet.HomeNet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeNet.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + HomeNet.MODID))
            .icon(() -> new ItemStack(ModItems.WALLPAD.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.WALLPAD.get());
                output.accept(ModItems.LOBBY_PHONE.get());
                output.accept(ModItems.DOOR_STATION.get());
                output.accept(ModItems.DOOR_CAMERA_SILVER.get());
                output.accept(ModItems.DOOR_CAMERA_SQUARE.get());
                output.accept(ModItems.DOOR_PHONE.get());
                output.accept(ModItems.VIDEO_PHONE.get());
                output.accept(ModItems.INTERPHONE.get());
                output.accept(ModItems.GUARD_CONSOLE.get());
                output.accept(ModItems.CONTROL_BLOCK.get());
                output.accept(ModItems.SMART_LIGHT.get());
                output.accept(ModItems.RF_CARD.get());
                output.accept(ModItems.HOME_LINKER.get());
                output.accept(ModItems.DOOR_LINKER.get());
                output.accept(ModItems.DASHBOARD.get());
            })
            .build());

    private ModCreativeTab() {}
}
