package com.qwerty.homenet.client.lobby;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.Screen;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.lobby.LobbySettings;
import com.qwerty.homenet.network.LobbyKeyPacket;
import com.qwerty.homenet.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.*;

/**
 * 공동현관 로비폰 화면. 실제 기기(248 x 279 mm)를 그대로 그리고,
 * LCD 터치 키패드와 오른쪽 터치키(보안/호출/경비/취소)를 눌러서 조작한다.
 * 모든 좌표는 기기 단위(mm)이며 화면 크기에 맞춰 확대/축소한다.
 */
public class LobbyPhoneScreen extends net.minecraft.client.gui.screens.Screen {
    private static final ResourceLocation TEX = HomeNet.id("textures/gui/lobby_phone.png");
    private static final int DW = 248, DH = 279;

    // LCD 레이아웃 (기기 단위)
    private static final int LCD_X1 = 55, LCD_Y1 = 45, LCD_X2 = 142, LCD_Y2 = 199;
    private static final int INFO_X1 = 61, INFO_Y1 = 58, INFO_X2 = 135, INFO_Y2 = 101;
    private static final int PANEL_X1 = 58, PANEL_Y1 = 49, PANEL_X2 = 139, PANEL_Y2 = 117;
    private static final int KEY_X0 = 57, KEY_W = 26, KEY_STEP = 28, KEY_H = 16;
    private static final int[] KEY_ROWS = {120, 141, 162, 183};
    private static final float[] RIGHT_KEYS_Y = {133f, 154f, 174.5f, 195f};
    private static final int[] RIGHT_KEYS = {KEY_SECURITY, KEY_CALL, KEY_GUARD, KEY_CANCEL};
    private static final float[] DIGIT_SCALE = {1.5f, 1.9f, 2.3f};

    private final BlockPos pos;
    private float s = 1f;
    private int left, top;

    private int pressedSlot = -1;     // 0~11 키패드, 12~15 오른쪽 키
    private long pressedAt;
    private boolean wasTalking;

    private EditBox msgBox;
    private String msgDraft = "";

    public LobbyPhoneScreen(BlockPos pos) {
        super(Component.translatable("block." + HomeNet.MODID + ".lobby_phone"));
        this.pos = pos;
    }

    private LobbyPhoneBlockEntity be() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        return level.getBlockEntity(pos) instanceof LobbyPhoneBlockEntity b ? b : null;
    }

    private boolean talking(LobbyPhoneBlockEntity be) {
        return be != null && be.getScreen() == Screen.TALKING && be.isConnected();
    }

    @Override
    protected void init() {
        s = Math.min((height - 12) / (float) DH, 1.6f);
        s = Math.max(s, 0.5f);
        int devW = Math.round(DW * s), devH = Math.round(DH * s);
        LobbyPhoneBlockEntity be = be();
        boolean talk = talking(be);
        wasTalking = talk;
        // 통화 중이면 오른쪽에 대화창을 둘 자리를 만든다
        int chatW = talk ? 160 : 0;
        left = (width - devW - chatW) / 2;
        top = (height - devH) / 2;

        msgBox = null;
        if (talk) {
            int cx = left + devW + 10;
            int cy = top + devH - 60;
            msgBox = new EditBox(font, cx, cy, 148, 18, Component.translatable("gui." + HomeNet.MODID + ".message"));
            msgBox.setMaxLength(IntercomLine.MAX_TEXT);
            msgBox.setValue(msgDraft);
            msgBox.setResponder(t -> msgDraft = t);
            msgBox.setHint(Component.translatable("gui." + HomeNet.MODID + ".message_hint"));
            addRenderableWidget(msgBox);
            addRenderableWidget(Button.builder(Component.translatable("gui." + HomeNet.MODID + ".send"), b -> sendMessage())
                    .bounds(cx, cy + 22, 148, 20).build());
            setFocused(msgBox);
        }
    }

    private void sendMessage() {
        String text = Intercom.sanitize(msgDraft, IntercomLine.MAX_TEXT);
        if (text.isEmpty()) return;
        ModNetwork.sendToServer(new LobbyKeyPacket(pos, KEY_MESSAGE, text));
        msgDraft = "";
        if (msgBox != null) msgBox.setValue("");
    }

    private void press(int key, int slot) {
        pressedSlot = slot;
        pressedAt = System.currentTimeMillis();
        ModNetwork.sendToServer(new LobbyKeyPacket(pos, key, ""));
    }

    // ================================================================== 입력

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        LobbyPhoneBlockEntity be = be();
        if (be == null) return false;
        float u = (float) ((mx - left) / s);
        float v = (float) ((my - top) / s);

        // LCD 키패드
        for (int slot = 0; slot < 12; slot++) {
            int kx = KEY_X0 + (slot % 3) * KEY_STEP;
            int ky = KEY_ROWS[slot / 3];
            if (u >= kx && u < kx + KEY_W && v >= ky && v < ky + KEY_H) {
                int key;
                if (slot < 9) key = be.getLayout()[slot];
                else if (slot == 10) key = be.getLayout()[9];
                else key = slot == 9 ? KEY_LEFT : KEY_RIGHT;
                press(key, slot);
                return true;
            }
        }
        // 오른쪽 터치키
        for (int i = 0; i < 4; i++) {
            float ky = RIGHT_KEYS_Y[i];
            if (u >= 150 && u <= 200 && v >= ky - 9 && v <= ky + 9) {
                press(RIGHT_KEYS[i], 12 + i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (msgBox != null && msgBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                sendMessage();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        // 키보드 숫자도 키패드로 입력
        int digit = -1;
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) digit = key - GLFW.GLFW_KEY_0;
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) digit = key - GLFW.GLFW_KEY_KP_0;
        if (digit >= 0) {
            LobbyPhoneBlockEntity be = be();
            int slot = -1;
            if (be != null) {
                int[] layout = be.getLayout();
                for (int i = 0; i < 10; i++) if (layout[i] == digit) slot = i < 9 ? i : 10;
            }
            press(digit, slot);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            press(KEY_CANCEL, 15);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (msgBox != null) msgBox.tick();
        LobbyPhoneBlockEntity be = be();
        var player = Minecraft.getInstance().player;
        if (be == null || player == null || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64) {
            onClose();
            return;
        }
        if (talking(be) != wasTalking) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================== 그리기

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LobbyPhoneBlockEntity be = be();
        if (be == null) return;
        long gameTime = Minecraft.getInstance().level.getGameTime();

        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(s, s, 1);

        g.blit(TEX, 0, 0, DW, DH, 0f, 0f, DW * 2, DH * 2, DW * 2, DH * 2);
        drawLcd(g, be, gameTime);
        drawRightKeys(g, be, mouseX, mouseY);

        pose.popPose();

        if (talking(be)) drawChat(g, be);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isPressed(int slot) {
        return pressedSlot == slot && System.currentTimeMillis() - pressedAt < 160;
    }

    private void drawLcd(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime) {
        Screen sc = be.getScreen();
        // 배경
        g.fillGradient(LCD_X1, LCD_Y1, LCD_X2, LCD_Y2, 0xFF2A3E9C, 0xFF141E58);
        // 잔무늬
        for (int y = LCD_Y1 + 2; y < LCD_Y2; y += 4) {
            for (int x = LCD_X1 + ((y / 4) % 2) * 2; x < LCD_X2; x += 4) {
                g.fill(x, y, x + 1, y + 1, 0x1AFFFFFF);
            }
        }
        // 상단 아이콘
        g.fill(136, 47, 141, 52, 0xFF4A78E0);
        g.fill(137, 48, 140, 51, 0xFFC8D6FA);

        switch (sc) {
            case HELP -> drawHelp(g);
            case ADMIN_PASSWORD -> drawAdminPassword(g, be);
            case ADMIN_MENU -> drawAdminMenu(g, be);
            case ADMIN_EDIT -> drawAdminEdit(g, be, gameTime);
            default -> drawInfo(g, LobbyLcd.of(be, gameTime), sc == Screen.MESSAGE && !"opened".equals(be.getMsgKey()));
        }
        drawKeypad(g, be);

        if (!be.isBacklight()) {
            g.fill(LCD_X1, LCD_Y1, LCD_X2, LCD_Y2, 0xC0000000);
        }
    }

    private void drawInfo(GuiGraphics g, LobbyLcd lcd, boolean wrapBig) {
        g.fillGradient(INFO_X1, INFO_Y1, INFO_X2, INFO_Y2, 0xFFF6F8FD, 0xFFC9D1EA);
        int dark = 0xFF1E2A66;
        if (!lcd.topLeft().getString().isEmpty()) text(g, lcd.topLeft(), INFO_X1 + 3, INFO_Y1 + 3, 0.7f, dark, false);
        if (!lcd.topRight().getString().isEmpty()) textRight(g, lcd.topRight(), INFO_X2 - 3, INFO_Y1 + 3, 0.6f, dark);

        float cx = (INFO_X1 + INFO_X2) / 2f;
        if (wrapBig) {
            // 안내 문구: 박스 폭에 맞춰 줄바꿈
            int maxW = Math.round((INFO_X2 - INFO_X1 - 6) / 0.9f);
            var lines = font.split(lcd.big(), maxW);
            float y0 = (INFO_Y1 + INFO_Y2) / 2f - lines.size() * 9 * 0.9f / 2f;
            for (int i = 0; i < lines.size(); i++) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(cx, y0 + i * 9 * 0.9f, 0);
                pose.scale(0.9f, 0.9f, 1);
                g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, 0, dark, false);
                pose.popPose();
            }
        } else {
            float bs = lcd.bigScale();
            float maxW = (INFO_X2 - INFO_X1 - 6);
            float w = font.width(lcd.big()) * bs;
            if (w > maxW) bs *= maxW / w;
            float y = (lcd.topLeft().getString().isEmpty() ? (INFO_Y1 + INFO_Y2) / 2f : (INFO_Y1 + INFO_Y2) / 2f + 3) - 3.5f * bs;
            text(g, lcd.big(), cx, y, bs, dark, true);
        }
        if (!lcd.subLeft().getString().isEmpty()) text(g, lcd.subLeft(), INFO_X1, INFO_Y2 + 6, 0.65f, 0xFFFFFFFF, false);
        if (!lcd.subRight().getString().isEmpty()) textRight(g, lcd.subRight(), INFO_X2, INFO_Y2 + 6, 0.65f, 0xFFFFFFFF);
    }

    private void panel(GuiGraphics g) {
        g.fill(PANEL_X1, PANEL_Y1, PANEL_X2, PANEL_Y2, 0xFFF1F3F8);
        g.renderOutline(PANEL_X1, PANEL_Y1, PANEL_X2 - PANEL_X1, PANEL_Y2 - PANEL_Y1, 0xFF9AA2B6);
    }

    private void adminHeader(GuiGraphics g) {
        textFit(g, LobbyLcd.tr("admin_header"), PANEL_X1 + 2, PANEL_Y1 + 2, 0.42f, PANEL_X2 - PANEL_X1 - 14, 0xFF30364A, false);
        textFit(g, LobbyLcd.tr("admin_hint"), PANEL_X1 + 2, PANEL_Y1 + 7, 0.42f, PANEL_X2 - PANEL_X1 - 4, 0xFF30364A, false);
        g.fill(PANEL_X1 + 1, PANEL_Y1 + 12, PANEL_X2 - 1, PANEL_Y1 + 13, 0xFFB0B6C6);
    }

    private void drawHelp(GuiGraphics g) {
        panel(g);
        g.fill(PANEL_X1 + 1, PANEL_Y1 + 1, PANEL_X2 - 1, PANEL_Y1 + 11, 0xFF2D3F8E);
        text(g, LobbyLcd.tr("help_title"), PANEL_X1 + 4, PANEL_Y1 + 3, 0.7f, 0xFFFFFFFF, false);
        for (int i = 0; i < 6; i++) {
            textFit(g, LobbyLcd.tr("help_" + (i + 1)), PANEL_X1 + 3, PANEL_Y1 + 15 + i * 8.5f, 0.5f, PANEL_X2 - PANEL_X1 - 6, 0xFF20242E, false);
        }
    }

    private void drawAdminPassword(GuiGraphics g, LobbyPhoneBlockEntity be) {
        panel(g);
        adminHeader(g);
        float cx = (PANEL_X1 + PANEL_X2) / 2f;
        textFit(g, LobbyLcd.tr("admin_pw_title"), cx, PANEL_Y1 + 18, 0.6f, PANEL_X2 - PANEL_X1 - 6, 0xFF20242E, true);
        g.renderOutline(PANEL_X1 + 6, PANEL_Y1 + 27, PANEL_X2 - PANEL_X1 - 12, 22, 0xFF9AA2B6);
        String stars = "*".repeat(be.getSecretLength());
        text(g, Component.literal(stars), cx, PANEL_Y1 + 33, 1.6f, 0xFF20242E, true);
        text(g, LobbyLcd.tr("enter_password"), cx, PANEL_Y1 + 55, 0.5f, 0xFF20242E, true);
    }

    private void drawAdminMenu(GuiGraphics g, LobbyPhoneBlockEntity be) {
        panel(g);
        adminHeader(g);
        int page = be.getAdminPage();
        float rowH = 7.4f;
        float y0 = PANEL_Y1 + 15;
        for (int i = 0; i < 7; i++) {
            LobbySettings.Item item = LobbySettings.item(page, i);
            if (item == null) continue;
            float y = y0 + i * rowH;
            Component value = Component.literal(be.displaySetting(page, i));
            float valueW = font.width(value) * 0.48f;
            textFit(g, Component.literal((i + 1) + " ").append(LobbyLcd.tr("setting." + item.key())),
                    PANEL_X1 + 2, y, 0.48f, PANEL_X2 - PANEL_X1 - 8 - valueW, 0xFF20242E, false);
            textRight(g, value, PANEL_X2 - 2, y, 0.48f, 0xFF20242E);
            g.fill(PANEL_X1 + 1, Math.round(y + rowH - 1.6f), PANEL_X2 - 1, Math.round(y + rowH - 1.6f) + 1, 0x40707890);
        }
        textRight(g, Component.literal((page + 1) + "/" + LobbySettings.pageCount()), PANEL_X2 - 2, PANEL_Y1 + 2, 0.42f, 0xFF30364A);
    }

    private void drawAdminEdit(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime) {
        panel(g);
        adminHeader(g);
        LobbySettings.Item item = LobbySettings.item(be.getAdminPage(), be.getEditItem());
        if (item == null) return;
        float cx = (PANEL_X1 + PANEL_X2) / 2f;
        textFit(g, LobbyLcd.tr("setting." + item.key()), cx, PANEL_Y1 + 16, 0.6f, PANEL_X2 - PANEL_X1 - 6, 0xFF20242E, true);
        g.renderOutline(PANEL_X1 + 6, PANEL_Y1 + 24, PANEL_X2 - PANEL_X1 - 12, 20, 0xFF9AA2B6);
        String v = be.getEditValue();
        boolean blink = (gameTime / 10) % 2 == 0;
        text(g, Component.literal(v + (blink ? "▌" : " ")), cx, PANEL_Y1 + 29, 1.3f, 0xFF20242E, true);
        Component range = switch (item.kind()) {
            case NUMBER -> LobbyLcd.tr("range_number", item.min(), item.max());
            case DIGITS -> LobbyLcd.tr("range_digits", item.max());
            case PASSWORD -> item.min() == 0 ? LobbyLcd.tr("range_password_optional") : LobbyLcd.tr("range_password");
        };
        textFit(g, range, cx, PANEL_Y1 + 47, 0.45f, PANEL_X2 - PANEL_X1 - 6, 0xFF20242E, true);
        textFit(g, LobbyLcd.tr("setting_desc." + item.key()), cx, PANEL_Y1 + 53, 0.45f, PANEL_X2 - PANEL_X1 - 6, 0xFF20242E, true);
        g.fill(PANEL_X1 + 1, PANEL_Y2 - 9, PANEL_X2 - 1, PANEL_Y2 - 8, 0xFFB0B6C6);
        textFit(g, LobbyLcd.tr("edit_hint"), cx, PANEL_Y2 - 6.5f, 0.42f, PANEL_X2 - PANEL_X1 - 6, 0xFF30364A, true);
    }

    private Component[] bottomLabels(LobbyPhoneBlockEntity be) {
        Component cancel = LobbyLcd.tr("key.cancel");
        Component hash = Component.literal("#");
        return switch (be.getScreen()) {
            case IDLE -> new Component[]{LobbyLcd.tr("key.help"),
                    be.isCommonPasswordUse() ? LobbyLcd.tr("key.common_pw") : hash};
            case INPUT -> be.isDongStage()
                    ? new Component[]{LobbyLcd.tr("key.dong"), LobbyLcd.tr("key.dong")}
                    : new Component[]{cancel, LobbyLcd.tr("key.password")};
            case PASSWORD, COMMON_PASSWORD, ADMIN_PASSWORD -> new Component[]{cancel, LobbyLcd.tr("key.ok")};
            case ADMIN_MENU -> new Component[]{Component.literal("←"), Component.literal("→")};
            case ADMIN_EDIT -> new Component[]{cancel, Component.empty()};
            case MESSAGE -> new Component[]{Component.empty(), Component.empty()};
            default -> new Component[]{cancel, hash};
        };
    }

    private void drawKeypad(GuiGraphics g, LobbyPhoneBlockEntity be) {
        int[] layout = be.getLayout();
        Component[] bottom = bottomLabels(be);
        float ds = DIGIT_SCALE[Math.max(0, Math.min(2, be.getDigitSize()))];
        for (int slot = 0; slot < 12; slot++) {
            int kx = KEY_X0 + (slot % 3) * KEY_STEP;
            int ky = KEY_ROWS[slot / 3];
            boolean pressed = isPressed(slot);
            g.fillGradient(kx, ky, kx + KEY_W, ky + KEY_H,
                    pressed ? 0xFF8CA2F4 : 0xFF5470D2, pressed ? 0xFF4258BC : 0xFF22348A);
            g.fill(kx, ky, kx + KEY_W, ky + 1, pressed ? 0xFFB8C6FA : 0xFF7E96E6);
            g.renderOutline(kx, ky, KEY_W, KEY_H, 0xFF16205A);
            float cx = kx + KEY_W / 2f;
            float cy = ky + KEY_H / 2f;
            if (slot < 9 || slot == 10) {
                int d = slot < 9 ? layout[slot] : layout[9];
                text(g, Component.literal(String.valueOf(d)), cx, cy - 3.6f * ds, ds, 0xFFFFFFFF, true);
            } else {
                Component label = bottom[slot == 9 ? 0 : 1];
                String[] parts = label.getString().split("\n");
                float ls = parts.length > 1 ? 0.55f : 0.75f;
                for (String p : parts) {
                    float pw = font.width(p) * ls;
                    if (pw > KEY_W - 3) ls *= (KEY_W - 3) / pw;
                }
                float lineH = 9 * ls;
                float y = cy - parts.length * lineH / 2f + 0.5f;
                for (String p : parts) {
                    text(g, Component.literal(p).withStyle(ChatFormatting.BOLD), cx, y, ls, 0xFFFFFFFF, true);
                    y += lineH;
                }
            }
        }
    }

    private void drawRightKeys(GuiGraphics g, LobbyPhoneBlockEntity be, int mouseX, int mouseY) {
        float u = (mouseX - left) / s;
        float v = (mouseY - top) / s;
        for (int i = 0; i < 4; i++) {
            float ky = RIGHT_KEYS_Y[i];
            int y1 = Math.round(ky - 8), y2 = Math.round(ky + 7);
            if (isPressed(12 + i)) {
                g.fill(151, y1, 199, y2, 0x60A0B8FF);
            } else if (u >= 150 && u <= 200 && v >= ky - 9 && v <= ky + 9) {
                g.fill(151, y1, 199, y2, 0x24FFFFFF);
            }
        }
        // 키 LED 상시 OFF 이고 화면이 꺼져 있으면 아이콘을 어둡게
        if (!be.isKeyLedAlways() && !be.isBacklight()) {
            g.fill(151, 122, 199, 205, 0xA0000000);
        }
    }

    private void drawChat(GuiGraphics g, LobbyPhoneBlockEntity be) {
        int devW = Math.round(DW * s), devH = Math.round(DH * s);
        int x = left + devW + 10;
        int y = top + 20;
        int w = 148;
        int bottom = top + devH - 66;
        g.fill(x - 4, y - 4, x + w + 4, bottom, 0xC0101830);
        g.drawString(font, LobbyLcd.tr(be.isGuardCall() ? "talking_guard" : "talking_unit"), x, y, 0xFF6EE07A, false);
        List<IntercomLine> log = be.getLog();
        int lineY = y + 14;
        int maxLines = Math.max(1, (bottom - lineY - 4) / 11);
        int start = Math.max(0, log.size() - maxLines);
        for (int i = start; i < log.size(); i++) {
            IntercomLine l = log.get(i);
            Component c = Component.literal("[").append(Intercom.sideName(l.side())).append("] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(l.name() + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(l.text()).withStyle(ChatFormatting.WHITE));
            g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(c, w)), x, lineY, 0xFFFFFFFF, false);
            lineY += 11;
        }
        if (log.isEmpty()) {
            g.drawString(font, Component.translatable("gui." + HomeNet.MODID + ".log_empty"), x, lineY, 0xFF9AA6B6, false);
        }
    }

    // ------------------------------------------------------------------ 글자 도우미 (기기 단위 좌표)

    /** maxW(기기 단위)를 넘으면 글자를 줄여서 맞춤 */
    private void textFit(GuiGraphics g, Component c, float x, float y, float scale, float maxW, int color, boolean center) {
        float w = font.width(c) * scale;
        if (w > maxW && w > 0) scale *= maxW / w;
        text(g, c, x, y, scale, color, center);
    }

    private void text(GuiGraphics g, Component c, float x, float y, float scale, int color, boolean center) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        int w = font.width(c);
        g.drawString(font, c, center ? -w / 2 : 0, 0, color, false);
        pose.popPose();
    }

    private void textRight(GuiGraphics g, Component c, float xRight, float y, float scale, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(xRight, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, c, -font.width(c), 0, color, false);
        pose.popPose();
    }
}
