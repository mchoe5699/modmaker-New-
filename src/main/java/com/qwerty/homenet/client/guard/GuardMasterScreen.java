package com.qwerty.homenet.client.guard;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.ReceiverBlockEntity.Action;
import com.qwerty.homenet.client.ReceiverScreen;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.network.WallpadActionPacket;
import com.qwerty.homenet.network.WallpadDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * KOCOM ASTRO KGP-70K 경비실기 화면 (사용설명서 화면 구성).
 * 본체 정면(송수화기 · 7인치 화면 · 기능 LED 버튼 · 10키 + 세대/경비/로비/문열림 · 조그 버튼)을 그리고
 * 화면은 400 x 225 좌표로 직접 그린다.
 *
 * 메인: 날씨·달력 / 방범 · 통화 · 조회 · 설정
 * 방범: 방범알림 · 방범목록보기 / 통화: 세대통화 · 경비실/관리실 · 공동현관 · 일반전화
 * 조회: 부재설정 · 우회등록 · 안전확인 · 유인택배 / 설정: 소리 · 비밀번호 · 터치보정 · 관리자모드
 */
public class GuardMasterScreen extends Screen implements ReceiverScreen {
    private static final ResourceLocation BODY = HomeNet.id("textures/gui/guard_master_body.png");
    private static final ResourceLocation HANDSET = HomeNet.id("textures/gui/guard_master_handset.png");
    private static final ResourceLocation BG = HomeNet.id("textures/gui/wallpad_bg.png");
    private static final ResourceLocation BG_SUB = HomeNet.id("textures/gui/wallpad_bg_sub.png");
    private static final ResourceLocation ICONS = HomeNet.id("textures/gui/wallpad_icons.png");
    private static final ResourceLocation CAMS = HomeNet.id("textures/gui/wallpad_cams.png");

    // 본체 좌표 (텍스처 1160 x 1020 의 절반)
    private static final int BW = 580, BH = 510;
    private static final int SX = 187, SY = 36, SW = 353, SH = 199;
    private static final int W = 400, H = 225;
    private static final float K = SW / (float) W;
    // 송수화기 그림 위치
    private static final int HX = 8, HY = 6, HW = 118, HH = 470;
    // 10키 (열 중심 x, 행 중심 y) – 4열 x 4행
    private static final int[] KEY_X = {213, 266, 319, 378};
    private static final int[] KEY_Y = {300, 336, 372, 408};
    private static final String[][] KEYS = {{"1", "2", "3", "세대"}, {"4", "5", "6", "경비"}, {"7", "8", "9", "로비"}, {"*", "0", "#", "문열림"}};
    // 조그 버튼
    private static final int JOG_X = 478, JOG_Y = 358, JOG_R = 58, JOG_IN = 20;
    // 기능 LED 버튼 (전화 · 부재 · 관리자 · 비상|전원)
    private static final int[] FN_X = {240, 290, 342, 404};
    private static final int FN_Y = 262;

    private static final int IC_SECURITY = 0, IC_CALL = 2, IC_INQUIRY = 3, IC_SETTINGS = 4, IC_SUN = 6, IC_CLOUD = 7,
            IC_RAIN = 8, IC_SNOW = 9, IC_WIFI = 10, IC_NOTICE = 12, IC_HOME = 14, IC_HELP = 15, IC_SHIELD = 16,
            IC_PHONE = 18, IC_MAGNIFIER = 19, IC_GEAR = 21, IC_SENSOR_DOOR = 27, IC_SENSOR_WINDOW = 28, IC_GAS = 29,
            IC_SPEAKER = 30, IC_LOCK = 32;
    private static final int CAM_DOOR = 0, CAM_LOBBY = 1, CAM_GUARD = 2, CAM_NEIGHBOR = 3;

    private static final int WHITE = 0xFFFFFFFF, DARK = 0xFF2A2F38, DIM = 0xFF6B7380, BLUE = 0xFF2E9BDB, RED = 0xFFE5483F,
            GREEN = 0xFF4CC36A, YELLOW = 0xFFFFD25A;

    private enum Page { MAIN, SECURITY, CALL, INQUIRY, SETTINGS, TOUCH }

    private static final Page[] MENU = {Page.SECURITY, Page.CALL, Page.INQUIRY, Page.SETTINGS};
    private static final int[] MENU_ICON = {IC_SHIELD, IC_PHONE, IC_MAGNIFIER, IC_GEAR};
    private static final String[] MENU_KEY = {"security", "call", "inquiry", "settings"};
    private static final String[] SEC_TABS = {"alarm", "list"};
    private static final String[] CALL_TABS = {"unit", "guard", "lobby", "phone"};
    private static final String[] INQ_TABS = {"absent", "bypass", "safety", "parcel"};
    private static final String[] SET_TABS = {"sound", "password", "touch", "admin"};

    private final BlockPos pos;
    private WallpadDataPacket data;

    private Page page = Page.MAIN;
    private final Map<Page, Integer> tabs = new LinkedHashMap<>();

    private GuiGraphics g;
    private float s;
    private int bx, by;
    private int mouseSX = -1, mouseSY = -1;
    private final List<Hot> hots = new ArrayList<>();
    private Consumer<String> keyInput;
    private Runnable confirmAction;

    private Popup popup;
    private Keypad keypad;

    private int calYear, calMonth;
    private String input = "";
    private int listPage;
    private long connectedSince;
    private int touchStep;
    private boolean pwUnlocked, adminUnlocked;
    private String pwAuth = "", pendingAuth = "";
    private String pw1 = "", pw2 = "";
    private int fieldFocus;
    private String adminAddr = "";
    private final Map<String, String> draft = new LinkedHashMap<>();
    private long keyFlashUntil;
    private String keyFlash = "";
    private float jogAngle;
    private int refreshTimer;

    private EditBox msgBox;
    private String msgDraft = "";

    private record Hot(int x, int y, int w, int h, Runnable action) {}

    private record Popup(List<String> lines, String ok, String cancel, Runnable onOk) {}

    private static final class Keypad {
        final String title;
        final boolean masked;
        final int maxLen;
        final Consumer<String> onOk;
        final Runnable onCancel;
        String value = "";

        Keypad(String title, boolean masked, int maxLen, Consumer<String> onOk, Runnable onCancel) {
            this.title = title;
            this.masked = masked;
            this.maxLen = maxLen;
            this.onOk = onOk;
            this.onCancel = onCancel;
        }
    }

    public GuardMasterScreen(WallpadDataPacket data) {
        super(Component.translatable("block.qwertys_homenet.guard_master"));
        this.pos = data.pos();
        this.data = data;
        LocalDate now = LocalDate.now();
        calYear = now.getYear();
        calMonth = now.getMonthValue();
        if (callState() != CallState.IDLE) {
            page = Page.CALL;
            tabs.put(Page.CALL, tabForKey(data.outgoing() ? data.peer() : key()));
            if (callState() == CallState.CONNECTED) connectedSince = System.currentTimeMillis();
        }
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void update(WallpadDataPacket p) {
        CallState before = callState();
        this.data = p;
        CallState now = callState();
        if (now == CallState.CONNECTED && before != CallState.CONNECTED) connectedSince = System.currentTimeMillis();
        if (now == CallState.RINGING && before != CallState.RINGING) {
            popup = null;
            keypad = null;
            page = Page.CALL;
            tabs.put(Page.CALL, tabForKey(key()));
        }
        if (now == CallState.CONNECTED && before == CallState.IDLE) {
            page = Page.CALL;
            tabs.put(Page.CALL, tabForKey(data.outgoing() ? data.peer() : key()));
        }
        if (!p.notice().isEmpty()) handleNotice(p.notice());
    }

    private static int tabForKey(String k) {
        if (k.startsWith("guard:") || k.startsWith("#guard") || k.equals("#office")) return 1;
        if (k.equals("lobby") || k.equals("front_door") || k.startsWith("#lobby")) return 2;
        return 0;
    }

    private CallState callState() {
        return CallState.byId(data.callState());
    }

    private String key() {
        return data.callerKey();
    }

    private boolean incoming() {
        return callState() != CallState.IDLE && !data.outgoing();
    }

    private void send(Action a) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, a));
    }

    private void send(Action a, String text) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, a.ordinal(), BlockPos.ZERO, text));
    }

    private static String t(String key, Object... args) {
        return I18n.get("gui." + HomeNet.MODID + ".gm." + key, args);
    }

    private static String wp(String key, Object... args) {
        return I18n.get("gui." + HomeNet.MODID + ".wp." + key, args);
    }

    private int tab(Page p) {
        return tabs.getOrDefault(p, 0);
    }

    private void go(Page p) {
        if (page != p) {
            page = p;
            input = "";
            listPage = 0;
            draft.clear();
            if (p == Page.SETTINGS) onSettingsTab(tab(Page.SETTINGS));
        }
    }

    private void setTab(Page p, int i) {
        tabs.put(p, i);
        input = "";
        listPage = 0;
        if (p == Page.SETTINGS) onSettingsTab(i);
    }

    // ------------------------------------------------------------------ 안내

    private void handleNotice(String n) {
        switch (n) {
            case "unlock_pw" -> {
                pwUnlocked = true;
                pwAuth = pendingAuth;
                keypad = null;
                return;
            }
            case "unlock_admin" -> {
                adminUnlocked = true;
                keypad = null;
                adminAddr = digits(data.unit());
                return;
            }
            case "pw_saved" -> {
                pwAuth = pw1;
                pw1 = "";
                pw2 = "";
            }
            case "unlock_fail" -> keypad = null;
            default -> {}
        }
        String k = "gui." + HomeNet.MODID + ".gm.notice." + n;
        info(I18n.exists(k) ? I18n.get(k) : wp("notice." + n));
    }

    private void info(String text) {
        popup = new Popup(Arrays.asList(text.split("\n")), wp("ok"), null, null);
    }

    private void confirm(String text, Runnable onOk) {
        popup = new Popup(Arrays.asList(text.split("\n")), wp("ok"), wp("cancel"), onOk);
    }

    // ------------------------------------------------------------------ 위젯

    @Override
    protected void init() {
        layout();
        msgBox = new EditBox(font, width / 2 - 110, Math.min(height - 22, by + Math.round(BH * s) + 4), 170, 16, Component.literal(""));
        msgBox.setMaxLength(IntercomLine.MAX_TEXT);
        msgBox.setValue(msgDraft);
        msgBox.setResponder(v -> msgDraft = v);
        msgBox.setHint(Component.literal(wp("message_hint")));
        addRenderableWidget(msgBox);
        addRenderableWidget(Button.builder(Component.literal(wp("send")), b -> sendMessage())
                .bounds(width / 2 + 64, msgBox.getY() - 2, 46, 20).build());
    }

    private void layout() {
        int availH = height - 34;
        s = Math.min((width - 16) / (float) BW, availH / (float) BH);
        s = Math.min(s, 1.5f);
        bx = Math.round((width - BW * s) / 2);
        by = Math.max(4, Math.round((availH - BH * s) / 2));
    }

    private void sendMessage() {
        String text = Intercom.sanitize(msgDraft, IntercomLine.MAX_TEXT);
        if (text.isEmpty() || callState() != CallState.CONNECTED) return;
        send(Action.SEND_MESSAGE, text);
        msgDraft = "";
        msgBox.setValue("");
    }

    // ------------------------------------------------------------------ 그리기 기본

    private void fill(int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    private void frame(int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void text(String str, float x, float y, int color, float scale) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1);
        g.drawString(font, str, 0, 0, color, false);
        ps.popPose();
    }

    private void shadowText(String str, float x, float y, int color, float scale) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1);
        g.drawString(font, str, 0, 0, color, true);
        ps.popPose();
    }

    private float width(String str, float scale) {
        return font.width(str) * scale;
    }

    private void textC(String str, float cx, float y, int color, float scale) {
        text(str, cx - width(str, scale) / 2, y, color, scale);
    }

    private void textFit(String str, float cx, float y, int color, float scale, float maxW) {
        float sc = scale;
        float w = width(str, sc);
        if (w > maxW) sc = sc * maxW / w;
        textC(str, cx, y + (scale - sc) * 4, color, sc);
    }

    private void icon(int idx, int x, int y, int size) {
        g.blit(ICONS, x, y, size, size, (idx % 8) * 128, (idx / 8) * 128, 128, 128, 1024, 1024);
    }

    private void icon(int idx, int x, int y, int size, int color) {
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        icon(idx, x, y, size);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void cam(int idx, int x, int y, int w, int h) {
        g.blit(CAMS, x, y, w, h, (idx % 2) * 512, (idx / 2) * 288, 480, 270, 1024, 1024);
    }

    private void hot(int x, int y, int w, int h, Runnable r) {
        hots.add(new Hot(x, y, w, h, r));
    }

    private boolean hover(int x, int y, int w, int h) {
        return mouseSX >= x && mouseSX < x + w && mouseSY >= y && mouseSY < y + h;
    }

    private void button(int x, int y, int w, int h, String label, boolean selected, boolean enabled, Runnable action) {
        boolean hv = enabled && hover(x, y, w, h);
        int top = selected ? 0xFF47B6F0 : enabled ? (hv ? 0xFFF2F4F7 : 0xFFE4E7EC) : 0xFFC9CDD3;
        int bottom = selected ? 0xFF1F86C9 : enabled ? (hv ? 0xFFC9CED6 : 0xFFB9BFC8) : 0xFFB3B8BF;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        frame(x, y, w, h, selected ? 0xFF16679C : 0xFF8C939D);
        fill(x + 1, y + 1, w - 2, 1, selected ? 0x55FFFFFF : 0x99FFFFFF);
        textFit(label, x + w / 2f, y + (h - 6) / 2f, selected ? WHITE : enabled ? DARK : 0xFF8E949C, 0.75f, w - 4);
        if (enabled) hot(x, y, w, h, action);
    }

    private void tabRow(String group, String[] keys, int selected, Consumer<Integer> onSelect) {
        int x = 79, y = 20, w = 317;
        fill(x, y, w, 16, 0xC0343A44);
        int tw = w / keys.length;
        for (int i = 0; i < keys.length; i++) {
            int tx = x + i * tw, idx = i;
            boolean sel = i == selected;
            if (sel) g.fillGradient(tx + 2, y + 2, tx + tw - 2, y + 14, 0xFF4CBDF5, 0xFF1F89CF);
            textFit(t(group + "." + keys[i]), tx + tw / 2f, y + 5, sel ? WHITE : 0xFFD7DCE3, 0.7f, tw - 6);
            hot(tx, y, tw, 16, () -> onSelect.accept(idx));
        }
    }

    private void panel(int x, int y, int w, int h) {
        fill(x, y, w, h, 0x8CF4F6F9);
        frame(x, y, w, h, 0xC0FFFFFF);
    }

    private void darkPanel(int x, int y, int w, int h) {
        fill(x, y, w, h, 0xB0283038);
        frame(x, y, w, h, 0xFF59626E);
    }

    private boolean blink() {
        return (System.currentTimeMillis() / 400) % 2 == 0;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String digits(String s) {
        return s.replaceAll("[^0-9]", "").replaceFirst("^0+(?=.)", "");
    }

    private static String when(long epoch) {
        return DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()));
    }

    // ------------------------------------------------------------------ 렌더

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.g = graphics;
        renderBackground(graphics);
        hots.clear();
        keyInput = null;
        confirmAction = null;
        layout();
        mouseSX = (int) Math.floor(((mouseX - bx) / s - SX) / K);
        mouseSY = (int) Math.floor(((mouseY - by) / s - SY) / K);
        int sxGui = bx + Math.round(SX * s), syGui = by + Math.round(SY * s);

        RenderSystem.enableBlend();
        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(bx, by, 0);
        ps.scale(s, s, 1);
        graphics.blit(BODY, 0, 0, BW, BH, 0, 0, BW * 2, BH * 2, BW * 2, BH * 2);
        drawHardware((mouseX - bx) / s, (mouseY - by) / s);

        ps.pushPose();
        ps.translate(SX, SY, 0);
        ps.scale(K, K, 1);
        graphics.enableScissor(sxGui, syGui, sxGui + Math.round(SW * s), syGui + Math.round(SH * s));
        drawScreen();
        graphics.disableScissor();
        ps.popPose();
        ps.popPose();

        boolean talking = callState() == CallState.CONNECTED;
        msgBox.visible = talking;
        for (var child : children()) if (child instanceof Button b) b.visible = talking;
        if (talking) graphics.drawString(font, wp("message_label"), msgBox.getX() - font.width(wp("message_label")) - 6, msgBox.getY() + 4, WHITE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 송수화기 / 키 눌림 / LED / 조그 */
    private void drawHardware(double mx, double my) {
        CallState cs = callState();
        boolean offHook = cs == CallState.CONNECTED || cs == CallState.DIALING;
        if (!offHook) {
            // 울릴 때 수화기가 살짝 떨림
            int dx = cs == CallState.RINGING && (System.currentTimeMillis() / 60) % 2 == 0 ? 1 : 0;
            g.blit(HANDSET, HX + dx, HY, HW, HH, 0, 0, HW * 2, HH * 2, HW * 2, HH * 2);
        }
        if (mx >= HX && mx < HX + HW && my >= HY && my < HY + HH) fill(HX, HY, HW, HH, 0x18FFFFFF);
        // 눌린 키 표시
        if (System.currentTimeMillis() < keyFlashUntil) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    if (KEYS[r][c].equals(keyFlash)) {
                        int w = c == 3 ? 50 : 40;
                        fill(KEY_X[c] - w / 2, KEY_Y[r] - 13, w, 26, 0x553DA5FF);
                    }
                }
            }
        }
        // 기능 버튼 LED
        int[] leds = new int[4];
        if (data.flag("absent")) leds[1] = 0xFFFF9A2E;
        if (adminUnlocked) leds[2] = 0xFF3DDC84;
        boolean alert = hasUnconfirmedAlert();
        leds[3] = alert && blink() ? 0xFFFF3B30 : 0xFF3DDC84;
        if (cs != CallState.IDLE && blink()) leds[0] = 0xFF3DA5FF;
        for (int i = 0; i < 4; i++) {
            if (leds[i] != 0) {
                fill(FN_X[i] - 9, FN_Y - 2, 18, 3, leds[i]);
                fill(FN_X[i] - 11, FN_Y - 4, 22, 7, (leds[i] & 0xFFFFFF) | 0x30000000);
            }
        }
        // 조그 표시 (돌린 방향)
        double a = Math.toRadians(jogAngle);
        int px = JOG_X + (int) Math.round(Math.cos(a) * (JOG_R - 9)), py = JOG_Y + (int) Math.round(Math.sin(a) * (JOG_R - 9));
        fill(px - 2, py - 2, 4, 4, 0xFF9AA4B0);
    }

    private boolean hasUnconfirmedAlert() {
        for (MissedCall m : data.records()) if (m.caller().startsWith("alert|") && m.caller().endsWith("|0")) return true;
        return false;
    }

    private void drawScreen() {
        switch (page) {
            case MAIN -> drawMain();
            case TOUCH -> drawTouch();
            default -> drawSub();
        }
        if (keypad != null) drawKeypad();
        if (popup != null) drawPopup();
    }

    // ------------------------------------------------------------------ 메인

    private void drawMain() {
        g.blit(BG, 0, 0, W, H, 0, 0, 800, 450, 800, 450);
        fill(0, 0, 132, H, 0x50102A55);
        var level = Minecraft.getInstance().level;
        int temp = 20;
        int wicon = IC_SUN;
        String weather = wp("weather.clear");
        if (level != null) {
            float base = level.getBiome(pos).value().getBaseTemperature();
            temp = Math.max(-15, Math.min(40, Math.round(base * 25f) - (level.isRaining() ? 3 : 0)));
            if (level.isThundering()) {
                wicon = IC_RAIN;
                weather = wp("weather.thunder");
            } else if (level.isRaining()) {
                wicon = base < 0.15f ? IC_SNOW : IC_RAIN;
                weather = base < 0.15f ? wp("weather.snow") : wp("weather.rain");
            } else if (level.getDayTime() % 24000 > 12500 && level.getDayTime() % 24000 < 23500) {
                wicon = IC_CLOUD;
                weather = wp("weather.clear_night");
            }
        }
        icon(wicon, 8, 8, 40);
        shadowText(temp + "℃", 54, 12, WHITE, 2.0f);
        shadowText(weather, 58, 34, WHITE, 0.75f);
        LocalDateTime now = LocalDateTime.now();
        shadowText(wp("wd." + (now.getDayOfWeek().getValue() % 7)) + " " + now.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
                + (now.getHour() < 12 ? " AM" : " PM"), 8, 56, WHITE, 0.72f);
        drawCalendar(4, 72);

        fill(0, 203, 132, 22, 0x70000000);
        icon(IC_WIFI, 6, 206, 16, digits(data.unit()).isEmpty() ? WHITE : GREEN);
        hot(4, 204, 20, 20, () -> info(t("status.addr", data.setting("guard_label", data.unit()))));
        icon(IC_SHIELD, 38, 206, 16, hasUnconfirmedAlert() && blink() ? RED : WHITE);
        hot(36, 204, 20, 20, () -> {
            go(Page.SECURITY);
            setTab(Page.SECURITY, 1);
        });
        icon(IC_LOCK, 70, 206, 16);
        hot(68, 204, 20, 20, () -> {
            go(Page.INQUIRY);
            setTab(Page.INQUIRY, 2);
        });
        icon(IC_NOTICE, 102, 206, 16);
        hot(100, 204, 20, 20, () -> {
            go(Page.INQUIRY);
            setTab(Page.INQUIRY, 3);
        });

        shadowText("HOMENET", 326, 8, WHITE, 1.0f);
        text("Guard Master", 340, 18, 0xFFE0ECF8, 0.55f);
        if (data.flag("absent")) {
            fill(150, 6, 120, 14, 0xC0FF9A2E);
            textC(t("absent_banner", data.setting("absent_fwd", "")), 210, 9, WHITE, 0.65f);
        }
        mainIcon(170, 56, IC_SECURITY, wp("menu.security"), Page.SECURITY);
        mainIcon(240, 56, IC_CALL, wp("menu.call"), Page.CALL);
        mainIcon(310, 56, IC_INQUIRY, wp("menu.inquiry"), Page.INQUIRY);
        mainIcon(170, 128, IC_SETTINGS, wp("menu.settings"), Page.SETTINGS);

        // 하단 안내줄 (설명서 사진의 "조그버튼을 좌우로 돌려 ..." 줄)
        fill(140, 192, 254, 14, 0xA0000000);
        String line = callState() != CallState.IDLE ? callStatusLine() : t("main_hint");
        textFit(line, 267, 195, callState() != CallState.IDLE ? YELLOW : WHITE, 0.65f, 248);
        if (callState() != CallState.IDLE) hot(140, 192, 254, 14, () -> go(Page.CALL));
    }

    private void mainIcon(int x, int y, int idx, String label, Page target) {
        if (hover(x, y, 44, 44)) fill(x - 2, y - 2, 48, 48, 0x40FFFFFF);
        icon(idx, x, y, 44);
        shadowText(label, x + 22 - width(label, 0.8f) / 2, y + 48, WHITE, 0.8f);
        hot(x - 4, y - 4, 52, 62, () -> go(target));
    }

    private void drawCalendar(int x, int y) {
        LocalDate first = LocalDate.of(calYear, calMonth, 1);
        LocalDate today = LocalDate.now();
        shadowText("<", x + 4, y + 2, WHITE, 0.9f);
        hot(x, y, 16, 12, () -> shiftMonth(-1));
        textC(wp("cal.header", calMonth, calYear), x + 62, y + 3, WHITE, 0.75f);
        shadowText(">", x + 116, y + 2, WHITE, 0.9f);
        hot(x + 110, y, 16, 12, () -> shiftMonth(1));
        fill(x, y + 13, 124, 10, 0x50FFC94D);
        String[] wd = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) textC(wd[i], x + 9 + i * 17.7f, y + 15, i == 6 ? 0xFFFF8A80 : WHITE, 0.6f);
        int offset = first.getDayOfWeek().getValue() - 1;
        for (int d = 1; d <= first.lengthOfMonth(); d++) {
            int cell = offset + d - 1;
            float cx = x + 9 + (cell % 7) * 17.7f;
            int cy = y + 27 + (cell / 7) * 17;
            boolean isToday = today.getYear() == calYear && today.getMonthValue() == calMonth && today.getDayOfMonth() == d;
            if (isToday) fill(Math.round(cx) - 7, cy - 3, 15, 13, 0x90FFFFFF);
            DayOfWeek dw = first.plusDays(d - 1).getDayOfWeek();
            textC(String.valueOf(d), cx, cy, isToday ? RED : dw == DayOfWeek.SUNDAY ? 0xFFFFB0A8 : WHITE, 0.62f);
        }
    }

    private void shiftMonth(int d) {
        LocalDate m = LocalDate.of(calYear, calMonth, 1).plusMonths(d);
        calYear = m.getYear();
        calMonth = m.getMonthValue();
    }

    private String peerDisplay(String p) {
        if (p.startsWith("#guard:")) return wp("guard_label_no", p.substring(7));
        if (p.startsWith("#lobby:")) return t("lobby_no", p.substring(7));
        return switch (p) {
            case "#guard" -> wp("guard_label");
            case "#office" -> wp("office_label");
            default -> p;
        };
    }

    private String peerName() {
        return data.outgoing() ? peerDisplay(data.peer()) : Intercom.sideName(key()).getString();
    }

    private String callStatusLine() {
        CallState cs = callState();
        if (data.outgoing()) return cs == CallState.DIALING ? wp("call.dialing", peerName()) : wp("call.talking_with", peerName(), talkTime());
        return cs == CallState.RINGING ? wp("call.incoming", peerName()) : wp("call.talking_with", peerName(), talkTime());
    }

    private String talkTime() {
        long sec = Math.max(0, (System.currentTimeMillis() - connectedSince) / 1000);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    // ------------------------------------------------------------------ 서브 화면 틀

    private void drawSub() {
        g.blit(BG_SUB, 0, 0, W, H, 0, 0, 800, 450, 800, 450);
        icon(IC_HOME, 4, 2, 14, hover(2, 0, 20, 18) ? 0xFF8FD3FF : WHITE);
        hot(0, 0, 24, 18, () -> go(Page.MAIN));
        icon(IC_HELP, W - 18, 2, 14, hover(W - 22, 0, 22, 18) ? 0xFF8FD3FF : WHITE);
        hot(W - 24, 0, 24, 18, () -> info(t("help." + page.name().toLowerCase(java.util.Locale.ROOT))));
        for (int i = 0; i < MENU.length; i++) {
            int y = 22 + i * 27;
            boolean sel = page == MENU[i];
            Page target = MENU[i];
            if (sel) g.fillGradient(3, y, 74, y + 22, 0xFF4CBDF5, 0xFF1F89CF);
            else fill(3, y, 71, 22, hover(3, y, 71, 22) ? 0xC0505A68 : 0xA0384049);
            frame(3, y, 71, 22, sel ? 0xFF8FD3FF : 0xFF6B7480);
            icon(MENU_ICON[i], 8, y + 4, 14, WHITE);
            text(wp("menu." + MENU_KEY[i]), 27, y + 8, WHITE, 0.75f);
            hot(3, y, 71, 22, () -> go(target));
        }
        frame(78, 19, 319, 204, 0xFF3FA7DD);
        switch (page) {
            case SECURITY -> drawSecurity();
            case CALL -> drawCall();
            case INQUIRY -> drawInquiry();
            case SETTINGS -> drawSettings();
            default -> {}
        }
    }

    private static final int CX = 80, CY = 38, CW = 315, CH = 183;

    // ------------------------------------------------------------------ 방범

    private List<int[]> alertIdx() {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        for (MissedCall m : data.records()) {
            if (m.caller().startsWith("alert|")) out.add(new int[]{data.records().indexOf(m), i++});
        }
        return out;
    }

    private void drawSecurity() {
        int tb = tab(Page.SECURITY);
        tabRow("sec", SEC_TABS, tb, i -> setTab(Page.SECURITY, i));
        List<MissedCall> alerts = new ArrayList<>();
        for (MissedCall m : data.records()) if (m.caller().startsWith("alert|")) alerts.add(m);
        if (tb == 0) {
            panel(CX + 4, CY + 4, 222, CH - 26);
            boolean emergency = alerts.stream().anyMatch(m -> m.caller().endsWith("|0"));
            secTile(CX + 14, CY + 14, IC_SENSOR_DOOR, t("sec.sensor1"), false);
            secTile(CX + 84, CY + 14, IC_SENSOR_WINDOW, t("sec.sensor234"), false);
            secTile(CX + 154, CY + 14, IC_GAS, t("sec.gas"), false);
            secTile(CX + 14, CY + 92, IC_SPEAKER, t("sec.emergency"), emergency && blink());
            if (emergency) {
                MissedCall last = alerts.stream().filter(m -> m.caller().endsWith("|0")).findFirst().orElse(alerts.get(0));
                String u = last.caller().split("\\|")[1];
                text(t("sec.from", u), CX + 84, CY + 104, RED, 0.75f);
                text(when(last.dayTime()), CX + 84, CY + 116, DARK, 0.6f);
            } else {
                text(t("sec.normal"), CX + 84, CY + 108, DIM, 0.7f);
            }
            int rx = CX + 232;
            darkPanel(rx - 4, CY + 4, 83, CH - 26);
            button(rx + 2, CY + 14, 71, 26, t("sec.stop"), false, emergency, () -> {
                for (int[] a : alertIdx()) {
                    String c = data.records().get(a[0]).caller();
                    if (c.endsWith("|0")) send(Action.CONFIRM_ALERT, String.valueOf(a[1]));
                }
            });
            // 아래 전화 줄: 비상 세대로 통화 연결
            int y = CY + CH - 20;
            fill(CX, y, CW, 18, 0xC0343A44);
            icon(IC_PHONE, CX + 6, y + 3, 12, emergency ? GREEN : 0xFF9AA4B0);
            text(t("sec.call_hint"), CX + 24, y + 6, WHITE, 0.6f);
            if (!alerts.isEmpty()) {
                String u = alerts.get(0).caller().split("\\|")[1];
                hot(CX, y, 160, 18, () -> send(Action.DIAL, u));
            }
        } else {
            drawRecordTable(new String[]{t("col.kind"), t("col.unit"), t("col.time"), t("col.check")}, new float[]{0.18f, 0.3f, 0.34f, 0.18f},
                    alerts, m -> {
                        String[] p = m.caller().split("\\|");
                        return new String[]{t("sec.emergency"), p.length > 1 ? p[1] : "", when(m.dayTime()),
                                p.length > 2 && p[2].equals("1") ? t("checked") : t("unchecked")};
                    }, i -> send(Action.CONFIRM_ALERT, String.valueOf(i)), "alert", false);
        }
    }

    private void secTile(int x, int y, int ic, String label, boolean on) {
        if (on) g.fillGradient(x, y, x + 60, y + 70, 0xFFFF7A6E, 0xFFD23A2E);
        else fill(x, y, 60, 70, 0xFFEDEFF2);
        frame(x, y, 60, 70, on ? 0xFF9E2A20 : 0xFF9AA1AB);
        icon(ic, x + 8, y + 4, 44);
        textFit(label, x + 30, y + 56, on ? WHITE : DARK, 0.65f, 56);
    }

    /** 목록 + 페이지 (< 01/05 >) + 전체삭제 */
    private void drawRecordTable(String[] header, float[] cols, List<MissedCall> rows, java.util.function.Function<MissedCall, String[]> fmt,
                                 Consumer<Integer> onRow, String clearKind, boolean withInput) {
        int x = CX + 4, y = CY + 4 + (withInput ? 20 : 0), w = withInput ? 222 : CW - 8;
        int perPage = withInput ? 5 : 7;
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        listPage = Math.max(0, Math.min(listPage, pages - 1));
        int h = perPage * 17 + 18;
        panel(x, y, w, h);
        fill(x + 3, y + 3, w - 6, 13, 0xFF59626E);
        float cx = x + 3;
        for (int i = 0; i < header.length; i++) {
            float cw = (w - 6) * cols[i];
            textFit(header[i], cx + cw / 2, y + 6, WHITE, 0.62f, cw - 4);
            cx += cw;
        }
        if (rows.isEmpty()) textC(t("empty"), x + w / 2f, y + 50, DIM, 0.7f);
        for (int r = 0; r < perPage; r++) {
            int idx = listPage * perPage + r;
            int ry = y + 17 + r * 17;
            fill(x + 3, ry + 16, w - 6, 1, 0x60808890);
            if (idx >= rows.size()) continue;
            String[] cells = fmt.apply(rows.get(idx));
            boolean unread = cells.length > 3 && cells[cells.length - 1].equals(t("unchecked"));
            if (hover(x + 3, ry, w - 6, 16)) fill(x + 3, ry, w - 6, 16, 0x403DA5FF);
            cx = x + 3;
            for (int i = 0; i < header.length && i < cells.length; i++) {
                float cw = (w - 6) * cols[i];
                textFit(cells[i], cx + cw / 2, ry + 5, unread ? 0xFF000000 : DARK, unread ? 0.66f : 0.6f, cw - 4);
                cx += cw;
            }
            hot(x + 3, ry, w - 6, 16, () -> onRow.accept(idx));
        }
        int py = y + h + 3;
        fill(x, py, w, 16, 0xC0343A44);
        button(x + w / 2 - 50, py + 2, 14, 12, "<", false, listPage > 0, () -> listPage--);
        textC(String.format("%02d/%02d", listPage + 1, pages), x + w / 2f, py + 5, WHITE, 0.6f);
        button(x + w / 2 + 36, py + 2, 14, 12, ">", false, listPage < pages - 1, () -> listPage++);
        if (clearKind != null) {
            button(x + w - 34, py + 1, 32, 14, t("clear_all"), false, !rows.isEmpty(),
                    () -> confirm(t("clear_confirm"), () -> send(Action.CLEAR_RECORDS, clearKind)));
        }
    }

    // ------------------------------------------------------------------ 통화

    private void inputField(String hint) {
        int x = CX + 4, y = CY + 4;
        fill(x, y, 222, 17, WHITE);
        frame(x, y, 222, 17, BLUE);
        text(input.isEmpty() ? hint : input + (blink() ? "_" : ""), x + 5, y + 5, input.isEmpty() ? 0xFF9AA1AB : DARK, 0.75f);
        // ⌫
        int bxx = x + 202;
        fill(bxx, y + 3, 16, 11, 0xFFE4E7EC);
        frame(bxx, y + 3, 16, 11, 0xFF8C939D);
        textC("×", bxx + 8, y + 5, DARK, 0.7f);
        hot(bxx, y, 20, 17, () -> input = input.isEmpty() ? "" : backspace(input));
    }

    private static String backspace(String s) {
        if (s.endsWith("동 ")) return s.substring(0, s.length() - 2);
        return s.substring(0, s.length() - 1);
    }

    private List<MissedCall> history(int type) {
        List<MissedCall> out = new ArrayList<>();
        for (MissedCall m : data.records()) {
            if (!m.caller().startsWith("hist|")) continue;
            String k = m.caller().substring(m.caller().indexOf('|', 5) + 1);
            if (tabForKey(k) == type && !(type == 0 && (k.startsWith("#") || k.equals("lobby")))) out.add(m);
        }
        return out;
    }

    private void drawCall() {
        int tb = tab(Page.CALL);
        tabRow("call", CALL_TABS, tb, i -> setTab(Page.CALL, i));
        CallState cs = callState();
        boolean mine = cs != CallState.IDLE && tabForKey(data.outgoing() ? data.peer() : key()) == tb && tb != 3;
        boolean ringing = mine && cs == CallState.RINGING;
        boolean talking = mine && cs == CallState.CONNECTED;
        boolean dialing = mine && cs == CallState.DIALING;
        if (talking || ringing) {
            int camIdx = tb == 0 ? CAM_NEIGHBOR : tb == 1 ? CAM_GUARD : key().equals("front_door") ? CAM_DOOR : CAM_LOBBY;
            fill(CX + 3, CY + 3, 224, CH - 24, 0xFF59626E);
            cam(camIdx, CX + 4, CY + 4, 222, CH - 26);
            fill(CX + 4, CY + 4, 222, 13, 0xA0000000);
            textFit(ringing ? wp("call.incoming", peerName()) : wp("call.talking_with", peerName(), talkTime()),
                    CX + 115, CY + 7, ringing ? YELLOW : 0xFF9CF0B0, 0.7f, 216);
            drawLog(CX + 6, CY + CH - 52);
        } else if (tb == 3) {
            inputField(t("call.phone_hint"));
            drawRecordTable(new String[]{t("col.number"), t("col.time")}, new float[]{0.5f, 0.5f}, List.of(), m -> new String[0], i -> {}, null, true);
        } else {
            String[] hints = {t("call.unit_hint"), t("call.guard_hint"), t("call.lobby_hint")};
            inputField(hints[tb]);
            List<MissedCall> hist = history(tb);
            String[] header = tb == 0 ? new String[]{"", t("col.dong"), t("col.ho"), t("col.time")} : new String[]{"", t("col.number"), t("col.time")};
            float[] cols = tb == 0 ? new float[]{0.1f, 0.2f, 0.2f, 0.5f} : new float[]{0.1f, 0.4f, 0.5f};
            drawRecordTable(header, cols, hist, m -> histCells(m, tb), i -> input = histInput(hist.get(i), tb), "hist", true);
            if (dialing) {
                fill(CX + 4, CY + 60, 222, 24, 0xD0000000);
                textFit(wp("call.dialing", peerName()), CX + 115, CY + 68, YELLOW, 0.8f, 214);
            }
        }
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 26);
        boolean idle = cs == CallState.IDLE;
        button(rx + 2, CY + 12, 71, 26, wp("call.talk"), talking || dialing || ringing && blink(), idle || ringing, () -> {
            if (ringing) send(Action.ANSWER);
            else confirmCall();
        });
        button(rx + 2, CY + 44, 71, 26, t("call.end"), false, cs != CallState.IDLE, () -> send(Action.HANG_UP));
        if (tb == 2) button(rx + 2, CY + 76, 71, 26, t("call.open"), false, talking, () -> send(Action.OPEN_DOOR));
        if (tb == 3) button(rx + 2, CY + 76, 71, 26, t("call.redial"), false, true, () -> info(wp("notice.no_line")));
        // 음량
        int y = CY + CH - 20;
        fill(CX, y, CW, 18, 0xC0343A44);
        icon(IC_SPEAKER, CX + 166, y + 3, 12, WHITE);
        int vol = parse(data.setting("vol_call", "8"), 8);
        slider(CX + 182, y + 3, vol, v -> send(Action.SET_SETTING, "vol_call=" + v));
        confirmAction = this::confirmCall;
        keyInput = c -> {
            if (c.equals("\b")) input = input.isEmpty() ? "" : backspace(input);
            else if (c.matches("[0-9*#]") && input.length() < 16) input += c;
        };
    }

    private String[] histCells(MissedCall m, int tb) {
        String[] p = m.caller().split("\\|", 3);
        String dir = p.length > 1 ? p[1] : "";
        String mark = switch (dir) {
            case "in" -> "↙";
            case "out" -> "↗";
            case "miss" -> "✕";
            default -> "→";
        };
        String k = p.length > 2 ? p[2] : "";
        String who = k.startsWith("#") ? peerDisplay(k) : k.startsWith("unit:") || k.startsWith("guard:") ? k.substring(k.indexOf(':') + 1)
                : Intercom.sideName(k).getString();
        if (tb == 0) {
            String[] dh = splitUnit(who);
            return new String[]{mark, dh[0], dh[1], when(m.dayTime())};
        }
        return new String[]{mark, who, when(m.dayTime())};
    }

    private String histInput(MissedCall m, int tb) {
        String[] p = m.caller().split("\\|", 3);
        String k = p.length > 2 ? p[2] : "";
        if (tb == 0) {
            String[] dh = splitUnit(k.startsWith("unit:") ? k.substring(5) : k);
            return dh[1].isEmpty() ? dh[0] : dh[0] + "동 " + dh[1] + "호";
        }
        return digits(k);
    }

    /** "101동 1203호", "101-1203" → [101, 1203] */
    private static String[] splitUnit(String u) {
        String[] parts = u.split("[^0-9]+");
        List<String> nums = new ArrayList<>();
        for (String x : parts) if (!x.isEmpty()) nums.add(x);
        if (nums.size() >= 2) return new String[]{nums.get(0), nums.get(1)};
        return new String[]{nums.isEmpty() ? u : nums.get(0), ""};
    }

    /** 조그 확인 / 통화 버튼: 세대는 "동 + 확인 + 호 + 확인", 나머지는 "번호 + 확인" */
    private void confirmCall() {
        if (callState() != CallState.IDLE) return;
        int tb = tab(Page.CALL);
        switch (tb) {
            case 0 -> {
                if (input.isEmpty()) return;
                if (!input.contains("동")) {
                    input = input + "동 ";
                    return;
                }
                String u = input.endsWith("호") ? input : input.trim() + "호";
                send(Action.DIAL, u);
                input = "";
            }
            case 1 -> {
                send(Action.CALL_GUARD, input.isEmpty() ? "guard" : "no:" + digits(input));
                input = "";
            }
            case 2 -> {
                if (digits(input).isEmpty()) {
                    info(t("notice.need_lobby"));
                    return;
                }
                send(Action.CALL_LOBBY, digits(input));
                input = "";
            }
            default -> info(wp("notice.no_line"));
        }
    }

    private void drawLog(int x, int y) {
        List<IntercomLine> log = data.log();
        int n = Math.min(3, log.size());
        for (int i = 0; i < n; i++) {
            IntercomLine l = log.get(log.size() - n + i);
            String line = l.name() + ": " + l.text();
            fill(x, y + i * 9 - 1, 218, 9, 0x90000000);
            text(font.plainSubstrByWidth(line, (int) (214 / 0.6f)), x + 2, y + i * 9, WHITE, 0.6f);
        }
    }

    private void slider(int x, int y, int value, Consumer<Integer> set) {
        fill(x, y, 128, 12, 0xFFF4F5F7);
        frame(x, y, 128, 12, 0xFF8C939D);
        textC("−", x + 6, y + 2, DARK, 0.8f);
        textC("+", x + 122, y + 2, DARK, 0.8f);
        hot(x, y, 12, 12, () -> set.accept(Math.max(1, value - 1)));
        hot(x + 116, y, 12, 12, () -> set.accept(Math.min(10, value + 1)));
        for (int i = 0; i < 10; i++) {
            int bxx = x + 16 + i * 10, v = i + 1;
            fill(bxx, y + 2, 7, 8, i < value ? 0xFF59626E : 0xFFD0D4DA);
            hot(bxx - 1, y, 10, 12, () -> set.accept(v));
        }
    }

    // ------------------------------------------------------------------ 조회

    private void drawInquiry() {
        int tb = tab(Page.INQUIRY);
        tabRow("inq", INQ_TABS, tb, i -> setTab(Page.INQUIRY, i));
        int rx = CX + 232;
        switch (tb) {
            case 0 -> {
                inputField(t("inq.fwd_hint"));
                panel(CX + 4, CY + 24, 222, CH - 30);
                boolean absent = data.flag("absent");
                String fwd = data.setting("absent_fwd", "");
                text(t("inq.absent_desc1"), CX + 10, CY + 32, DARK, 0.65f);
                text(t("inq.absent_desc2"), CX + 10, CY + 43, DIM, 0.6f);
                fill(CX + 10, CY + 62, 210, 40, absent ? 0xFFFFE3C2 : 0xFFE8EBEF);
                text(absent ? t("inq.absent_on") : t("inq.absent_off"), CX + 16, CY + 68, absent ? 0xFFB45A00 : DARK, 0.75f);
                if (absent) text(fwd.isEmpty() ? t("inq.absent_nofwd") : t("inq.fwd_to", wp("guard_label_no", fwd)), CX + 16, CY + 84, DARK, 0.65f);
                darkPanel(rx - 4, CY + 4, 83, CH - 8);
                button(rx + 2, CY + 12, 71, 26, t("inq.absent_set"), absent, true, () -> send(Action.ABSENT_SET, digits(input)));
                button(rx + 2, CY + 44, 71, 26, t("inq.absent_clear"), false, absent, () -> send(Action.ABSENT_CLEAR));
                numberInput();
            }
            case 1 -> {
                inputField(t("inq.fwd_hint"));
                panel(CX + 4, CY + 24, 222, CH - 30);
                String fwd = data.setting("busy_fwd", "");
                text(t("inq.bypass_desc1"), CX + 10, CY + 32, DARK, 0.65f);
                text(t("inq.bypass_desc2"), CX + 10, CY + 43, DIM, 0.6f);
                fill(CX + 10, CY + 62, 210, 40, fwd.isEmpty() ? 0xFFE8EBEF : 0xFFD5ECFF);
                text(fwd.isEmpty() ? t("inq.bypass_off") : t("inq.bypass_on", wp("guard_label_no", fwd)), CX + 16, CY + 74, DARK, 0.72f);
                darkPanel(rx - 4, CY + 4, 83, CH - 8);
                button(rx + 2, CY + 12, 71, 26, t("inq.bypass_set"), !fwd.isEmpty(), true, () -> send(Action.BUSY_FWD_SET, digits(input)));
                button(rx + 2, CY + 44, 71, 26, t("inq.bypass_clear"), false, !fwd.isEmpty(), () -> send(Action.BUSY_FWD_CLEAR));
                numberInput();
            }
            case 2 -> {
                drawRecordTable(new String[]{t("col.unread"), t("col.unit"), t("col.time"), t("col.check")}, new float[]{0.2f, 0.3f, 0.3f, 0.2f},
                        List.of(), m -> new String[0], i -> {}, null, false);
                text(t("inq.safety_note"), CX + 8, CY + CH - 12, 0xFF3A414C, 0.55f);
            }
            default -> {
                inputField(t("inq.parcel_hint"));
                List<MissedCall> all = new ArrayList<>();
                List<Integer> idx = new ArrayList<>();
                int i = 0;
                for (MissedCall m : data.records()) {
                    if (!m.caller().startsWith("parcel|")) continue;
                    String[] p = m.caller().split("\\|");
                    boolean keep = switch (parcelFilter) {
                        case 1 -> p.length > 1 && digits(p[1]).contains(digits(input)) && !digits(input).isEmpty();
                        case 2 -> p.length > 2 && p[2].equals("0");
                        default -> true;
                    };
                    if (keep) {
                        all.add(m);
                        idx.add(i);
                    }
                    i++;
                }
                drawRecordTable(new String[]{t("col.kind"), t("col.unit"), t("col.time")}, new float[]{0.25f, 0.35f, 0.4f}, all, m -> {
                    String[] p = m.caller().split("\\|");
                    return new String[]{p.length > 2 && p[2].equals("1") ? t("inq.parcel_done") : t("inq.parcel_wait"), p.length > 1 ? p[1] : "",
                            when(m.dayTime())};
                }, r -> send(Action.PARCEL_DONE, String.valueOf(idx.get(r))), "parcel", true);
                darkPanel(rx - 4, CY + 4, 83, CH - 8);
                button(rx + 2, CY + 12, 71, 26, t("inq.parcel_add"), false, true, () -> {
                    String[] dh = splitUnit(input);
                    if (dh[1].isEmpty()) {
                        info(t("notice.need_unit"));
                        return;
                    }
                    send(Action.PARCEL_ADD, dh[0] + "동 " + dh[1] + "호");
                    input = "";
                });
                button(rx + 2, CY + 44, 71, 26, t("inq.parcel_search"), parcelFilter == 1, true, () -> parcelFilter = parcelFilter == 1 ? 0 : 1);
                button(rx + 2, CY + 76, 71, 26, t("inq.parcel_unrecv"), parcelFilter == 2, true, () -> parcelFilter = parcelFilter == 2 ? 0 : 2);
                confirmAction = () -> {
                    if (!input.contains("동") && !input.isEmpty()) input += "동 ";
                };
                keyInput = c -> {
                    if (c.equals("\b")) input = input.isEmpty() ? "" : backspace(input);
                    else if (c.matches("[0-9]") && input.length() < 14) input += c;
                };
            }
        }
    }

    private int parcelFilter;

    private void numberInput() {
        keyInput = c -> {
            if (c.equals("\b")) input = input.isEmpty() ? "" : backspace(input);
            else if (c.matches("[0-9]") && input.length() < 2) input += c;
        };
    }

    // ------------------------------------------------------------------ 설정

    private void onSettingsTab(int idx) {
        draft.clear();
        fieldFocus = 0;
        switch (SET_TABS[idx]) {
            case "password" -> {
                if (!pwUnlocked) keypad = new Keypad(wp("settings.pw_title"), true, 8, v -> {
                    pendingAuth = v;
                    send(Action.UNLOCK, "pw:" + v);
                }, () -> tabs.put(Page.SETTINGS, 0));
            }
            case "admin" -> {
                if (!adminUnlocked) keypad = new Keypad(wp("settings.admin_title"), true, 8, v -> send(Action.UNLOCK, "admin:" + v),
                        () -> tabs.put(Page.SETTINGS, 0));
            }
            case "touch" -> {
                page = Page.TOUCH;
                touchStep = 0;
            }
            default -> {}
        }
    }

    private void drawSettings() {
        int tb = tab(Page.SETTINGS);
        tabRow("set", SET_TABS, tb, i -> setTab(Page.SETTINGS, i));
        switch (SET_TABS[tb]) {
            case "sound" -> {
                panel(CX + 4, CY + 4, CW - 8, CH - 8);
                int y = CY + 40;
                fill(CX + 10, y, CW - 20, 24, 0xFFE2E5EA);
                frame(CX + 10, y, CW - 20, 24, 0xFFB0B6BE);
                fill(CX + 14, y + 4, 64, 16, 0xFF59626E);
                textC(wp("settings.vol_system"), CX + 46, y + 8, WHITE, 0.65f);
                icon(IC_SPEAKER, CX + 96, y + 5, 14, DARK);
                String cur = draft.getOrDefault("vol_system", data.setting("vol_system", "5"));
                slider(CX + 116, y + 6, parse(cur, 5), v -> draft.put("vol_system", String.valueOf(v)));
                button(CX + CW - 120, CY + CH - 28, 52, 18, wp("settings.reset"), false, true, () -> {
                    draft.clear();
                    send(Action.RESET_SOUND);
                });
                button(CX + CW - 62, CY + CH - 28, 52, 18, wp("settings.save"), false, true, () -> {
                    draft.forEach((k, v) -> send(Action.SET_SETTING, k + "=" + v));
                    draft.clear();
                    info(wp("notice.saved"));
                });
            }
            case "password" -> drawPassword();
            case "admin" -> drawAdmin();
            default -> {
                panel(CX + 4, CY + 4, CW - 8, CH - 8);
                button(CX + CW / 2 - 50, CY + 70, 100, 24, wp("settings.touch_start"), false, true, () -> {
                    page = Page.TOUCH;
                    touchStep = 0;
                });
            }
        }
    }

    private void field(int x, int y, int w, String label, String value, boolean focused, Runnable onFocus) {
        fill(x, y, w, 30, 0xFF59626E);
        text(label, x + 4, y + 3, WHITE, 0.6f);
        fill(x + 3, y + 13, w - 6, 14, focused ? WHITE : 0xFFE8EBEF);
        frame(x + 3, y + 13, w - 6, 14, focused ? BLUE : 0xFF8C939D);
        text("*".repeat(value.length()) + (focused && blink() ? "_" : ""), x + 7, y + 17, DARK, 0.7f);
        hot(x, y, w, 30, onFocus);
    }

    private void sideKeypad(int x, int y, Consumer<String> onKey) {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < 12; i++) {
            String kk = keys[i];
            button(x + (i % 3) * 44, y + (i / 3) * 34, 40, 30, kk, false, true, () -> onKey.accept(kk));
        }
        keyInput = onKey;
    }

    private void drawPassword() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!pwUnlocked) {
            textC(wp("settings.pw_locked"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            button(CX + CW / 2 - 40, CY + 90, 80, 20, wp("settings.pw_enter"), false, true, () -> onSettingsTab(1));
            return;
        }
        field(CX + 12, CY + 14, 120, wp("settings.pw_new"), pw1, fieldFocus == 0, () -> fieldFocus = 0);
        field(CX + 12, CY + 52, 120, wp("settings.pw_confirm"), pw2, fieldFocus == 1, () -> fieldFocus = 1);
        sideKeypad(CX + 168, CY + 10, k -> {
            if (k.equals("\b")) {
                if (fieldFocus == 0 && !pw1.isEmpty()) pw1 = pw1.substring(0, pw1.length() - 1);
                if (fieldFocus == 1 && !pw2.isEmpty()) pw2 = pw2.substring(0, pw2.length() - 1);
                return;
            }
            if (!k.matches("\\d")) return;
            if (fieldFocus == 0 && pw1.length() < 4) {
                pw1 += k;
                if (pw1.length() == 4) fieldFocus = 1;
            } else if (fieldFocus == 1 && pw2.length() < 4) pw2 += k;
        });
        button(CX + 12, CY + 92, 56, 20, wp("settings.save"), false, true, () -> {
            if (pw1.length() != 4 || !pw1.equals(pw2)) {
                info(wp("notice.pw_mismatch"));
                return;
            }
            send(Action.SET_DOOR_PASSWORD, pwAuth + "|" + pw1);
        });
        button(CX + 76, CY + 92, 56, 20, wp("settings.edit"), false, true, () -> {
            pw1 = "";
            pw2 = "";
            fieldFocus = 0;
        });
        text(t("set.pw_note1"), CX + 12, CY + 124, 0xFF3A414C, 0.58f);
        text(t("set.pw_note2"), CX + 12, CY + 134, 0xFF3A414C, 0.58f);
    }

    private void drawAdmin() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!adminUnlocked) {
            textC(wp("settings.admin_locked"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            button(CX + CW / 2 - 40, CY + 90, 80, 20, wp("settings.pw_enter"), false, true, () -> onSettingsTab(3));
            return;
        }
        fill(CX + 10, CY + 10, 150, 14, 0xFF59626E);
        text(t("set.addr_title"), CX + 14, CY + 14, WHITE, 0.65f);
        text(t("set.addr_label"), CX + 12, CY + 32, DARK, 0.65f);
        fill(CX + 70, CY + 28, 60, 16, WHITE);
        frame(CX + 70, CY + 28, 60, 16, BLUE);
        text(adminAddr + (blink() ? "_" : ""), CX + 75, CY + 32, DARK, 0.8f);
        text(t("set.addr_current", data.setting("guard_label", data.unit())), CX + 12, CY + 50, DIM, 0.6f);
        text(t("set.addr_note"), CX + 12, CY + 62, 0xFF3A414C, 0.55f);
        button(CX + 12, CY + 76, 50, 18, wp("settings.save"), false, true, () -> send(Action.SET_UNIT, adminAddr));
        button(CX + 68, CY + 76, 50, 18, wp("settings.edit"), false, true, () -> adminAddr = "");
        fill(CX + 10, CY + 104, 150, 14, 0xFF59626E);
        text(t("set.ip_title"), CX + 14, CY + 108, WHITE, 0.65f);
        int a = Math.floorMod(pos.hashCode(), 200) + 20;
        text("IP Address : 10.0.0." + a, CX + 12, CY + 124, DIM, 0.6f);
        text("Subnet Mask : 255.255.255.0", CX + 12, CY + 134, DIM, 0.6f);
        text("Gateway : 10.0.0.1   ·   Server : 10.0.0.2", CX + 12, CY + 144, DIM, 0.6f);
        text("ASTRO KGP-70K  S/W 1.5.08", CX + 12, CY + 156, DIM, 0.55f);
        sideKeypad(CX + 168, CY + 10, k -> {
            if (k.equals("\b")) adminAddr = adminAddr.isEmpty() ? "" : adminAddr.substring(0, adminAddr.length() - 1);
            else if (k.matches("\\d") && adminAddr.length() < 2) adminAddr += k;
        });
    }

    // ------------------------------------------------------------------ 터치보정

    private static final int[][] TOUCH_POINTS = {{30, 30}, {370, 30}, {370, 195}, {30, 195}, {200, 112}};

    private void drawTouch() {
        fill(0, 0, W, H, WHITE);
        textC(wp("touch.line1"), W / 2f, 8, DARK, 0.65f);
        textC(wp("touch.line2"), W / 2f, 18, DARK, 0.65f);
        int[] p = TOUCH_POINTS[Math.min(touchStep, 4)];
        fill(p[0] - 10, p[1], 21, 1, DARK);
        fill(p[0], p[1] - 10, 1, 21, DARK);
        hot(p[0] - 12, p[1] - 12, 25, 25, () -> {
            touchStep++;
            if (touchStep >= TOUCH_POINTS.length) {
                page = Page.SETTINGS;
                tabs.put(Page.SETTINGS, 0);
                info(wp("notice.touch_done"));
            }
        });
    }

    // ------------------------------------------------------------------ 팝업

    private void drawPopup() {
        hots.clear();
        fill(0, 0, W, H, 0x80000000);
        int w = 230, lines = popup.lines.size(), h = 46 + lines * 11;
        int x = (W - w) / 2, y = (H - h) / 2;
        fill(x, y, w, h, 0xFFF4F5F7);
        frame(x, y, w, h, 0xFF59626E);
        fill(x, y, w, 3, BLUE);
        icon(IC_HELP, x + w / 2 - 7, y + 6, 14, BLUE);
        for (int i = 0; i < lines; i++) textFit(popup.lines.get(i), x + w / 2f, y + 24 + i * 11, DARK, 0.75f, w - 12);
        Popup p = popup;
        Runnable ok = () -> {
            popup = null;
            if (p.onOk != null) p.onOk.run();
        };
        if (p.cancel == null) {
            button(x + w / 2 - 30, y + h - 20, 60, 15, p.ok, false, true, ok);
        } else {
            button(x + w / 2 - 64, y + h - 20, 60, 15, p.ok, false, true, ok);
            button(x + w / 2 + 4, y + h - 20, 60, 15, p.cancel, false, true, () -> popup = null);
        }
        keyInput = null;
        confirmAction = ok;
    }

    private void drawKeypad() {
        hots.clear();
        fill(0, 0, W, H, 0x80000000);
        Keypad kp = keypad;
        int x = 130, y = 14, w = 140, h = 198;
        fill(x, y, w, h, 0xFF4A515C);
        frame(x, y, w, h, 0xFF1E2329);
        textC(kp.title, x + w / 2f, y + 6, WHITE, 0.8f);
        fill(x + 10, y + 18, w - 20, 16, WHITE);
        textC(kp.masked ? "*".repeat(kp.value.length()) : kp.value, x + w / 2f, y + 23, DARK, 0.9f);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "#", "0", "*"};
        for (int i = 0; i < 12; i++) {
            String d = keys[i];
            button(x + 10 + (i % 3) * 41, y + 40 + (i / 3) * 34, 38, 30, d, false, true, () -> {
                if (kp.value.length() < kp.maxLen) kp.value += d;
            });
        }
        Runnable ok = () -> {
            String v = kp.value;
            kp.value = "";
            kp.onOk.accept(v);
        };
        button(x + 10, y + h - 22, 58, 16, wp("ok"), false, true, ok);
        button(x + w - 68, y + h - 22, 58, 16, wp("cancel"), false, true, () -> {
            keypad = null;
            if (kp.onCancel != null) kp.onCancel.run();
        });
        keyInput = c -> {
            if (c.equals("\b")) {
                if (!kp.value.isEmpty()) kp.value = kp.value.substring(0, kp.value.length() - 1);
            } else if (kp.value.length() < kp.maxLen && c.matches("\\d")) kp.value += c;
        };
        confirmAction = ok;
    }

    // ------------------------------------------------------------------ 본체 키

    private void hardKey(String k) {
        keyFlash = k;
        keyFlashUntil = System.currentTimeMillis() + 150;
        switch (k) {
            case "세대" -> {
                popup = null;
                keypad = null;
                go(Page.CALL);
                setTab(Page.CALL, 0);
            }
            case "경비" -> {
                popup = null;
                keypad = null;
                go(Page.CALL);
                setTab(Page.CALL, 1);
            }
            case "로비" -> {
                popup = null;
                keypad = null;
                go(Page.CALL);
                setTab(Page.CALL, 2);
            }
            case "문열림" -> send(Action.GUARD_DOOR);
            case "*" -> {
                // * = 입력 취소
                if (keyInput != null) keyInput.accept("\b");
            }
            default -> {
                if (keyInput != null) keyInput.accept(k);
            }
        }
    }

    private void fnKey(int i) {
        popup = null;
        keypad = null;
        switch (i) {
            case 0 -> {
                go(Page.CALL);
                setTab(Page.CALL, 3);
            }
            case 1 -> {
                go(Page.INQUIRY);
                setTab(Page.INQUIRY, 0);
            }
            case 2 -> {
                go(Page.SETTINGS);
                setTab(Page.SETTINGS, 3);
            }
            default -> {
                // 비상 정지: 확인 안 한 비상을 모두 확인 처리 (경보음 정지)
                for (int[] a : alertIdx()) {
                    if (data.records().get(a[0]).caller().endsWith("|0")) send(Action.CONFIRM_ALERT, String.valueOf(a[1]));
                }
            }
        }
    }

    /** 조그: 돌리면 목록 페이지 / 메뉴 이동, 누르면 확인 */
    private void jog(int dir) {
        jogAngle += dir * 30;
        if (page == Page.MAIN) {
            Page[] order = {Page.SECURITY, Page.CALL, Page.INQUIRY, Page.SETTINGS};
            go(order[Math.floorMod(dir > 0 ? 0 : 3, 4)]);
            return;
        }
        listPage = Math.max(0, listPage + dir);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        double ux = (mx - bx) / s, uy = (my - by) / s;
        // 송수화기
        if (ux >= HX && ux < HX + HW && uy >= HY && uy < HY + HH) {
            click();
            if (callState() != CallState.IDLE) send(Action.PICKUP);
            else {
                go(Page.CALL);
            }
            return true;
        }
        // 10키
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int w = c == 3 ? 50 : 40;
                if (ux >= KEY_X[c] - w / 2.0 && ux <= KEY_X[c] + w / 2.0 && uy >= KEY_Y[r] - 13 && uy <= KEY_Y[r] + 13) {
                    click();
                    hardKey(KEYS[r][c]);
                    return true;
                }
            }
        }
        // 조그
        double dx = ux - JOG_X, dy = uy - JOG_Y, d = Math.sqrt(dx * dx + dy * dy);
        if (d <= JOG_R) {
            click();
            if (d <= JOG_IN + 4) {
                if (confirmAction != null) confirmAction.run();
            } else {
                jog(dx >= 0 ? 1 : -1);
            }
            return true;
        }
        // 기능 버튼
        for (int i = 0; i < 4; i++) {
            if (Math.abs(ux - FN_X[i]) <= 14 && uy >= FN_Y - 8 && uy <= FN_Y + 14) {
                click();
                fnKey(i);
                return true;
            }
        }
        double sx = (ux - SX) / K, sy = (uy - SY) / K;
        if (sx < 0 || sy < 0 || sx >= W || sy >= H) return false;
        for (int i = hots.size() - 1; i >= 0; i--) {
            Hot h = hots.get(i);
            if (sx >= h.x && sx < h.x + h.w && sy >= h.y && sy < h.y + h.h) {
                click();
                h.action.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        double ux = (mx - bx) / s, uy = (my - by) / s;
        double dx = ux - JOG_X, dy = uy - JOG_Y;
        if (dx * dx + dy * dy <= JOG_R * JOG_R * 1.5) {
            jog(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void click() {
        float vol = parse(data.setting("vol_system", "5"), 5) / 10f;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, vol * 0.6f));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (msgBox != null && msgBox.isFocused() && msgBox.visible) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                sendMessage();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (popup != null || keypad != null)) {
            if (keypad != null && keypad.onCancel != null) keypad.onCancel.run();
            popup = null;
            keypad = null;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && confirmAction != null) {
            confirmAction.run();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && keyInput != null) {
            keyInput.accept("\b");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (msgBox != null && msgBox.isFocused() && msgBox.visible) return super.charTyped(c, mods);
        if (keyInput != null && (Character.isDigit(c) || c == '*' || c == '#')) {
            keyInput.accept(String.valueOf(c));
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void tick() {
        super.tick();
        msgBox.tick();
        if (++refreshTimer >= 40) {
            refreshTimer = 0;
            send(Action.REFRESH);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
