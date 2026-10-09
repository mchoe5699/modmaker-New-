package com.qwerty.homenet.client;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.GuardMasterBlock;
import com.qwerty.homenet.network.GuardAnglePacket;
import com.qwerty.homenet.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 게임 중 키 입력: KGP-70K 를 맨손으로 바라보고 Ctrl + (+ / -) → 본체 각도 1도씩 */
@Mod.EventBusSubscriber(modid = HomeNet.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) return;
        if ((event.getModifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        int key = event.getKey();
        int delta;
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) delta = 1;
        else if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) delta = -1;
        else return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null || !mc.player.getMainHandItem().isEmpty()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof GuardMasterBlock)) return;
        ModNetwork.sendToServer(new GuardAnglePacket(hit.getBlockPos(), delta));
    }

    @SuppressWarnings("unused")
    private static boolean ctrl() {
        return Screen.hasControlDown();
    }
}
