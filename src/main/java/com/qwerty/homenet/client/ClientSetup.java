package com.qwerty.homenet.client;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.client.lobby.LobbyPhoneRenderer;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HomeNet.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LOBBY_PHONE.get(), LobbyPhoneRenderer::new);
    }
}
