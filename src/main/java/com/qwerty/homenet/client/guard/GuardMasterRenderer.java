package com.qwerty.homenet.client.guard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.GuardMasterBlock;
import com.qwerty.homenet.block.WallpadBlock;
import com.qwerty.homenet.blockentity.GuardMasterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * KGP-70K 본체는 각도를 1도씩 바꿀 수 있어서 블록 모델(22.5도 단위만 가능) 대신 여기서 돌려 그린다.
 * 받침대·코드는 블록 모델, 본체와 받침 다리는 추가 모델.
 */
public class GuardMasterRenderer implements BlockEntityRenderer<GuardMasterBlockEntity> {
    public static final ResourceLocation BODY = HomeNet.id("block/guard_master_body");
    public static final ResourceLocation BODY_RINGING = HomeNet.id("block/guard_master_body_ringing");
    public static final ResourceLocation BODY_OFFHOOK = HomeNet.id("block/guard_master_body_offhook");
    public static final ResourceLocation LEG = HomeNet.id("block/guard_master_leg");

    /** 본체가 도는 축 (블록 픽셀): 앞쪽 아래 모서리 */
    private static final float OY = 0.7f, OZ = 4.0f;
    /** 본체 뒷면까지 두께, 다리가 받치는 지점(본체 아래에서 위로) */
    private static final float THICK = 1.6f, SUPPORT = 7.0f;

    public GuardMasterRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(GuardMasterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GuardMasterBlock)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean offHook = state.getValue(GuardMasterBlock.OFFHOOK);
        boolean ringing = state.getValue(WallpadBlock.RINGING);
        BakedModel body = mc.getModelManager().getModel(offHook ? BODY_OFFHOOK : ringing ? BODY_RINGING : BODY);
        BakedModel leg = mc.getModelManager().getModel(LEG);
        float yRot = switch (state.getValue(GuardMasterBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        float angle = be.getAngle();
        var consumer = buffers.getBuffer(RenderType.cutout());

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YN.rotationDegrees(yRot));
        pose.translate(-0.5, 0, -0.5);

        // 받침 다리: 본체 뒷면이 닿는 높이까지
        double a = Math.toRadians(angle);
        float topY = (float) (OY + SUPPORT * Math.cos(a) - THICK * Math.sin(a));
        float backZ = (float) (OZ + SUPPORT * Math.sin(a) + THICK * Math.cos(a));
        float height = Math.max(0.3f, topY - OY);
        pose.pushPose();
        pose.translate(0, OY / 16f, (backZ - 0.15f) / 16f);
        pose.scale(1, height / 16f, 1);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), consumer, state, leg, 1, 1, 1, light, overlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();

        // 본체
        pose.translate(0, OY / 16f, OZ / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.translate(0, -OY / 16f, -OZ / 16f);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), consumer, state, body, 1, 1, 1, light, overlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
