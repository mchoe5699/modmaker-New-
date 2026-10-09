package com.qwerty.homenet.client.lobby;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.homenet.block.LobbyPhoneBlock;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * 블록 정면 LCD 의 정보 박스에 시계 / 입력 번호 / 호출 상태를 그린다.
 * 키패드와 기기 외형은 텍스처에 있다.
 */
public class LobbyPhoneRenderer implements BlockEntityRenderer<LobbyPhoneBlockEntity> {
    // 기기 단위(mm) → 블록 픽셀
    private static final float K = 16f / 279f;
    private static final float LEFT_PX = 8f + (float) LobbyPhoneBlock.HALF_WIDTH;   // 정면에서 볼 때 기기 왼쪽 끝 (모델 x)
    private static final float FRONT_PX = 16f - (float) LobbyPhoneBlock.DEPTH;

    private static final int INFO_X1 = 61, INFO_Y1 = 58, INFO_X2 = 135, INFO_Y2 = 101;
    private static final int DARK = 0xFF1E2A66;

    private final Font font;

    public LobbyPhoneRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(LobbyPhoneBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null || !be.isBacklight()) return;
        Direction facing = be.getBlockState().getValue(LobbyPhoneBlock.FACING);

        pose.pushPose();
        // 북쪽을 보는 모델 기준으로 회전
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        // 기기 왼쪽 위 모서리, 정면 바로 앞
        pose.translate(LEFT_PX / 16f, 1.0, FRONT_PX / 16f - 0.002);
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        float unit = K / 16f;
        pose.scale(unit, -unit, unit);

        LobbyLcd lcd = LobbyLcd.of(be, be.getLevel().getGameTime());
        boolean wrap = be.getScreen() == Screen.MESSAGE && !"opened".equals(be.getMsgKey());
        float cx = (INFO_X1 + INFO_X2) / 2f;

        if (!lcd.topLeft().getString().isEmpty()) draw(pose, buffers, lcd.topLeft(), INFO_X1 + 3, INFO_Y1 + 3, 0.7f, DARK, false);
        if (!lcd.topRight().getString().isEmpty()) {
            float w = font.width(lcd.topRight()) * 0.6f;
            draw(pose, buffers, lcd.topRight(), INFO_X2 - 3 - w, INFO_Y1 + 3, 0.6f, DARK, false);
        }
        if (wrap) {
            int maxW = Math.round((INFO_X2 - INFO_X1 - 6) / 0.9f);
            var lines = font.split(lcd.big(), maxW);
            float y0 = (INFO_Y1 + INFO_Y2) / 2f - lines.size() * 9 * 0.9f / 2f;
            for (int i = 0; i < lines.size(); i++) {
                pose.pushPose();
                pose.translate(cx, y0 + i * 9 * 0.9f, 0);
                pose.scale(0.9f, 0.9f, 0.9f);
                Matrix4f m = pose.last().pose();
                font.drawInBatch(lines.get(i), -font.width(lines.get(i)) / 2f, 0, DARK, false, m, buffers,
                        Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        } else {
            float bs = lcd.bigScale();
            float maxW = INFO_X2 - INFO_X1 - 6;
            float w = font.width(lcd.big()) * bs;
            if (w > maxW) bs *= maxW / w;
            float y = (lcd.topLeft().getString().isEmpty() ? (INFO_Y1 + INFO_Y2) / 2f : (INFO_Y1 + INFO_Y2) / 2f + 3) - 3.5f * bs;
            draw(pose, buffers, lcd.big(), cx, y, bs, DARK, true);
        }
        if (!lcd.subLeft().getString().isEmpty()) draw(pose, buffers, lcd.subLeft(), INFO_X1, INFO_Y2 + 6, 0.65f, 0xFFFFFFFF, false);
        if (!lcd.subRight().getString().isEmpty()) {
            float w = font.width(lcd.subRight()) * 0.65f;
            draw(pose, buffers, lcd.subRight(), INFO_X2 - w, INFO_Y2 + 6, 0.65f, 0xFFFFFFFF, false);
        }
        pose.popPose();
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, Component c, float x, float y, float scale,
                      int color, boolean center) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, scale);
        float w = font.width(c);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(c, center ? -w / 2f : 0, 0, color, false, m, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 24;
    }
}
