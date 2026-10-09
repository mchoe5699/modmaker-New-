package com.qwerty.homenet.client.lobby;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.Screen;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.item.RfCardItem;
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
import static com.qwerty.homenet.client.lobby.LobbyLcd.*;

/**
 * 공동현관 로비폰 화면. 실제 기기(248 x 279 mm)를 사진/영상/설명서대로 그리고,
 * LCD 터치 키패드, 오른쪽 터치키(보안/호출/경비/취소), 카드 인식부를 눌러 조작한다.
 * 좌표는 모두 기기 단위(mm).
 */
public class LobbyPhoneScreen extends net.minecraft.client.gui.screens.Screen {
    private static final ResourceLocation TEX = HomeNet.id("textures/gui/lobby_phone.png");
    private static final int DW = 248, DH = 279, TEX_SCALE = 4;

    // LCD 레이아웃 (tools/gen_lobby_phone.py 와 같음)
    private static final int LCD_X1 = 55, LCD_Y1 = 45, LCD_X2 = 142, LCD_Y2 = 199;
    private static final float INFO_X1 = 61, INFO_Y1 = 60, INFO_X2 = 135, INFO_Y2 = 102;
    private static final float PANEL_X1 = 57, PANEL_Y1 = 57, PANEL_X2 = 140, PANEL_Y2 = 117;
    private static final int KEY_X0 = 57, KEY_W = 27, KEY_STEP = 28, KEY_H = 16;
    private static final int[] KEY_ROWS = {120, 141, 162, 183};
    private static final float[] RIGHT_KEYS_Y = {133f, 154f, 174.5f, 195f};
    private static final int[] RIGHT_KEYS = {KEY_SECURITY, KEY_CALL, KEY_GUARD, KEY_CANCEL};
    private static final float NET_X = 134.8f, ICON_Y = 51.8f, DOOR_X = 126.4f;
    /** 숫자 크기 (작은/보통/큰). 숫자 높이 7단위 x 1.6 = 11.2 < 키 높이 16 */
    private static final float[] DIGIT_SCALE = {1.0f, 1.3f, 1.6f};
    private static final float CARD_X1 = 64, CARD_Y1 = 207, CARD_X2 = 140, CARD_Y2 = 236;

    private static final int PANEL_BG = 0xFFF3F5FA;
    private static final int PANEL_LINE = 0xFF9EA4B8;
    private static final int PANEL_TEXT = 0xFF3A4058;
    private static final int INFO_TEXT = 0xFF26306C;

    private final BlockPos pos;
    private float s = 1f;
    private int left, top;

    private int pressedSlot = -1;
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
        s = Math.max(0.5f, Math.min((height - 12) / (float) DH, 1.6f));
        int devW = Math.round(DW * s), devH = Math.round(DH * s);
        LobbyPhoneBlockEntity be = be();
        boolean talk = talking(be);
        wasTalking = talk;
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
        for (int i = 0; i < 4; i++) {
            float ky = RIGHT_KEYS_Y[i];
            if (u >= 150 && u <= 200 && v >= ky - 9 && v <= ky + 9) {
                press(RIGHT_KEYS[i], 12 + i);
                return true;
            }
        }
        // 카드 인식부: 손에 출입 카드를 들고 누르면 카드 접촉
        if (u >= CARD_X1 && u <= CARD_X2 && v >= CARD_Y1 && v <= CARD_Y2) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getMainHandItem().getItem() instanceof RfCardItem) {
                press(KEY_CARD, 16);
            } else if (player != null) {
                player.displayClientMessage(Component.translatable("msg." + HomeNet.MODID + ".hold_card"), true);
            }
            return true;
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

        g.blit(TEX, 0, 0, DW, DH, 0f, 0f, DW * TEX_SCALE, DH * TEX_SCALE, DW * TEX_SCALE, DH * TEX_SCALE);
        drawLcd(g, be, gameTime);
        drawRightKeys(g, be, gameTime, mouseX, mouseY);
        if (isPressed(16)) fillF(g, CARD_X1, CARD_Y1, CARD_X2, CARD_Y2, 0x40A0B8FF);

        pose.popPose();

        if (talking(be)) drawChat(g, be);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isPressed(int slot) {
        return pressedSlot == slot && System.currentTimeMillis() - pressedAt < 160;
    }

    private void drawLcd(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime) {
        Screen sc = be.getScreen();
        switch (sc) {
            case HELP -> drawHelp(g);
            case ADMIN_PASSWORD -> drawAdminPassword(g, be);
            case ADMIN_MENU -> drawAdminMenu(g, be);
            case ADMIN_EDIT -> drawAdminEdit(g, be, gameTime);
            case CARD_MENU -> drawMenu(g, "card_menu_title", 3, "card_menu_");
            case CARD_UNIT_MENU -> drawMenu(g, "card_unit_menu_title", 4, "card_unit_menu_");
            case PROX_MENU -> drawMenu(g, "prox_menu_title", 2, "prox_menu_");
            case CARD_MASTER, CARD_REG, CARD_DELETE, CARD_DELETE_ALL, CARD_UNIT_REG, CARD_UNIT_DELETE -> drawCardScreen(g, be, gameTime);
            default -> drawInfo(g, LobbyLcd.of(be, gameTime), sc == Screen.MESSAGE);
        }
        // 문열림 아이콘 (네트워크 아이콘 옆, 문열림 시간 동안)
        if (be.isDoorOpen(gameTime)) {
            float is = 0.94f;
            text(g, icon(ICON_DOOR), DOOR_X - 3.75f * is, ICON_Y - 4 * is, is, 0xFFFFFFFF, false);
        }
        drawKeypad(g, be);
        if (!be.isBacklight()) fillF(g, LCD_X1, LCD_Y1, LCD_X2, LCD_Y2, 0xC8000000);
    }

    // ------------------------------------------------------------------ 정보 박스 (시계, 번호, 비밀번호, 호출)

    private void drawInfo(GuiGraphics g, LobbyLcd lcd, boolean wrap) {
        // 박스: 둥근 모서리 + 흰 안쪽선 (텍스처와 같은 모양)
        infoBox(g);
        if (!lcd.topLeft().getString().isEmpty()) text(g, lcd.topLeft(), INFO_X1 + 4, INFO_Y1 + 3, 0.62f, INFO_TEXT, false);
        if (!lcd.topRight().getString().isEmpty()) textRight(g, lcd.topRight(), INFO_X2 - 4, INFO_Y1 + 3.5f, 0.52f, INFO_TEXT);
        float cx = (INFO_X1 + INFO_X2) / 2f;
        if (wrap) {
            float sc = 0.85f;
            int maxW = Math.round((INFO_X2 - INFO_X1 - 6) / sc);
            var lines = font.split(lcd.big(), maxW);
            float y0 = (INFO_Y1 + INFO_Y2) / 2f - lines.size() * 10 * sc / 2f;
            for (int i = 0; i < lines.size(); i++) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(cx, y0 + i * 10 * sc, 0);
                pose.scale(sc, sc, 1);
                g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, 0, INFO_TEXT, false);
                pose.popPose();
            }
        } else {
            drawBig(g, lcd, cx);
        }
        if (!lcd.subLeft().getString().isEmpty()) text(g, lcd.subLeft(), INFO_X1, INFO_Y2 + 6, 0.62f, 0xFFFFFFFF, false);
        if (!lcd.subRight().getString().isEmpty()) textRight(g, lcd.subRight(), INFO_X2, INFO_Y2 + 6, 0.62f, 0xFFFFFFFF);
    }

    /** 큰 글자: 박스 안에 꼭 들어가도록 크기 조정 */
    private void drawBig(GuiGraphics g, LobbyLcd lcd, float cx) {
        float bs = lcd.bigScale();
        boolean bitmap = isBitmapBig(lcd.big());
        float textW = Math.max(1, font.width(lcd.big()) - (bitmap ? 1 : 0));
        float maxW = INFO_X2 - INFO_X1 - 8;
        float topPad = lcd.topLeft().getString().isEmpty() ? 4 : 10;
        float maxH = INFO_Y2 - INFO_Y1 - topPad - 3;
        bs = Math.min(bs, maxW / textW);
        bs = Math.min(bs, maxH / 8f);
        float areaMid = INFO_Y1 + topPad + (INFO_Y2 - INFO_Y1 - topPad - 3) / 2f;
        float y = areaMid - (bitmap ? 4f : 4.6f) * bs;
        text(g, lcd.big(), cx, y, bs, INFO_TEXT, true);
    }

    private void infoBox(GuiGraphics g) {
        fillF(g, INFO_X1 + 1, INFO_Y1, INFO_X2 - 1, INFO_Y2, 0xFF4E5890);
        fillF(g, INFO_X1, INFO_Y1 + 1, INFO_X2, INFO_Y2 - 1, 0xFF4E5890);
        gradF(g, INFO_X1 + 1, INFO_Y1 + 0.5f, INFO_X2 - 1, INFO_Y2 - 0.5f, 0xFFF0F2FA, 0xFFCED5EC);
        gradF(g, INFO_X1 + 0.5f, INFO_Y1 + 1, INFO_X2 - 0.5f, INFO_Y2 - 1, 0xFFF0F2FA, 0xFFCED5EC);
        fillF(g, INFO_X1 + 1.2f, INFO_Y1 + 0.9f, INFO_X2 - 1.2f, INFO_Y1 + 1.2f, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ 흰 패널 화면 (관리자 / RF 카드 / 도움말)

    private void panel(GuiGraphics g) {
        fillF(g, PANEL_X1, PANEL_Y1, PANEL_X2, PANEL_Y2, PANEL_BG);
        outline(g, PANEL_X1, PANEL_Y1, PANEL_X2, PANEL_Y2, PANEL_LINE);
    }

    /** 1줄: App Ver / FW Ver, 2줄: '0'번 - RF 카드 설정, '9'번 - 근접센서 설정 */
    private float adminHeader(GuiGraphics g, boolean secondLine) {
        text(g, lcd(LobbySettings.APP_VER), PANEL_X1 + 1.5f, PANEL_Y1 + 1.5f, 0.36f, PANEL_TEXT, false);
        textRight(g, lcd(LobbySettings.FW_VER), PANEL_X2 - 1.5f, PANEL_Y1 + 1.5f, 0.36f, PANEL_TEXT);
        if (!secondLine) return PANEL_Y1 + 6;
        textFit(g, tr("admin_hint"), (PANEL_X1 + PANEL_X2) / 2f, PANEL_Y1 + 6f, 0.36f, PANEL_X2 - PANEL_X1 - 3, PANEL_TEXT, true);
        return PANEL_Y1 + 11;
    }

    /** 테두리 상자 + 가로줄. rows 개의 줄, 반환: 줄 높이 */
    private float tableBox(GuiGraphics g, float y1, float y2, int rows) {
        float bx1 = PANEL_X1 + 1.5f, bx2 = PANEL_X2 - 1.5f;
        outline(g, bx1, y1, bx2, y2, PANEL_LINE);
        float rh = (y2 - y1) / rows;
        for (int i = 1; i < rows; i++) {
            float y = y1 + i * rh;
            fillF(g, bx1, y, bx2, y + 0.3f, PANEL_LINE);
        }
        return rh;
    }

    private void drawAdminPassword(GuiGraphics g, LobbyPhoneBlockEntity be) {
        panel(g);
        float y = adminHeader(g, false) + 2;
        float y2 = PANEL_Y2 - 3;
        float bx1 = PANEL_X1 + 1.5f, bx2 = PANEL_X2 - 1.5f, cx = (PANEL_X1 + PANEL_X2) / 2f;
        outline(g, bx1, y, bx2, y2, PANEL_LINE);
        fillF(g, bx1, y + 7, bx2, y + 7.3f, PANEL_LINE);
        fillF(g, bx1, y2 - 7, bx2, y2 - 6.7f, PANEL_LINE);
        text(g, tr("admin_pw_title"), cx, y + 2, 0.42f, PANEL_TEXT, true);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < be.getSecretLength(); i++) stars.append(i == 0 ? "*" : "  *");
        text(g, lcd(stars.toString()), cx + 6, (y + y2) / 2f - 3, 1.1f, PANEL_TEXT, true);
        text(g, tr("enter_password_admin"), cx, y2 - 5, 0.42f, PANEL_TEXT, true);
    }

    private void drawAdminMenu(GuiGraphics g, LobbyPhoneBlockEntity be) {
        panel(g);
        float y = adminHeader(g, true) + 1;
        int page = be.getAdminPage();
        float rh = tableBox(g, y, PANEL_Y2 - 2, 7);
        float div = PANEL_X1 + 1.5f + (PANEL_X2 - PANEL_X1 - 3) * 0.55f;
        fillF(g, div, y, div + 0.3f, PANEL_Y2 - 2, PANEL_LINE);
        for (int i = 0; i < 7; i++) {
            LobbySettings.Item item = LobbySettings.item(page, i);
            if (item == null) continue;
            float ry = y + i * rh + (rh - 3.6f) / 2f;
            textFit(g, Component.literal((i + 1) + " ").append(tr("setting." + item.key())).withStyle(st -> st.withFont(FONT)),
                    PANEL_X1 + 2.5f, ry, 0.38f, div - PANEL_X1 - 3.5f, PANEL_TEXT, false);
            Component val = lcd(be.displaySetting(page, i));
            float vw = font.width(val) * 0.38f;
            float maxVw = PANEL_X2 - div - 4;
            float vs = vw > maxVw ? 0.38f * maxVw / vw : 0.38f;
            textRight(g, val, PANEL_X2 - 2.5f, ry, vs, PANEL_TEXT);
        }
    }

    private void drawAdminEdit(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime) {
        panel(g);
        LobbySettings.Item item = LobbySettings.item(be.getAdminPage(), be.getEditItem());
        float y = adminHeader(g, true) + 1;
        if (item == null) return;
        float y2 = PANEL_Y2 - 2;
        float bx1 = PANEL_X1 + 1.5f, bx2 = PANEL_X2 - 1.5f, cx = (PANEL_X1 + PANEL_X2) / 2f;
        outline(g, bx1, y, bx2, y2, PANEL_LINE);
        fillF(g, bx1, y + 6, bx2, y + 6.3f, PANEL_LINE);
        fillF(g, bx1, y2 - 6, bx2, y2 - 5.7f, PANEL_LINE);
        text(g, tr("setting." + item.key()), cx, y + 1.6f, 0.42f, PANEL_TEXT, true);
        boolean blink = (gameTime / 10) % 2 == 0;

        if (item.kind() == LobbySettings.Kind.LOBBY_NO) {
            // 로비번호: 동 / 라인
            text(g, tr("lobby_no_desc1"), cx, y + 8, 0.38f, PANEL_TEXT, true);
            String typing = be.getEditValue();
            Component cur = lcd(LobbySettings.lobbyNoDisplay(be.getEditDong() + "|" + be.getEditLine()));
            text(g, cur, cx, y + 14, 0.8f, PANEL_TEXT, true);
            text(g, lcd(typing), cx, y + 23, 1.0f, PANEL_TEXT, true);
            if (blink) {
                float tw = font.width(lcd(typing)) * 1.0f;
                fillF(g, cx + tw / 2 + 0.5f, y + 23, cx + tw / 2 + 4, y + 30, PANEL_TEXT);
            }
            text(g, tr("lobby_no_desc2"), cx, y2 - 15, 0.36f, PANEL_TEXT, true);
            text(g, tr("lobby_no_desc3"), cx, y2 - 10.5f, 0.36f, PANEL_TEXT, true);
        } else {
            String v = be.getEditValue();
            float vs = 1.3f;
            Component val = lcd(v);
            float tw = font.width(val) * vs;
            float vy = y + 10;
            text(g, val, cx, vy, vs, PANEL_TEXT, true);
            if (blink) fillF(g, cx + tw / 2 + 0.6f, vy + 0.5f, cx + tw / 2 + 5, vy + 11, PANEL_TEXT);
            textFit(g, tr("setting_desc1." + item.key()), cx, y2 - 16, 0.38f, bx2 - bx1 - 2, PANEL_TEXT, true);
            textFit(g, tr("setting_desc2." + item.key()), cx, y2 - 11, 0.38f, bx2 - bx1 - 2, PANEL_TEXT, true);
        }
        textFit(g, tr("edit_hint"), cx, y2 - 4.4f, 0.38f, bx2 - bx1 - 2, PANEL_TEXT, true);
    }

    /** 번호 메뉴: 제목 + 항목 + "호출, 경비를 누르면 상위메뉴로 이동" */
    private void drawMenu(GuiGraphics g, String titleKey, int items, String itemPrefix) {
        panel(g);
        float y = adminHeader(g, true) + 1;
        float rh = tableBox(g, y, PANEL_Y2 - 2, 8);
        text(g, tr(titleKey), (PANEL_X1 + PANEL_X2) / 2f, y + (rh - 3.6f) / 2f, 0.4f, PANEL_TEXT, true);
        for (int i = 0; i < items; i++) {
            textFit(g, tr(itemPrefix + (i + 1)), PANEL_X1 + 3, y + (i + 1) * rh + (rh - 3.6f) / 2f, 0.4f,
                    PANEL_X2 - PANEL_X1 - 6, PANEL_TEXT, false);
        }
        text(g, tr("menu_footer"), (PANEL_X1 + PANEL_X2) / 2f, y + 7 * rh + (rh - 3.6f) / 2f, 0.38f, PANEL_TEXT, true);
    }

    private void drawCardScreen(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime) {
        panel(g);
        float y = adminHeader(g, true) + 1;
        float rh = tableBox(g, y, PANEL_Y2 - 2, 8);
        Screen sc = be.getScreen();
        Component[] comps = new Component[6];
        String title;
        String notice = be.activeNotice(gameTime);
        switch (sc) {
            case CARD_MASTER, CARD_REG -> {
                title = sc == Screen.CARD_MASTER ? "card_master_title" : "card_reg_title";
                comps[0] = tr("card_touch");
                comps[1] = tr("card_press0");
                if (!be.getPendingCard().isEmpty()) comps[2] = tr("card_number", be.getPendingCard());
            }
            case CARD_UNIT_REG, CARD_UNIT_DELETE -> {
                title = sc == Screen.CARD_UNIT_REG ? "card_unit_reg_title" : "card_unit_delete_title";
                comps[0] = tr("card_dong_info", be.getCardDong());
                comps[1] = tr("card_ho_info", be.getCardHo());
                if (!be.isCardHoSet()) comps[2] = tr("card_check_unit");
                else comps[2] = tr(sc == Screen.CARD_UNIT_REG ? "card_touch_to_register" : "card_hash_to_delete");
                if (!be.getEditValue().isEmpty()) comps[3] = tr("card_typing", be.getEditValue());
            }
            case CARD_DELETE -> {
                title = "card_delete_title";
                comps[0] = tr("card_touch_please");
                comps[1] = tr("card_delete_desc");
            }
            default -> {
                title = "card_delete_all_title";
                comps[0] = tr("card_delete_all_desc", be.getCardCount());
            }
        }
        if (notice != null) comps[4] = msg("card_" + notice, be.getNoticeArg());
        text(g, tr(title), (PANEL_X1 + PANEL_X2) / 2f, y + (rh - 3.6f) / 2f, 0.4f, PANEL_TEXT, true);
        for (int i = 0; i < 6; i++) {
            if (comps[i] == null) continue;
            textFit(g, comps[i], PANEL_X1 + 3, y + (i + 1) * rh + (rh - 3.6f) / 2f, 0.4f, PANEL_X2 - PANEL_X1 - 6,
                    i == 4 ? 0xFF2A58C8 : PANEL_TEXT, false);
        }
        text(g, tr("menu_footer"), (PANEL_X1 + PANEL_X2) / 2f, y + 7 * rh + (rh - 3.6f) / 2f, 0.38f, PANEL_TEXT, true);
    }

    /** 도움말: 설명서 9쪽 "사용방법" */
    private void drawHelp(GuiGraphics g) {
        panel(g);
        fillF(g, PANEL_X1 + 1, PANEL_Y1 + 1, PANEL_X2 - 1, PANEL_Y1 + 9, 0xFF3A4C9C);
        text(g, tr("help_title"), PANEL_X1 + 4, PANEL_Y1 + 2.6f, 0.55f, 0xFFFFFFFF, false);
        String[] rows = {"help_unit", "help_guard", "help_cancel", "help_password"};
        float y = PANEL_Y1 + 12;
        for (String r : rows) {
            fillF(g, PANEL_X1 + 2, y, PANEL_X1 + 27, y + 7, 0xFF5868B4);
            textFit(g, tr(r), PANEL_X1 + 14.5f, y + 1.8f, 0.4f, 23, 0xFFFFFFFF, true);
            textFit(g, tr(r + "_how"), PANEL_X1 + 29, y + 1.8f, 0.4f, PANEL_X2 - PANEL_X1 - 31, PANEL_TEXT, false);
            y += 11;
        }
    }

    // ------------------------------------------------------------------ 키패드

    private Component[] bottomLabels(LobbyPhoneBlockEntity be) {
        Component cancel = tr("key.cancel");
        Component hash = keyDigit("#");
        Component star = keyDigit("*");
        Component la = lcd("←"), ra = lcd("→");
        return switch (be.getScreen()) {
            case IDLE -> new Component[]{tr("key.help"), be.isCommonPasswordUse() ? tr("key.common_pw") : hash};
            case INPUT -> be.isDongStage()
                    ? new Component[]{tr("key.dong"), tr("key.dong")}
                    : new Component[]{cancel, tr("key.password")};
            case PASSWORD, COMMON_PASSWORD, ADMIN_PASSWORD -> new Component[]{cancel, tr("key.ok")};
            case ADMIN_EDIT -> {
                LobbySettings.Item it = LobbySettings.item(be.getAdminPage(), be.getEditItem());
                yield it != null && it.kind() == LobbySettings.Kind.LOBBY_NO
                        ? new Component[]{tr("key.dong"), tr("key.line")}
                        : new Component[]{la, ra};
            }
            case ADMIN_MENU, CARD_MENU, CARD_UNIT_MENU, CARD_MASTER, CARD_REG, CARD_DELETE, CARD_DELETE_ALL, PROX_MENU ->
                    new Component[]{la, ra};
            case CARD_UNIT_REG, CARD_UNIT_DELETE -> new Component[]{star, hash};
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
            if (isPressed(slot)) fillF(g, kx, ky, kx + KEY_W, ky + KEY_H, 0x60C0D0FF);
            float cx = kx + KEY_W / 2f;
            float cy = ky + KEY_H / 2f;
            // 숫자/글자가 키 밖으로 절대 나가지 않도록 키 영역으로 잘라서 그림
            g.enableScissor(sx(kx), sy(ky), sx(kx + KEY_W), sy(ky + KEY_H));
            if (slot < 9 || slot == 10) {
                int d = slot < 9 ? layout[slot] : layout[9];
                Component c = keyDigit(String.valueOf(d));
                float w = Math.max(1, font.width(c) - 1);
                float scale = Math.min(ds, Math.min((KEY_W - 4) / w, (KEY_H - 3) / 8f));
                text(g, c, cx, cy - 4f * scale, scale, 0xFFFFFFFF, true);
            } else {
                Component label = bottom[slot == 9 ? 0 : 1];
                String raw = label.getString();
                if (raw.equals("*") || raw.equals("#")) {
                    text(g, label, cx, cy - 4f * 1.3f, 1.3f, 0xFFFFFFFF, true);
                } else {
                    String[] parts = raw.split("\n");
                    float ls = parts.length > 1 ? 0.5f : (raw.length() <= 1 ? 1.2f : 0.7f);
                    for (String p : parts) {
                        float pw = font.width(lcd(p)) * ls;
                        if (pw > KEY_W - 3) ls *= (KEY_W - 3) / pw;
                    }
                    float lineH = 9.5f * ls;
                    float y = cy - parts.length * lineH / 2f;
                    for (String p : parts) {
                        text(g, lcd(p), cx, y, ls, 0xFFFFFFFF, true);
                        y += lineH;
                    }
                }
            }
            g.disableScissor();
        }
    }

    private void drawRightKeys(GuiGraphics g, LobbyPhoneBlockEntity be, long gameTime, int mouseX, int mouseY) {
        float u = (mouseX - left) / s;
        float v = (mouseY - top) / s;
        for (int i = 0; i < 4; i++) {
            float ky = RIGHT_KEYS_Y[i];
            if (isPressed(12 + i)) {
                fillF(g, 151, ky - 8, 199, ky + 7, 0x60A0B8FF);
            } else if (u >= 150 && u <= 200 && v >= ky - 9 && v <= ky + 9) {
                fillF(g, 151, ky - 8, 199, ky + 7, 0x24FFFFFF);
            }
        }
        if (!be.isKeyLedOn(gameTime)) fillF(g, 151, 122, 199, 205, 0xB0000000);
    }

    private void drawChat(GuiGraphics g, LobbyPhoneBlockEntity be) {
        int devW = Math.round(DW * s), devH = Math.round(DH * s);
        int x = left + devW + 10;
        int y = top + 20;
        int w = 148;
        int bottom = top + devH - 66;
        g.fill(x - 4, y - 4, x + w + 4, bottom, 0xC0101830);
        g.drawString(font, Component.translatable("lobby." + HomeNet.MODID + "." + (be.isGuardCall() ? "talking_guard" : "talking_unit")),
                x, y, 0xFF6EE07A, false);
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

    // ------------------------------------------------------------------ 그리기 도우미 (기기 단위 좌표)

    private int sx(float u) {
        return Math.round(left + u * s);
    }

    private int sy(float v) {
        return Math.round(top + v * s);
    }

    /** 소수 좌표 사각형 (pose 를 10배 축소해서 그림) */
    private void fillF(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.scale(0.1f, 0.1f, 1);
        g.fill(Math.round(x1 * 10), Math.round(y1 * 10), Math.round(x2 * 10), Math.round(y2 * 10), color);
        pose.popPose();
    }

    private void gradF(GuiGraphics g, float x1, float y1, float x2, float y2, int c1, int c2) {
        var pose = g.pose();
        pose.pushPose();
        pose.scale(0.1f, 0.1f, 1);
        g.fillGradient(Math.round(x1 * 10), Math.round(y1 * 10), Math.round(x2 * 10), Math.round(y2 * 10), c1, c2);
        pose.popPose();
    }

    private void outline(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        float t = 0.3f;
        fillF(g, x1, y1, x2, y1 + t, color);
        fillF(g, x1, y2 - t, x2, y2, color);
        fillF(g, x1, y1, x1 + t, y2, color);
        fillF(g, x2 - t, y1, x2, y2, color);
    }

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
