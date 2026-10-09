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
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import static com.qwerty.homenet.client.lobby.LobbyLcd.*;

/**
 * 블록 정면 LCD: 정보 박스의 시계 / 입력 번호 / 호출 상태, 그리고 문열림 아이콘.
 * 외형과 키패드는 텍스처(lobby_phone_front.png)에 있다.
 */
public class LobbyPhoneRenderer implements BlockEntityRenderer<LobbyPhoneBlockEntity> {
    /** 기기 단위(mm) → 블록 픽셀 */
    private static final float K = (float) LobbyPhoneBlock.HEIGHT / 279f;
    private static final float LEFT_PX = 8f + (float) LobbyPhoneBlock.HALF_WIDTH;
    private static final float TOP_PX = (float) LobbyPhoneBlock.Y2;
    private static final float FRONT_PX = 16f - (float) LobbyPhoneBlock.DEPTH;

    private static final float INFO_X1 = 61, INFO_Y1 = 60, INFO_X2 = 135, INFO_Y2 = 102;
    private static final float DOOR_X = 126.4f, ICON_Y = 51.8f;
    private static final int DARK = 0xFF26306C;

    private final Font font;

    public LobbyPhoneRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(LobbyPhoneBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null || !be.isBacklight()) return;
        long gameTime = be.getLevel().getGameTime();
        Direction facing = be.getBlockState().getValue(LobbyPhoneBlock.FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        // 정면에서 볼 때 기기 왼쪽 위 모서리, 정면 바로 앞
        pose.translate(LEFT_PX / 16f, TOP_PX / 16f, FRONT_PX / 16f - 0.001);
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        float unit = K / 16f;
        pose.scale(unit, -unit, unit);

        if (be.isDoorOpen(gameTime)) {
            float is = 0.94f;
            draw(pose, buffers, icon(ICON_DOOR), DOOR_X - 3.75f * is, ICON_Y - 4 * is, is, 0xFFFFFFFF, false);
        }

        LobbyLcd lcd = LobbyLcd.of(be, gameTime);
        Screen sc = be.getScreen();
        float cx = (INFO_X1 + INFO_X2) / 2f;
        if (!lcd.topLeft().getString().isEmpty()) draw(pose, buffers, lcd.topLeft(), INFO_X1 + 4, INFO_Y1 + 3, 0.62f, DARK, false);
        if (!lcd.topRight().getString().isEmpty()) {
            float w = font.width(lcd.topRight()) * 0.52f;
            draw(pose, buffers, lcd.topRight(), INFO_X2 - 4 - w, INFO_Y1 + 3.5f, 0.52f, DARK, false);
        }
        if (sc == Screen.MESSAGE) {
            float s = 0.85f;
            int maxW = Math.round((INFO_X2 - INFO_X1 - 6) / s);
            var lines = font.split(lcd.big(), maxW);
            float y0 = (INFO_Y1 + INFO_Y2) / 2f - lines.size() * 10 * s / 2f;
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence line = lines.get(i);
                pose.pushPose();
                pose.translate(cx, y0 + i * 10 * s, 0);
                pose.scale(s, s, s);
                Matrix4f m = pose.last().pose();
                font.drawInBatch(line, -font.width(line) / 2f, 0, DARK, false, m, buffers,
                        Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        } else {
            float bs = lcd.bigScale();
            boolean bitmap = isBitmapBig(lcd.big());
            float textW = Math.max(1, font.width(lcd.big()) - (bitmap ? 1 : 0));
            float topPad = lcd.topLeft().getString().isEmpty() ? 4 : 10;
            bs = Math.min(bs, (INFO_X2 - INFO_X1 - 8) / textW);
            bs = Math.min(bs, (INFO_Y2 - INFO_Y1 - topPad - 3) / 8f);
            float areaMid = INFO_Y1 + topPad + (INFO_Y2 - INFO_Y1 - topPad - 3) / 2f;
            draw(pose, buffers, lcd.big(), cx, areaMid - (bitmap ? 4f : 4.6f) * bs, bs, DARK, true);
        }
        if (!lcd.subLeft().getString().isEmpty()) draw(pose, buffers, lcd.subLeft(), INFO_X1, INFO_Y2 + 6, 0.62f, 0xFFFFFFFF, false);
        if (!lcd.subRight().getString().isEmpty()) {
            float w = font.width(lcd.subRight()) * 0.62f;
            draw(pose, buffers, lcd.subRight(), INFO_X2 - w, INFO_Y2 + 6, 0.62f, 0xFFFFFFFF, false);
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
