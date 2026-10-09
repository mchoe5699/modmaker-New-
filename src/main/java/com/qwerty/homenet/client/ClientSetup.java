package com.qwerty.homenet.client;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.client.guard.GuardMasterRenderer;
import com.qwerty.homenet.client.lobby.LobbyPhoneRenderer;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HomeNet.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LOBBY_PHONE.get(), LobbyPhoneRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GUARD_MASTER.get(), GuardMasterRenderer::new);
    }

    /** KGP-70K 본체 / 다리 모델 (블록엔티티 렌더러가 각도만큼 돌려서 그림) */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(GuardMasterRenderer.BODY);
        event.register(GuardMasterRenderer.BODY_RINGING);
        event.register(GuardMasterRenderer.BODY_OFFHOOK);
        event.register(GuardMasterRenderer.LEG);
    }
}
