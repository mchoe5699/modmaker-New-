package com.qwerty.homenet.client.wallpad;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.blockentity.ReceiverBlockEntity.Action;
import com.qwerty.homenet.client.ReceiverScreen;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.network.DeviceEntry;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.network.WallpadActionPacket;
import com.qwerty.homenet.network.WallpadDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
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
 * 월패드 화면 (KOCOM KHN-893N 사용설명서 화면 구성).
 * 본체 그림 위에 화면을 그리고, 화면은 400 x 225 단위 좌표로 직접 그린다 (터치 영역도 같은 좌표).
 *
 * 메인: 날씨·시계·달력 / 상태표시(네트워크·재실·공지·엘리베이터) / 방범·제어·통화·조회·설정·에너지
 * 서브: 홈 · 도움말 / 왼쪽 메뉴 / 탭
 * 본체 버튼: 비상 · 외출 · 경비 · 통화 · 문열림
 */
public class KhnWallpadScreen extends Screen implements ReceiverScreen {
    private static final ResourceLocation BODY = HomeNet.id("textures/gui/wallpad_body.png");
    private static final ResourceLocation BG = HomeNet.id("textures/gui/wallpad_bg.png");
    private static final ResourceLocation BG_SUB = HomeNet.id("textures/gui/wallpad_bg_sub.png");
    private static final ResourceLocation ICONS = HomeNet.id("textures/gui/wallpad_icons.png");
    private static final ResourceLocation CAMS = HomeNet.id("textures/gui/wallpad_cams.png");

    // 본체 좌표 (텍스처 1410 x 950 의 절반)
    private static final int BW = 705, BH = 475;
    private static final int SX = 104, SY = 99, SW = 494, SH = 278;
    /** 화면 좌표계 */
    private static final int W = 400, H = 225;
    private static final float K = SW / (float) W;
    private static final int[] BUTTON_Y = {115, 177, 239, 300, 361};
    private static final int BUTTON_X1 = 600, BUTTON_X2 = 646;

    // 아이콘 번호 (wallpad_icons.png 128칸)
    private static final int IC_SECURITY = 0, IC_CONTROL = 1, IC_CALL = 2, IC_INQUIRY = 3, IC_SETTINGS = 4, IC_ENERGY = 5,
            IC_SUN = 6, IC_CLOUD = 7, IC_RAIN = 8, IC_SNOW = 9, IC_WIFI = 10, IC_PERSON = 11, IC_NOTICE = 12, IC_ELEVATOR = 13,
            IC_HOME = 14, IC_HELP = 15, IC_SHIELD = 16, IC_WRENCH = 17, IC_PHONE = 18, IC_MAGNIFIER = 19, IC_GAUGE = 20,
            IC_GEAR = 21, IC_BULB_ON = 22, IC_BULB_OFF = 23, IC_FAN = 24, IC_HEATER = 25, IC_AC = 26, IC_SENSOR_DOOR = 27,
            IC_SENSOR_WINDOW = 28, IC_GAS = 29, IC_SPEAKER = 30, IC_BRIGHT = 31, IC_LOCK = 32;
    // 카메라 화면 번호 (wallpad_cams.png)
    private static final int CAM_DOOR = 0, CAM_LOBBY = 1, CAM_GUARD = 2, CAM_NEIGHBOR = 3, CAM_ELEVATOR = 4, CAM_CCTV = 5, CAM_NONE = 6;

    // 색
    private static final int WHITE = 0xFFFFFFFF, DARK = 0xFF2A2F38, DIM = 0xFF6B7380, BLUE = 0xFF2E9BDB, RED = 0xFFE5483F,
            GREEN = 0xFF4CC36A, YELLOW = 0xFFFFD25A;

    private enum Page { MAIN, SECURITY, CONTROL, CALL, INQUIRY, ENERGY, SETTINGS, TOUCH }

    private static final Page[] MENU = {Page.SECURITY, Page.CONTROL, Page.CALL, Page.INQUIRY, Page.ENERGY, Page.SETTINGS};
    private static final int[] MENU_ICON = {IC_SHIELD, IC_WRENCH, IC_PHONE, IC_MAGNIFIER, IC_GAUGE, IC_GEAR};
    private static final String[] MENU_KEY = {"security", "control", "call", "inquiry", "energy", "settings"};

    private static final String[] CONTROL_TABS = {"gas", "light", "heating", "vent", "aircon", "other"};
    private static final String[] CALL_TABS = {"front", "household", "guard", "phone"};
    private static final String[] INQUIRY_TABS = {"notice", "visitor", "repair", "vote", "fee", "elevator", "cctv", "parcel", "memo", "parking"};
    private static final String[] ENERGY_TABS = {"realtime", "average", "meter"};
    private static final String[] SETTING_TABS = {"sound", "password", "sms", "ars", "touch", "admin"};
    private static final String[] ENERGY_KEYS = {"elec", "water", "gas", "hotwater", "heat"};
    private static final String[] ENERGY_UNITS = {"kWh", "m³", "m³", "m³", "kWh"};

    private final BlockPos pos;
    private WallpadDataPacket data;
    private long[] lastEnergy = new long[0];
    private final boolean[] energyUp = new boolean[5];

    private Page page = Page.MAIN;
    private final Map<Page, Integer> tabs = new LinkedHashMap<>();
    private int inquiryScroll, settingScroll;

    // 그리기 상태
    private GuiGraphics g;
    private float s;
    private int bx, by;
    private int mouseSX = -1, mouseSY = -1;
    private final List<Hot> hots = new ArrayList<>();
    private Consumer<String> keyInput;

    // 팝업
    private Popup popup;
    private Keypad keypad;

    // 화면별 상태
    private int calYear, calMonth;
    private String selectedRoom = "";
    private int selectedHeat, selectedVent, selectedAc, selectedGas;
    private int roomPage;
    private String unitInput = "";
    private String phoneInput = "";
    private boolean videoCall;
    private boolean videoBlocked = true;
    private boolean guardOffice;
    private long monitorUntil;
    private long connectedSince;
    private int brightness = 5;
    private int visitorIndex;
    private long confirmDeleteUntil, confirmClearUntil;
    private int cctvCam;
    private int energySel;
    private int repairSel = -1;
    // 설정 (저장 전 임시값)
    private final Map<String, String> draft = new LinkedHashMap<>();
    private boolean pwUnlocked, adminUnlocked;
    private String pwAuth = "", pendingAuth = "";
    private String pw1 = "", pw2 = "";
    private int fieldFocus;
    private String adminUnit = "";
    private int touchStep;
    private int refreshTimer;

    private EditBox msgBox;
    private String msgDraft = "";

    private record Hot(int x, int y, int w, int h, Runnable action) {}

    private record Popup(List<String> lines, String ok, String cancel, Runnable onOk, Runnable onCancel) {}

    private static final class Keypad {
        final String title;
        final boolean masked;
        final int maxLen;
        final Consumer<String> onOk;
        final Runnable onCancel;
        final boolean energyStyle;
        String value = "";

        Keypad(String title, boolean masked, int maxLen, boolean energyStyle, Consumer<String> onOk, Runnable onCancel) {
            this.title = title;
            this.masked = masked;
            this.maxLen = maxLen;
            this.energyStyle = energyStyle;
            this.onOk = onOk;
            this.onCancel = onCancel;
        }
    }

    public KhnWallpadScreen(WallpadDataPacket data) {
        super(Component.translatable("block.qwertys_homenet.wallpad"));
        this.pos = data.pos();
        this.data = data;
        LocalDate now = LocalDate.now();
        calYear = now.getYear();
        calMonth = now.getMonthValue();
        lastEnergy = data.energy().clone();
        autoNavigate(CallState.IDLE);
        if (callState() != CallState.IDLE) {
            page = Page.CALL;
            String k = data.outgoing() ? (isGuardPeer() ? "guard:" : "unit:") : key();
            tabs.put(Page.CALL, k.startsWith("guard:") ? 2 : k.startsWith("unit:") ? 1 : 0);
        }
        if (callState() == CallState.CONNECTED) connectedSince = System.currentTimeMillis();
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void update(WallpadDataPacket p) {
        CallState before = callState();
        long[] old = data.energy();
        this.data = p;
        for (int i = 0; i < 5 && i < p.energy().length && i < old.length; i++) {
            if (p.energy()[i] > old[i]) energyUp[i] = true;
        }
        if (callState() == CallState.CONNECTED && before != CallState.CONNECTED) connectedSince = System.currentTimeMillis();
        autoNavigate(before);
        if (!p.notice().isEmpty()) handleNotice(p.notice());
    }

    private CallState callState() {
        return CallState.byId(data.callState());
    }

    private boolean incoming() {
        return callState() != CallState.IDLE && !data.outgoing();
    }

    private String key() {
        return data.callerKey();
    }

    /** 호출이 오면 해당 통화 화면으로 */
    private void autoNavigate(CallState before) {
        CallState now = callState();
        if (now == CallState.RINGING && before != CallState.RINGING) {
            popup = null;
            keypad = null;
            page = Page.CALL;
            String k = key();
            tabs.put(Page.CALL, k.startsWith("guard:") ? 2 : k.startsWith("unit:") ? 1 : 0);
            videoCall = false;
            videoBlocked = true;
        }
    }

    private void send(Action a) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, a));
    }

    private void send(Action a, String text) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, a.ordinal(), BlockPos.ZERO, text));
    }

    private void send(Action a, BlockPos target, String text) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, a.ordinal(), target, text));
    }

    private static String t(String key, Object... args) {
        return I18n.get("gui." + HomeNet.MODID + ".wp." + key, args);
    }

    private int tab(Page p) {
        return tabs.getOrDefault(p, 0);
    }

    private void go(Page p) {
        if (page != p) {
            page = p;
            draft.clear();
            fieldFocus = 0;
            if (p == Page.SETTINGS) onSettingsTab(tab(Page.SETTINGS));
        }
    }

    // ------------------------------------------------------------------ 안내 팝업

    private void handleNotice(String n) {
        switch (n) {
            case "unlock_pw" -> {
                pwUnlocked = true;
                pwAuth = pendingAuth;
                keypad = null;
                pw1 = "";
                pw2 = "";
                fieldFocus = 0;
                return;
            }
            case "unlock_admin" -> {
                adminUnlocked = true;
                keypad = null;
                adminUnit = data.unit();
                return;
            }
            case "pw_saved" -> {
                pwAuth = pw1;
                pw1 = "";
                pw2 = "";
            }
            default -> {}
        }
        if (n.startsWith("energy_over:")) {
            int i = parse(n.substring(12), 0);
            info(t("notice.energy_over", t("energy." + ENERGY_KEYS[Math.max(0, Math.min(4, i))])));
            return;
        }
        if (n.equals("unlock_fail")) keypad = null;
        info(t("notice." + n));
    }

    private void info(String text) {
        popup = new Popup(Arrays.asList(text.split("\n")), t("ok"), null, null, null);
    }

    private void confirm(String text, Runnable onOk) {
        popup = new Popup(Arrays.asList(text.split("\n")), t("ok"), t("cancel"), onOk, null);
    }

    // ------------------------------------------------------------------ 위젯 (메시지 입력)

    @Override
    protected void init() {
        layout();
        msgBox = new EditBox(font, width / 2 - 110, Math.min(height - 22, by + Math.round(BH * s) + 4), 170, 16, Component.literal(""));
        msgBox.setMaxLength(IntercomLine.MAX_TEXT);
        msgBox.setValue(msgDraft);
        msgBox.setResponder(v -> msgDraft = v);
        msgBox.setHint(Component.literal(t("message_hint")));
        addRenderableWidget(msgBox);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(t("send")), b -> sendMessage())
                .bounds(width / 2 + 64, msgBox.getY() - 2, 46, 20).build());
    }

    private void layout() {
        int availH = height - 34;
        s = Math.min((width - 16) / (float) BW, availH / (float) BH);
        s = Math.min(s, 1.6f);
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

    /** 폭에 맞게 줄여서 가운데 정렬 */
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
        // 밝기 조절
        if (brightness < 5) fill(x, y, w, h, ((5 - brightness) * 30) << 24);
        else if (brightness > 5) fill(x, y, w, h, ((brightness - 5) * 18) << 24 | 0xFFFFFF);
    }

    private void hot(int x, int y, int w, int h, Runnable r) {
        hots.add(new Hot(x, y, w, h, r));
    }

    private boolean hover(int x, int y, int w, int h) {
        return mouseSX >= x && mouseSX < x + w && mouseSY >= y && mouseSY < y + h;
    }

    /** 설명서 화면의 회색 버튼 (선택되면 파란색) */
    private void button(int x, int y, int w, int h, String label, boolean selected, boolean enabled, Runnable action) {
        boolean hv = enabled && hover(x, y, w, h);
        int top = selected ? 0xFF47B6F0 : enabled ? (hv ? 0xFFF2F4F7 : 0xFFE4E7EC) : 0xFFC9CDD3;
        int bottom = selected ? 0xFF1F86C9 : enabled ? (hv ? 0xFFC9CED6 : 0xFFB9BFC8) : 0xFFB3B8BF;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        frame(x, y, w, h, selected ? 0xFF16679C : 0xFF8C939D);
        fill(x + 1, y + 1, w - 2, 1, selected ? 0x55FFFFFF : 0x99FFFFFF);
        int color = selected ? WHITE : enabled ? DARK : 0xFF8E949C;
        textFit(label, x + w / 2f, y + (h - 6) / 2f, color, 0.75f, w - 4);
        if (enabled) hot(x, y, w, h, action);
    }

    /** 위쪽 탭 줄 */
    private void tabRow(int x, int y, int w, String group, String[] keys, int selected, int first, int count, Consumer<Integer> onSelect) {
        fill(x, y, w, 16, 0xC0343A44);
        boolean arrows = count < keys.length;
        int ax = arrows ? 12 : 0;
        int tw = (w - ax * 2) / count;
        if (arrows) {
            text("<", x + 4, y + 4, first > 0 ? WHITE : 0xFF666C76, 0.9f);
            text(">", x + w - 9, y + 4, first + count < keys.length ? WHITE : 0xFF666C76, 0.9f);
            int f = first;
            if (f > 0) hot(x, y, ax, 16, () -> scrollTabs(group, -1));
            if (f + count < keys.length) hot(x + w - ax, y, ax, 16, () -> scrollTabs(group, 1));
        }
        for (int i = 0; i < count && first + i < keys.length; i++) {
            int idx = first + i;
            int tx = x + ax + i * tw;
            boolean sel = idx == selected;
            if (sel) g.fillGradient(tx + 2, y + 2, tx + tw - 2, y + 14, 0xFF4CBDF5, 0xFF1F89CF);
            textFit(t(group + "." + keys[idx]), tx + tw / 2f, y + 5, sel ? WHITE : 0xFFD7DCE3, 0.7f, tw - 6);
            hot(tx, y, tw, 16, () -> onSelect.accept(idx));
        }
    }

    private void scrollTabs(String group, int d) {
        if (group.equals("inquiry")) inquiryScroll = Math.max(0, Math.min(INQUIRY_TABS.length - 5, inquiryScroll + d));
        else settingScroll = Math.max(0, Math.min(SETTING_TABS.length - 5, settingScroll + d));
    }

    /** 반투명 흰 패널 */
    private void panel(int x, int y, int w, int h) {
        fill(x, y, w, h, 0x8CF4F6F9);
        frame(x, y, w, h, 0xC0FFFFFF);
    }

    private void darkPanel(int x, int y, int w, int h) {
        fill(x, y, w, h, 0xB0283038);
        frame(x, y, w, h, 0xFF59626E);
    }

    // ------------------------------------------------------------------ 렌더

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.g = graphics;
        renderBackground(graphics);
        hots.clear();
        keyInput = null;
        layout();
        int sxGui = bx + Math.round(SX * s), syGui = by + Math.round(SY * s);
        mouseSX = (int) Math.floor(((mouseX - bx) / s - SX) / K);
        mouseSY = (int) Math.floor(((mouseY - by) / s - SY) / K);

        RenderSystem.enableBlend();
        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(bx, by, 0);
        ps.scale(s, s, 1);
        graphics.blit(BODY, 0, 0, BW, BH, 0, 0, BW * 2, BH * 2, BW * 2, BH * 2);
        drawButtonLeds();

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
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Button b) b.visible = talking;
        }
        if (talking) graphics.drawString(font, t("message_label"), msgBox.getX() - font.width(t("message_label")) - 6, msgBox.getY() + 4, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private boolean blink() {
        return (System.currentTimeMillis() / 400) % 2 == 0;
    }

    /** 본체 오른쪽 버튼 LED */
    private void drawButtonLeds() {
        CallState cs = callState();
        int[] colors = new int[5];
        if (data.flag("emergency") && blink()) colors[0] = 0xFFFF3B30;
        if (data.flag("outing")) colors[1] = 0xFF3DDC84;
        if (incoming() && key().startsWith("guard:") && cs == CallState.RINGING && blink()) colors[2] = 0xFF3DA5FF;
        if (cs == CallState.CONNECTED || (cs == CallState.DIALING) || (cs == CallState.RINGING && blink())) colors[3] = 0xFF3DA5FF;
        if (cs == CallState.CONNECTED && incoming() && !Intercom.isReceiverKey(key())) colors[4] = 0xFF3DA5FF;
        for (int i = 0; i < 5; i++) {
            int y = BUTTON_Y[i];
            if (colors[i] != 0) {
                g.fill(BUTTON_X1 + 3, y - 2, BUTTON_X2 - 3, y + 2, colors[i]);
                g.fill(BUTTON_X1 + 1, y - 4, BUTTON_X2 - 1, y + 4, (colors[i] & 0xFFFFFF) | 0x40000000);
            }
        }
    }

    private void drawScreen() {
        switch (page) {
            case MAIN -> drawMain();
            case TOUCH -> drawTouch();
            default -> drawSub();
        }
        if (data.flag("alarm") && page != Page.TOUCH) drawAlarmBanner();
        if (keypad != null) drawKeypad();
        if (popup != null) drawPopup();
    }

    // ------------------------------------------------------------------ 메인 화면

    private int outdoorTemp() {
        var level = Minecraft.getInstance().level;
        if (level == null) return 20;
        int temp = Math.round(level.getBiome(pos).value().getBaseTemperature() * 25f);
        if (level.isRaining()) temp -= 3;
        return Math.max(-15, Math.min(40, temp));
    }

    private void drawMain() {
        g.blit(BG, 0, 0, W, H, 0, 0, 800, 450, 800, 450);
        // 왼쪽 정보 패널
        fill(0, 0, 132, H, 0x50102A55);
        fill(132, 0, 1, H, 0x40FFFFFF);

        var level = Minecraft.getInstance().level;
        boolean cold = level != null && level.getBiome(pos).value().getBaseTemperature() < 0.15f;
        String weather;
        int wicon;
        if (level != null && level.isThundering()) {
            weather = t("weather.thunder");
            wicon = IC_RAIN;
        } else if (level != null && level.isRaining()) {
            weather = cold ? t("weather.snow") : t("weather.rain");
            wicon = cold ? IC_SNOW : IC_RAIN;
        } else if (level != null && level.getDayTime() % 24000 > 12500 && level.getDayTime() % 24000 < 23500) {
            weather = t("weather.clear_night");
            wicon = IC_CLOUD;
        } else {
            weather = t("weather.clear");
            wicon = IC_SUN;
        }
        icon(wicon, 8, 8, 40);
        shadowText(outdoorTemp() + "°", 56, 10, WHITE, 2.4f);
        shadowText(weather, 58, 34, WHITE, 0.75f);
        LocalDateTime now = LocalDateTime.now();
        String wd = t("wd." + (now.getDayOfWeek().getValue() % 7));
        String clock = now.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")) + (now.getHour() < 12 ? " AM" : " PM");
        shadowText(wd + " " + clock, 8, 56, WHITE, 0.72f);

        drawCalendar(4, 72);

        // 상태표시
        fill(0, 203, 132, 22, 0x70000000);
        boolean net = !data.unit().isEmpty();
        icon(IC_WIFI, 6, 206, 16, net ? GREEN : 0xFFFFFFFF);
        hot(4, 204, 20, 20, () -> info(net ? t("status.net_ok", data.unit()) : t("status.net_none")));
        boolean outing = data.flag("outing");
        icon(IC_PERSON, 38, 206, 16, outing ? GREEN : 0xFFFFFFFF);
        hot(36, 204, 20, 20, () -> confirm(outing ? t("status.outing_off") : t("status.outing_on"), () -> send(Action.OUTING)));
        icon(IC_NOTICE, 70, 206, 16);
        hot(68, 204, 20, 20, () -> {
            go(Page.INQUIRY);
            tabs.put(Page.INQUIRY, 0);
        });
        icon(IC_ELEVATOR, 102, 206, 16);
        hot(100, 204, 20, 20, () -> {
            go(Page.INQUIRY);
            tabs.put(Page.INQUIRY, 5);
            inquiryScroll = 5;
        });
        if (!data.missed().isEmpty() || !data.visitors().isEmpty() && newVisitor()) {
            g.fill(114, 205, 118, 209, RED);
        }

        // 로고
        shadowText("HOME", 340, 9, WHITE, 0.9f);
        text("NET", 366, 11, 0xFFE0ECF8, 0.6f);

        // 아이콘
        int[] xs = {160, 216, 272, 328};
        Page[] row1 = {Page.SECURITY, Page.CONTROL, Page.CALL, Page.INQUIRY};
        int[] ic1 = {IC_SECURITY, IC_CONTROL, IC_CALL, IC_INQUIRY};
        String[] l1 = {"security", "control", "call", "inquiry"};
        for (int i = 0; i < 4; i++) mainIcon(xs[i], 52, ic1[i], t("menu." + l1[i]), row1[i]);
        mainIcon(xs[0], 122, IC_SETTINGS, t("menu.settings"), Page.SETTINGS);
        mainIcon(xs[1], 122, IC_ENERGY, t("menu.energy"), Page.ENERGY);

        if (callState() != CallState.IDLE) {
            String st = callStatusLine();
            fill(140, 192, 254, 14, 0xA0000000);
            textC(st, 267, 195, YELLOW, 0.7f);
            hot(140, 192, 254, 14, () -> go(Page.CALL));
        }
    }

    private boolean newVisitor() {
        return !data.visitors().isEmpty() && System.currentTimeMillis() - data.visitors().get(0).dayTime() < 10 * 60_000L;
    }

    private void mainIcon(int x, int y, int idx, String label, Page target) {
        boolean hv = hover(x, y, 44, 44);
        if (hv) fill(x - 2, y - 2, 48, 48, 0x40FFFFFF);
        icon(idx, x, y, 44);
        float lw = width(label, 0.8f);
        shadowText(label, x + 22 - lw / 2, y + 48, WHITE, 0.8f);
        hot(x - 4, y - 4, 52, 62, () -> go(target));
    }

    private void drawCalendar(int x, int y) {
        LocalDate first = LocalDate.of(calYear, calMonth, 1);
        LocalDate today = LocalDate.now();
        String header = t("cal.header", calMonth, calYear);
        shadowText("<", x + 4, y + 2, WHITE, 0.9f);
        hot(x, y, 16, 12, () -> shiftMonth(-1));
        textC(header, x + 62, y + 3, WHITE, 0.75f);
        shadowText(">", x + 116, y + 2, WHITE, 0.9f);
        hot(x + 110, y, 16, 12, () -> shiftMonth(1));
        fill(x, y + 13, 124, 10, 0x50FFC94D);
        String[] wd = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) textC(wd[i], x + 9 + i * 17.7f, y + 15, i == 6 ? 0xFFFF8A80 : WHITE, 0.6f);
        int offset = first.getDayOfWeek().getValue() - 1;
        int days = first.lengthOfMonth();
        for (int d = 1; d <= days; d++) {
            int cell = offset + d - 1;
            float cx = x + 9 + (cell % 7) * 17.7f;
            int cy = y + 27 + (cell / 7) * 17;
            boolean isToday = today.getYear() == calYear && today.getMonthValue() == calMonth && today.getDayOfMonth() == d;
            if (isToday) fill(Math.round(cx) - 7, cy - 3, 15, 13, 0x90FFFFFF);
            DayOfWeek dw = first.plusDays(d - 1).getDayOfWeek();
            int c = isToday ? RED : dw == DayOfWeek.SUNDAY ? 0xFFFFB0A8 : WHITE;
            textC(String.valueOf(d), cx, cy, c, 0.62f);
        }
    }

    private void shiftMonth(int d) {
        LocalDate m = LocalDate.of(calYear, calMonth, 1).plusMonths(d);
        calYear = m.getYear();
        calMonth = m.getMonthValue();
    }

    private String callStatusLine() {
        CallState cs = callState();
        if (data.outgoing()) {
            return cs == CallState.DIALING ? t("call.dialing", peerDisplay()) : t("call.talking_with", peerDisplay(), talkTime());
        }
        String who = Intercom.sideName(key()).getString();
        return cs == CallState.RINGING ? t("call.incoming", who) : t("call.talking_with", who, talkTime());
    }

    private String talkTime() {
        long sec = Math.max(0, (System.currentTimeMillis() - connectedSince) / 1000);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private void drawAlarmBanner() {
        if (!blink()) return;
        fill(0, 0, W, 16, 0xE0D02020);
        textC(data.flag("emergency") ? t("alarm.emergency") : t("alarm.test"), W / 2f - 30, 4, WHITE, 0.8f);
        fill(W - 64, 2, 60, 12, 0xFFFFFFFF);
        textC(t("security.stop"), W - 34, 4, RED, 0.7f);
        hot(W - 64, 0, 64, 16, () -> send(Action.ALARM_STOP));
    }

    // ------------------------------------------------------------------ 서브 화면 틀

    private void drawSub() {
        g.blit(BG_SUB, 0, 0, W, H, 0, 0, 800, 450, 800, 450);
        // 홈 / 도움말
        icon(IC_HOME, 4, 2, 14, hover(2, 0, 20, 18) ? 0xFF8FD3FF : WHITE);
        hot(0, 0, 24, 18, () -> go(Page.MAIN));
        icon(IC_HELP, W - 18, 2, 14, hover(W - 22, 0, 22, 18) ? 0xFF8FD3FF : WHITE);
        hot(W - 24, 0, 24, 18, () -> info(t("help." + page.name().toLowerCase(java.util.Locale.ROOT))));
        // 왼쪽 메뉴
        for (int i = 0; i < MENU.length; i++) {
            int y = 22 + i * 27;
            boolean sel = page == MENU[i];
            Page target = MENU[i];
            if (sel) g.fillGradient(3, y, 74, y + 22, 0xFF4CBDF5, 0xFF1F89CF);
            else fill(3, y, 71, 22, hover(3, y, 71, 22) ? 0xC0505A68 : 0xA0384049);
            frame(3, y, 71, 22, sel ? 0xFF8FD3FF : 0xFF6B7480);
            icon(MENU_ICON[i], 8, y + 4, 14, WHITE);
            text(t("menu." + MENU_KEY[i]), 27, y + 8, WHITE, 0.75f);
            hot(3, y, 71, 22, () -> go(target));
        }
        // 내용 영역
        frame(78, 19, 319, 204, 0xFF3FA7DD);
        switch (page) {
            case SECURITY -> drawSecurity();
            case CONTROL -> drawControl();
            case CALL -> drawCall();
            case INQUIRY -> drawInquiry();
            case ENERGY -> drawEnergy();
            case SETTINGS -> drawSettings();
            default -> {}
        }
    }

    private static final int CX = 80, CY = 38, CW = 315, CH = 183;

    // ------------------------------------------------------------------ 방범

    private void drawSecurity() {
        int tb = tab(Page.SECURITY);
        tabRow(79, 20, 317, "security", new String[]{"set", "test"}, tb, 0, 2, i -> tabs.put(Page.SECURITY, i));
        panel(CX + 4, CY + 4, 216, 150);
        if (tb == 0) {
            boolean s1 = data.flag("sec1"), s2 = data.flag("sec2");
            securityTile(CX + 16, CY + 30, IC_SENSOR_DOOR, t("security.sensor1"), s1, () -> send(Action.SET_SETTING, "sec1=" + (s1 ? 0 : 1)));
            securityTile(CX + 82, CY + 30, IC_SENSOR_WINDOW, t("security.sensor2"), s2, () -> send(Action.SET_SETTING, "sec2=" + (s2 ? 0 : 1)));
            securityTile(CX + 148, CY + 30, IC_GAS, t("security.gas"), false, () -> info(t("security.gas_ok")));
            String mode = data.flag("outing") ? t("security.mode_out") : (s1 || s2) ? t("security.mode_home") : t("security.mode_off");
            text(t("security.state", mode), CX + 12, CY + 120, DARK, 0.75f);
            if (data.flag("emergency")) text(t("alarm.emergency"), CX + 12, CY + 136, RED, 0.75f);
        } else {
            icon(IC_SPEAKER, CX + 20, CY + 30, 40, DARK);
            text(t("security.test_desc1"), CX + 70, CY + 36, DARK, 0.75f);
            text(t("security.test_desc2"), CX + 70, CY + 50, DIM, 0.7f);
            button(CX + 70, CY + 76, 90, 22, t("security.test_btn"), data.flag("alarm"), true, () -> send(Action.ALARM_TEST));
        }
        int rx = CX + 226;
        darkPanel(rx, CY + 4, 85, 150);
        button(rx + 6, CY + 16, 73, 24, t("security.reset"), false, true, () -> send(Action.ALARM_STOP));
        button(rx + 6, CY + 48, 73, 24, t("security.stop"), false, true, () -> send(Action.ALARM_STOP));
        button(rx + 6, CY + 80, 73, 24, t("security.outing"), data.flag("outing"), true, () -> send(Action.OUTING));
        button(rx + 6, CY + 112, 73, 24, t("security.emergency"), data.flag("emergency"), true, () -> send(Action.EMERGENCY));
        text(t("security.hint"), CX + 6, CY + 162, 0xFF3A414C, 0.65f);
    }

    private void securityTile(int x, int y, int ic, String label, boolean on, Runnable r) {
        if (on) g.fillGradient(x - 4, y - 4, x + 56, y + 66, 0xFF63C8F7, 0xFF2A93D6);
        else fill(x - 4, y - 4, 60, 70, hover(x - 4, y - 4, 60, 70) ? 0xFFFFFFFF : 0xFFEDEFF2);
        frame(x - 4, y - 4, 60, 70, on ? 0xFF1A6EA5 : 0xFF9AA1AB);
        icon(ic, x + 4, y, 44);
        textC(label, x + 26, y + 52, on ? WHITE : DARK, 0.7f);
        hot(x - 4, y - 4, 60, 70, r);
    }

    // ------------------------------------------------------------------ 제어

    private List<DeviceEntry> devices(DeviceType... types) {
        List<DeviceEntry> out = new ArrayList<>();
        for (DeviceEntry e : data.devices()) {
            DeviceType dt = DeviceType.byId(e.type());
            for (DeviceType t : types) if (dt == t && e.online()) out.add(e);
        }
        return out;
    }

    private String deviceName(DeviceEntry e, int idx) {
        if (!e.name().isEmpty()) return e.name();
        return DeviceType.byId(e.type()).displayName().getString() + " " + (idx + 1);
    }

    private void drawControl() {
        int tb = tab(Page.CONTROL);
        boolean hasOther = !devices(DeviceType.OUTLET, DeviceType.DOOR_LOCK, DeviceType.CURTAIN, DeviceType.OTHER).isEmpty();
        int count = hasOther ? 6 : 5;
        if (tb >= count) tb = 0;
        tabRow(79, 20, 317, "control", CONTROL_TABS, tb, 0, count, i -> {
            tabs.put(Page.CONTROL, i);
            roomPage = 0;
        });
        switch (tb) {
            case 0 -> drawGas();
            case 1 -> drawLights();
            case 2 -> drawClimate(DeviceType.HEATING);
            case 3 -> drawVent();
            case 4 -> drawClimate(DeviceType.AIRCON);
            default -> drawOther();
        }
    }

    private void noDevice(String typeKey) {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        textC(t("control.none", t("control." + typeKey)), CX + CW / 2f, CY + 70, DARK, 0.8f);
        textC(t("control.none_hint"), CX + CW / 2f, CY + 86, DIM, 0.65f);
    }

    /** 오른쪽 방(기기) 선택 버튼, 4개씩 페이지 */
    private int roomButtons(List<String> names, int selected, Consumer<Integer> onSelect) {
        int rx = CX + 226;
        darkPanel(rx, CY + 4, 85, CH - 30);
        int pages = Math.max(1, (names.size() + 3) / 4);
        roomPage = Math.min(roomPage, pages - 1);
        for (int i = 0; i < 4; i++) {
            int idx = roomPage * 4 + i;
            if (idx >= names.size()) break;
            button(rx + 6, CY + 12 + i * 30, 73, 24, names.get(idx), idx == selected, true, () -> onSelect.accept(idx));
        }
        if (pages > 1) {
            button(rx + 6, CY + 128, 34, 14, "<", false, roomPage > 0, () -> roomPage--);
            button(rx + 45, CY + 128, 34, 14, ">", false, roomPage < pages - 1, () -> roomPage++);
        }
        return Math.min(selected, Math.max(0, names.size() - 1));
    }

    private void drawGas() {
        List<DeviceEntry> gas = devices(DeviceType.GAS);
        if (gas.isEmpty()) {
            noDevice("gas");
            return;
        }
        selectedGas = Math.min(selectedGas, gas.size() - 1);
        DeviceEntry e = gas.get(selectedGas);
        panel(CX + 4, CY + 4, 216, CH - 8);
        g.blit(ICONS, CX + 30, CY + 12, 160, 160, 0, 640, 256, 256, 1024, 1024);
        g.blit(ICONS, CX + 30, CY + 12, 160, 160, e.on() ? 256 : 512, 640, 256, 256, 1024, 1024);
        int rx = CX + 226;
        darkPanel(rx, CY + 4, 85, CH - 8);
        String[] lines = t(e.on() ? "control.gas_open" : "control.gas_closed").split("\n");
        for (int i = 0; i < lines.length; i++) textC(lines[i], rx + 42, CY + 40 + i * 11, WHITE, 0.75f);
        button(rx + 12, CY + 80, 61, 22, t("control.close"), e.on(), e.on(), () -> send(Action.SET_POWER, e.pos(), "0"));
        if (gas.size() > 1) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < gas.size(); i++) names.add(deviceName(gas.get(i), i));
            for (int i = 0; i < names.size() && i < 3; i++) {
                int idx = i;
                button(rx + 6, CY + 116 + i * 18, 73, 15, names.get(i), i == selectedGas, true, () -> selectedGas = idx);
            }
        }
        text(t("control.gas_note"), CX + 8, CY + CH - 16, 0xFF3A414C, 0.6f);
    }

    /** "거실 1" → 방 "거실" */
    private static String roomOf(String name) {
        int sp = name.lastIndexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }

    private void drawLights() {
        List<DeviceEntry> lights = devices(DeviceType.LIGHT);
        if (lights.isEmpty()) {
            noDevice("light");
            return;
        }
        List<String> rooms = new ArrayList<>();
        Map<String, List<DeviceEntry>> byRoom = new LinkedHashMap<>();
        Map<DeviceEntry, String> labels = new java.util.HashMap<>();
        for (int i = 0; i < lights.size(); i++) {
            DeviceEntry e = lights.get(i);
            String name = deviceName(e, i);
            String room = e.name().isEmpty() ? t("control.light_all_room") : roomOf(name);
            labels.put(e, name.equals(room) ? name : name.substring(Math.min(name.length(), room.length())).trim());
            if (!byRoom.containsKey(room)) rooms.add(room);
            byRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(e);
        }
        if (!byRoom.containsKey(selectedRoom)) selectedRoom = rooms.get(0);
        int sel = rooms.indexOf(selectedRoom);
        roomButtons(rooms, sel, i -> selectedRoom = rooms.get(i));
        List<DeviceEntry> list = byRoom.get(selectedRoom);
        panel(CX + 4, CY + 4, 216, CH - 30);
        for (int i = 0; i < list.size() && i < 12; i++) {
            DeviceEntry e = list.get(i);
            int x = CX + 12 + (i % 4) * 52, y = CY + 10 + (i / 4) * 48;
            boolean hv = hover(x, y, 46, 44);
            if (e.on()) g.fillGradient(x, y, x + 46, y + 44, 0xFF63C8F7, 0xFF2A93D6);
            else fill(x, y, 46, 44, hv ? 0xFFFFFFFF : 0xFFEDEFF2);
            frame(x, y, 46, 44, e.on() ? 0xFF1A6EA5 : 0xFF9AA1AB);
            icon(e.on() ? IC_BULB_ON : IC_BULB_OFF, x + 9, y + 2, 28);
            textFit(labels.get(e), x + 23, y + 33, e.on() ? WHITE : DARK, 0.62f, 42);
            hot(x, y, 46, 44, () -> send(Action.TOGGLE, e.pos(), ""));
        }
        boolean anyOn = list.stream().anyMatch(DeviceEntry::on);
        int by2 = CY + CH - 22;
        fill(CX + 4, by2 - 2, CW - 8, 20, 0xB0283038);
        // 방 전체 조명 ON/OFF
        int sx = CX + 10;
        g.fillGradient(sx, by2 + 1, sx + 40, by2 + 15, anyOn ? 0xFF63C8F7 : 0xFFE4E7EC, anyOn ? 0xFF2A93D6 : 0xFFB9BFC8);
        frame(sx, by2 + 1, 40, 14, 0xFF59626E);
        textC(anyOn ? "ON" : "OFF", sx + 20, by2 + 5, anyOn ? WHITE : DARK, 0.65f);
        hot(sx, by2, 40, 16, () -> {
            for (DeviceEntry e : list) send(Action.SET_POWER, e.pos(), anyOn ? "0" : "1");
        });
        text(t("control.room_all", selectedRoom), sx + 46, by2 + 5, WHITE, 0.65f);
        button(CX + CW - 92, by2, 84, 16, t("control.all_off"), false, true, () -> send(Action.ALL_LIGHTS_OFF));
    }

    private void drawClimate(DeviceType type) {
        boolean heat = type == DeviceType.HEATING;
        List<DeviceEntry> list = devices(type);
        if (list.isEmpty()) {
            noDevice(heat ? "heating" : "aircon");
            return;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) names.add(deviceName(list.get(i), i));
        int sel = heat ? selectedHeat : selectedAc;
        sel = roomButtons(names, sel, i -> {
            if (heat) selectedHeat = i;
            else selectedAc = i;
        });
        DeviceEntry e = list.get(sel);
        panel(CX + 4, CY + 4, 216, CH - 30);
        icon(heat ? IC_HEATER : IC_AC, CX + 12, CY + 10, 22);
        text(t(heat ? "control.heat_title" : "control.ac_title"), CX + 40, CY + 18, DARK, 0.8f);
        textBox(CX + 16, CY + 48, t("control.current"), e.roomTemp() + "℃");
        textBox(CX + 16, CY + 80, t("control.set"), e.setTemp() + "℃");
        button(CX + 160, CY + 46, 30, 26, "+", false, true, () -> send(Action.SET_TEMP, e.pos(), "+1"));
        button(CX + 160, CY + 78, 30, 26, "−", false, true, () -> send(Action.SET_TEMP, e.pos(), "-1"));
        if (heat && e.away()) text(t("control.away_on"), CX + 16, CY + 112, BLUE, 0.7f);
        int by2 = CY + CH - 22;
        fill(CX + 4, by2 - 2, CW - 8, 20, 0xB0283038);
        button(CX + 10, by2, 44, 16, t("control.power"), e.on(), true, () -> send(Action.SET_POWER, e.pos(), e.on() ? "0" : "1"));
        if (heat) button(CX + 58, by2, 44, 16, t("control.away"), e.away(), true, () -> send(Action.SET_AWAY, e.pos(), e.away() ? "0" : "1"));
        text(e.on() ? t(heat ? "control.heat_on" : "control.ac_on") : t("control.off"), CX + 110, by2 + 5, e.on() ? 0xFF8FE0A0 : 0xFFB8C0CA, 0.65f);
    }

    private void textBox(int x, int y, String label, String value) {
        fill(x, y, 54, 22, 0xFF59626E);
        textC(label, x + 27, y + 8, WHITE, 0.65f);
        fill(x + 58, y, 82, 22, 0xFFFFFFFF);
        frame(x + 58, y, 82, 22, 0xFF8C939D);
        textC(value, x + 99, y + 5, DARK, 1.3f);
    }

    private void drawVent() {
        List<DeviceEntry> list = devices(DeviceType.VENT);
        if (list.isEmpty()) {
            noDevice("vent");
            return;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) names.add(deviceName(list.get(i), i));
        selectedVent = roomButtons(names, selectedVent, i -> selectedVent = i);
        DeviceEntry e = list.get(selectedVent);
        panel(CX + 4, CY + 4, 216, CH - 30);
        icon(IC_FAN, CX + 12, CY + 10, 22);
        text(t("control.vent_title"), CX + 40, CY + 18, DARK, 0.8f);
        String[] lv = {t("control.weak"), t("control.mid"), t("control.strong")};
        for (int i = 0; i < 3; i++) {
            int x = CX + 22 + i * 62, y = CY + 50;
            boolean sel = e.on() && e.level() == i + 1;
            if (sel) g.fillGradient(x, y, x + 54, y + 54, 0xFF63C8F7, 0xFF2A93D6);
            else fill(x, y, 54, 54, hover(x, y, 54, 54) ? 0xFFFFFFFF : 0xFFEDEFF2);
            frame(x, y, 54, 54, sel ? 0xFF1A6EA5 : 0xFF9AA1AB);
            int sz = 22 + i * 6;
            icon(IC_FAN, x + 27 - sz / 2, y + 22 - sz / 2, sz);
            textC(lv[i], x + 27, y + 43, sel ? WHITE : DARK, 0.65f);
            int level = i + 1;
            hot(x, y, 54, 54, () -> send(Action.SET_LEVEL, e.pos(), String.valueOf(level)));
        }
        int by2 = CY + CH - 22;
        fill(CX + 4, by2 - 2, CW - 8, 20, 0xB0283038);
        button(CX + CW / 2 - 70, by2, 44, 16, t("control.power"), e.on(), true, () -> send(Action.SET_POWER, e.pos(), e.on() ? "0" : "1"));
    }

    private void drawOther() {
        List<DeviceEntry> list = devices(DeviceType.OUTLET, DeviceType.DOOR_LOCK, DeviceType.CURTAIN, DeviceType.OTHER);
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        for (int i = 0; i < list.size() && i < 16; i++) {
            DeviceEntry e = list.get(i);
            DeviceType dt = DeviceType.byId(e.type());
            int x = CX + 10 + (i % 2) * 152, y = CY + 10 + (i / 2) * 20;
            button(x, y, 146, 17, deviceName(e, i) + "  ·  " + dt.stateName(e.on()).getString(), e.on(), true,
                    () -> send(Action.TOGGLE, e.pos(), ""));
        }
    }

    // ------------------------------------------------------------------ 통화

    private void drawCall() {
        int tb = tab(Page.CALL);
        tabRow(79, 20, 317, "call", CALL_TABS, tb, 0, 4, i -> tabs.put(Page.CALL, i));
        switch (tb) {
            case 0 -> drawFrontDoor();
            case 1 -> drawHousehold();
            case 2 -> drawGuard();
            default -> drawPhone();
        }
        drawCallBar();
    }

    /** 아래쪽 밝기 / 음량 조절 줄 */
    private void drawCallBar() {
        int y = CY + CH - 18;
        fill(CX, y, CW, 18, 0xC0343A44);
        icon(IC_BRIGHT, CX + 4, y + 3, 12, WHITE);
        slider(CX + 20, y + 3, brightness, v -> brightness = v);
        boolean talking = callState() == CallState.CONNECTED;
        String volKey = talking ? "vol_call" : "vol_ring";
        int vol = parse(data.setting(volKey, "5"), 5);
        icon(IC_SPEAKER, CX + 166, y + 3, 12, WHITE);
        slider(CX + 182, y + 3, vol, v -> send(Action.SET_SETTING, volKey + "=" + v));
    }

    /** − ▮▮▮▮ + (1~10) */
    private void slider(int x, int y, int value, Consumer<Integer> set) {
        fill(x, y, 128, 12, 0xFFF4F5F7);
        frame(x, y, 128, 12, 0xFF8C939D);
        textC("−", x + 6, y + 2, DARK, 0.8f);
        textC("+", x + 122, y + 2, DARK, 0.8f);
        hot(x, y, 12, 12, () -> set.accept(Math.max(1, value - 1)));
        hot(x + 116, y, 12, 12, () -> set.accept(Math.min(10, value + 1)));
        for (int i = 0; i < 10; i++) {
            int bxx = x + 16 + i * 10;
            fill(bxx, y + 2, 7, 8, i < value ? 0xFF59626E : 0xFFD0D4DA);
            int v = i + 1;
            hot(bxx - 1, y, 10, 12, () -> set.accept(v));
        }
    }

    private int videoX = CX + 4, videoY = CY + 4, videoW = 222, videoH = CH - 30;

    private void videoArea(int camIdx, boolean on) {
        fill(videoX - 1, videoY - 1, videoW + 2, videoH + 2, 0xFF59626E);
        if (on) cam(camIdx, videoX, videoY, videoW, videoH);
        else fill(videoX, videoY, videoW, videoH, 0x60E8ECF0);
    }

    private void overlayLine(String s1, int color) {
        fill(videoX, videoY, videoW, 13, 0xA0000000);
        textFit(s1, videoX + videoW / 2f, videoY + 3, color, 0.7f, videoW - 6);
    }

    private void drawLog() {
        List<IntercomLine> log = data.log();
        int n = Math.min(3, log.size());
        int y = videoY + videoH - 4 - n * 9;
        for (int i = log.size() - n; i < log.size(); i++) {
            IntercomLine l = log.get(i);
            String line = "[" + Intercom.sideName(l.side()).getString() + "] " + l.name() + ": " + l.text();
            fill(videoX + 2, y - 1, videoW - 4, 9, 0x90000000);
            List<FormattedCharSequence> parts = font.split(Component.literal(line), (int) ((videoW - 8) / 0.6f));
            if (!parts.isEmpty()) {
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(videoX + 4, y, 0);
                ps.scale(0.6f, 0.6f, 1);
                g.drawString(font, parts.get(0), 0, 0, WHITE, false);
                ps.popPose();
            }
            y += 9;
        }
    }

    private void drawFrontDoor() {
        CallState cs = callState();
        String k = key();
        boolean doorCall = incoming() && !Intercom.isReceiverKey(k);
        boolean monitoring = !doorCall && System.currentTimeMillis() < monitorUntil;
        int camIdx = k.equals("lobby") ? CAM_LOBBY : CAM_DOOR;
        videoArea(doorCall ? camIdx : CAM_DOOR, doorCall || monitoring);
        if (doorCall) {
            overlayLine(cs == CallState.RINGING ? t("call.from_" + (k.equals("lobby") ? "lobby" : "front")) : t("call.talking", talkTime()),
                    cs == CallState.RINGING ? YELLOW : 0xFF9CF0B0);
            drawLog();
        } else if (monitoring) {
            overlayLine(t("call.monitoring"), WHITE);
        } else if (callState() != CallState.IDLE) {
            overlayLine(callStatusLine(), YELLOW);
        }
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        boolean ringing = doorCall && cs == CallState.RINGING, talking = doorCall && cs == CallState.CONNECTED;
        button(rx + 2, CY + 14, 71, 26, t("call.talk"), talking || ringing && blink(), callState() == CallState.IDLE || doorCall, () -> {
            if (ringing) send(Action.ANSWER);
            else if (talking) send(Action.HANG_UP);
            else monitorUntil = monitoring ? 0 : System.currentTimeMillis() + 30_000;
        });
        button(rx + 2, CY + 46, 71, 26, t("call.open_door"), false, talking, () -> send(Action.OPEN_DOOR));
        button(rx + 2, CY + 78, 71, 26, t("call.save_video"), false, doorCall, () -> send(Action.SAVE_VISITOR));
        if (!talking && !ringing) text(t("call.open_note"), rx, CY + 112, 0xFFD7DCE3, 0.55f);
    }

    private void drawHousehold() {
        CallState cs = callState();
        boolean unitCall = incoming() && key().startsWith("unit:");
        boolean outUnit = data.outgoing() && !isGuardPeer();
        boolean ringing = unitCall && cs == CallState.RINGING;
        boolean talking = (unitCall || outUnit) && cs == CallState.CONNECTED;
        boolean dialing = outUnit && cs == CallState.DIALING;
        int lx = CX + 4;
        panel(lx, CY + 4, 222, CH - 30);
        if (talking && videoCall && !videoBlocked) {
            cam(CAM_NEIGHBOR, lx + 2, CY + 6, 218, CH - 34);
            overlayLineAt(lx + 2, CY + 6, 218, t("call.video_talking", peerName(), talkTime()), 0xFF9CF0B0);
        } else {
            // 세대 입력
            fill(lx + 6, CY + 10, 210, 18, WHITE);
            frame(lx + 6, CY + 10, 210, 18, 0xFF8C939D);
            String shown = unitInput.isEmpty() ? t("call.unit_hint") : unitInput;
            text(shown, lx + 10, CY + 15, unitInput.isEmpty() ? 0xFF9AA1AB : DARK, 0.8f);
            button(lx + 196, CY + 12, 18, 14, "<", false, !unitInput.isEmpty(), () -> unitInput = backspaceUnit(unitInput));
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", t("call.dong"), "0", t("call.ho")};
            for (int i = 0; i < 12; i++) {
                int x = lx + 8 + (i % 3) * 70, y = CY + 32 + (i / 3) * 29;
                String kk = keys[i];
                int idx = i;
                button(x, y, 64, 25, kk, false, cs == CallState.IDLE, () -> unitKey(idx));
            }
            keyInput = cs == CallState.IDLE ? c -> {
                if (c.equals("\b")) unitInput = backspaceUnit(unitInput);
                else if (c.matches("\\d") && unitInput.length() < 14) unitInput += c;
            } : null;
            if (ringing || dialing || talking) {
                String st = ringing ? t("call.from_unit", peerName()) : dialing ? t("call.dialing_unit", peerDisplay()) : t("call.talking_with", peerName(), talkTime());
                fill(lx + 2, CY + 60, 218, 22, 0xD0000000);
                textFit(st, lx + 111, CY + 67, ringing || dialing ? YELLOW : 0xFF9CF0B0, 0.8f, 210);
            }
            if (talking) drawLogIn(lx + 2, CY + 88);
        }
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        boolean idle = cs == CallState.IDLE;
        button(rx + 2, CY + 14, 71, 26, t("call.voice"), (talking || dialing) && !videoCall || ringing && blink(), idle || ringing, () -> {
            videoCall = false;
            if (ringing) send(Action.ANSWER);
            else dialUnit();
        });
        String videoLabel = talking && videoCall ? t(videoBlocked ? "call.video_unblock" : "call.video_block") : t("call.video");
        button(rx + 2, CY + 46, 71, 26, videoLabel, (talking || dialing) && videoCall, idle || ringing || talking && videoCall, () -> {
            if (talking) {
                videoBlocked = !videoBlocked;
                return;
            }
            videoCall = true;
            videoBlocked = true;
            if (ringing) send(Action.ANSWER);
            else dialUnit();
        });
        button(rx + 2, CY + 78, 71, 26, t("call.end"), false, ringing || talking || dialing, () -> send(Action.HANG_UP));
    }

    private void overlayLineAt(int x, int y, int w, String str, int color) {
        fill(x, y, w, 13, 0xA0000000);
        textFit(str, x + w / 2f, y + 3, color, 0.7f, w - 6);
    }

    private void drawLogIn(int x, int y) {
        List<IntercomLine> log = data.log();
        int n = Math.min(3, log.size());
        for (int i = 0; i < n; i++) {
            IntercomLine l = log.get(log.size() - n + i);
            String line = l.name() + ": " + l.text();
            fill(x, y + i * 9 - 1, 218, 9, 0x90000000);
            text(font.plainSubstrByWidth(line, (int) (214 / 0.6f)), x + 2, y + i * 9, WHITE, 0.6f);
        }
    }

    private boolean isGuardPeer() {
        return data.peer().startsWith("#guard") || data.peer().equals("#office");
    }

    /** 이 기기가 호출한 대상 이름 ("#guard" / "#office" 는 경비실 / 관리실) */
    private String peerDisplay() {
        if (data.peer().startsWith("#guard:")) return t("guard_label_no", data.peer().substring(7));
        return switch (data.peer()) {
            case "#guard" -> t("guard_label");
            case "#office" -> t("office_label");
            default -> data.peer();
        };
    }

    private String peerName() {
        if (data.outgoing()) return peerDisplay();
        return Intercom.sideName(key()).getString();
    }

    private void unitKey(int idx) {
        if (unitInput.length() >= 14) return;
        if (idx == 9) {
            if (!unitInput.isEmpty() && !unitInput.contains("동") && Character.isDigit(unitInput.charAt(unitInput.length() - 1))) unitInput += "동 ";
        } else if (idx == 11) {
            if (!unitInput.isEmpty() && !unitInput.endsWith("호") && Character.isDigit(unitInput.charAt(unitInput.length() - 1))) unitInput += "호";
        } else {
            if (unitInput.endsWith("호")) return;
            unitInput += idx == 10 ? "0" : String.valueOf(idx + 1);
        }
    }

    private static String backspaceUnit(String s) {
        if (s.endsWith("동 ")) return s.substring(0, s.length() - 2);
        return s.isEmpty() ? s : s.substring(0, s.length() - 1);
    }

    private void dialUnit() {
        String u = unitInput.trim();
        String norm = u.replaceAll("[^0-9A-Za-z가-힣]", "").replace("동", "").replace("호", "");
        String own = data.unit().replaceAll("[^0-9A-Za-z가-힣]", "").replace("동", "").replace("호", "");
        if (norm.isEmpty() || norm.equals(own)) {
            popup = new Popup(Arrays.asList(t("notice.bad_unit").split("\n")), t("ok"), t("cancel"), () -> unitInput = "", null);
            return;
        }
        send(Action.DIAL, u);
    }

    private void drawGuard() {
        CallState cs = callState();
        boolean guardCall = incoming() && key().startsWith("guard:");
        boolean outGuard = data.outgoing() && isGuardPeer();
        boolean ringing = guardCall && cs == CallState.RINGING;
        boolean talking = (guardCall || outGuard) && cs == CallState.CONNECTED;
        boolean dialing = outGuard && cs == CallState.DIALING;
        videoArea(CAM_GUARD, talking || ringing);
        if (ringing) overlayLine(t("call.from_guard", peerName()), YELLOW);
        else if (dialing) overlayLine(t("call.dialing_guard", peerDisplay()), YELLOW);
        else if (talking) {
            overlayLine(t("call.talking_with", peerName(), talkTime()), 0xFF9CF0B0);
            drawLog();
        }
        boolean office = outGuard ? data.peer().equals("#office") : guardOffice;
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        String guardNo = data.setting("guard_no", "");
        button(rx + 2, CY + 10, 71, 24, guardNo.isEmpty() ? t("call.guard_btn") : t("guard_label_no", guardNo), !office && (cs == CallState.IDLE || dialing || talking) || ringing && blink(),
                cs == CallState.IDLE, () -> guardOffice = false);
        button(rx + 2, CY + 38, 71, 24, t("call.office"), office && (cs == CallState.IDLE || dialing || talking), cs == CallState.IDLE, () -> guardOffice = true);
        button(rx + 2, CY + 66, 71, 24, t("call.talk"), talking || dialing, cs == CallState.IDLE || ringing, () -> {
            if (ringing) send(Action.ANSWER);
            else send(Action.CALL_GUARD, guardOffice ? "office" : "guard");
        });
        button(rx + 2, CY + 94, 71, 24, t("call.finish"), false, ringing || talking || dialing, () -> send(Action.HANG_UP));
    }

    private void drawPhone() {
        int lx = CX + 4;
        panel(lx, CY + 4, 222, CH - 30);
        fill(lx + 6, CY + 10, 210, 18, WHITE);
        frame(lx + 6, CY + 10, 210, 18, 0xFF8C939D);
        text(phoneInput.isEmpty() ? t("call.phone_hint") : phoneInput, lx + 10, CY + 15, phoneInput.isEmpty() ? 0xFF9AA1AB : DARK, 0.8f);
        button(lx + 196, CY + 12, 18, 14, "<", false, !phoneInput.isEmpty(), () -> phoneInput = phoneInput.substring(0, phoneInput.length() - 1));
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < 12; i++) {
            String kk = keys[i];
            button(lx + 8 + (i % 3) * 70, CY + 32 + (i / 3) * 29, 64, 25, kk, false, true, () -> {
                if (phoneInput.length() < 15) phoneInput += kk;
            });
        }
        keyInput = c -> {
            if (c.equals("\b")) {
                if (!phoneInput.isEmpty()) phoneInput = phoneInput.substring(0, phoneInput.length() - 1);
            } else if (phoneInput.length() < 15) phoneInput += c;
        };
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        button(rx + 2, CY + 14, 71, 26, t("call.talk"), false, !phoneInput.isEmpty(), () -> info(t("notice.no_line")));
        button(rx + 2, CY + 46, 71, 26, t("call.end"), false, true, () -> phoneInput = "");
    }

    // ------------------------------------------------------------------ 조회

    private void drawInquiry() {
        int tb = tab(Page.INQUIRY);
        if (tb < inquiryScroll) inquiryScroll = tb;
        if (tb >= inquiryScroll + 5) inquiryScroll = tb - 4;
        tabRow(79, 20, 317, "inquiry", INQUIRY_TABS, tb, inquiryScroll, 5, i -> tabs.put(Page.INQUIRY, i));
        switch (INQUIRY_TABS[tb]) {
            case "notice" -> table(new String[]{t("col.no"), t("col.title"), t("col.writer"), t("col.date")}, new float[]{0.1f, 0.45f, 0.2f, 0.25f},
                    List.of(), t("inquiry.notice_empty"));
            case "visitor" -> drawVisitors();
            case "repair" -> drawRepair();
            case "vote" -> table(new String[]{t("col.no"), t("col.title"), t("col.period"), t("col.result")}, new float[]{0.1f, 0.45f, 0.3f, 0.15f},
                    List.of(), t("inquiry.vote_empty"));
            case "fee" -> drawFee();
            case "elevator" -> drawElevator();
            case "cctv" -> drawCctv();
            case "parcel" -> table(new String[]{t("col.date"), t("col.received"), t("col.box")}, new float[]{0.4f, 0.3f, 0.3f},
                    List.of(), t("inquiry.parcel_empty"));
            case "memo" -> drawMemo();
            default -> table(new String[]{t("col.tag"), t("col.detail")}, new float[]{0.7f, 0.3f}, List.of(), t("inquiry.parking_empty"));
        }
    }

    private void table(String[] header, float[] cols, List<String[]> rows, String empty) {
        int x = CX + 4, y = CY + 4, w = CW - 8;
        panel(x, y, w, CH - 8);
        fill(x + 4, y + 4, w - 8, 14, 0xFF59626E);
        float cx = x + 4;
        for (int i = 0; i < header.length; i++) {
            float cw = (w - 8) * cols[i];
            textFit(header[i], cx + cw / 2, y + 8, WHITE, 0.65f, cw - 4);
            cx += cw;
        }
        for (int r = 0; r < 8; r++) fill(x + 4, y + 18 + (r + 1) * 19, w - 8, 1, 0x60808890);
        if (rows.isEmpty()) {
            textC(empty, x + w / 2f, y + 80, DIM, 0.75f);
            return;
        }
        for (int r = 0; r < rows.size() && r < 8; r++) {
            cx = x + 4;
            for (int i = 0; i < header.length; i++) {
                float cw = (w - 8) * cols[i];
                textFit(rows.get(r)[i], cx + cw / 2, y + 25 + r * 19, DARK, 0.65f, cw - 4);
                cx += cw;
            }
        }
    }

    private void drawVisitors() {
        List<MissedCall> v = data.visitors();
        visitorIndex = Math.max(0, Math.min(visitorIndex, v.size() - 1));
        if (v.isEmpty()) {
            videoArea(CAM_NONE, false);
            textC(t("inquiry.visitor_empty"), videoX + videoW / 2f, videoY + 60, DIM, 0.8f);
        } else {
            MissedCall m = v.get(visitorIndex);
            videoArea(m.caller().equals("lobby") ? CAM_LOBBY : CAM_DOOR, true);
            overlayLine(Intercom.sideName(m.caller()).getString(), WHITE);
        }
        int by2 = CY + CH - 22;
        fill(CX + 4, by2, 222, 16, 0xC0343A44);
        if (!v.isEmpty()) {
            String when = DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm:ss")
                    .format(Instant.ofEpochMilli(v.get(visitorIndex).dayTime()).atZone(ZoneId.systemDefault()));
            fill(CX + 8, by2 + 2, 110, 12, 0xFFE8EBEF);
            text(when, CX + 12, by2 + 5, DARK, 0.6f);
            button(CX + 150, by2 + 2, 14, 12, "<", false, visitorIndex > 0, () -> visitorIndex--);
            textC((visitorIndex + 1) + "/" + v.size(), CX + 180, by2 + 5, WHITE, 0.6f);
            button(CX + 196, by2 + 2, 14, 12, ">", false, visitorIndex < v.size() - 1, () -> visitorIndex++);
        }
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        long now = System.currentTimeMillis();
        boolean cDel = now < confirmDeleteUntil, cClr = now < confirmClearUntil;
        button(rx + 2, CY + 14, 71, 26, cDel ? t("ok") : t("inquiry.delete"), cDel, !v.isEmpty(), () -> {
            if (cDel) {
                send(Action.DELETE_VISITOR, String.valueOf(visitorIndex));
                confirmDeleteUntil = 0;
            } else confirmDeleteUntil = now + 3000;
        });
        button(rx + 2, CY + 46, 71, 26, cClr ? t("ok") : t("inquiry.clear"), cClr, !v.isEmpty(), () -> {
            if (cClr) {
                send(Action.CLEAR_VISITORS);
                confirmClearUntil = 0;
            } else confirmClearUntil = now + 3000;
        });
        if (cDel || cClr) text(t("inquiry.confirm_hint"), rx, CY + 80, YELLOW, 0.55f);
    }

    private void drawRepair() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        String[] items = {"elec", "civil", "arch", "comm", "etc", "result"};
        int[] ics = {IC_BULB_OFF, IC_WRENCH, IC_HOME, IC_WIFI, IC_NOTICE, IC_MAGNIFIER};
        for (int i = 0; i < 6; i++) {
            int x = CX + 40 + (i % 3) * 82, y = CY + 14 + (i / 3) * 70;
            boolean sel = repairSel == i;
            if (sel) g.fillGradient(x, y, x + 64, y + 60, 0xFF63C8F7, 0xFF2A93D6);
            else fill(x, y, 64, 60, hover(x, y, 64, 60) ? 0xFFFFFFFF : 0xFFEDEFF2);
            frame(x, y, 64, 60, sel ? 0xFF1A6EA5 : 0xFF9AA1AB);
            icon(ics[i], x + 18, y + 6, 28, sel ? WHITE : 0xFF4A5260);
            textFit(t("repair." + items[i]), x + 32, y + 44, sel ? WHITE : DARK, 0.65f, 60);
            int idx = i;
            hot(x, y, 64, 60, () -> {
                if (idx == 5) info(t("repair.no_result"));
                else repairSel = idx;
            });
        }
        button(CX + CW - 80, CY + CH - 26, 70, 18, t("inquiry.repair"), false, repairSel >= 0 && repairSel < 5, () -> {
            info(t("repair.done", t("repair." + items[repairSel])));
            repairSel = -1;
        });
    }

    private void drawFee() {
        long[] e = data.energy();
        long[] cur = new long[5], prev = new long[5], prev2 = new long[5];
        for (int i = 0; i < 5 && e.length >= 15; i++) {
            cur[i] = e[i];
            prev[i] = e[5 + i];
            prev2[i] = e[10 + i];
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{t("fee.common"), won(35000), won(35000), won(35000)});
        rows.add(new String[]{t("fee.elec"), won(cur[0] / 1000 * 120), won(prev[0] / 1000 * 120), won(prev2[0] / 1000 * 120)});
        rows.add(new String[]{t("fee.water"), won(cur[1] / 1000 * 700 + cur[3] / 1000 * 3000), won(prev[1] / 1000 * 700 + prev[3] / 1000 * 3000),
                won(prev2[1] / 1000 * 700 + prev2[3] / 1000 * 3000)});
        rows.add(new String[]{t("fee.heat"), won(cur[4] / 1000 * 90 + cur[2] / 1000 * 900), won(prev[4] / 1000 * 90 + prev[2] / 1000 * 900),
                won(prev2[4] / 1000 * 90 + prev2[2] / 1000 * 900)});
        rows.add(new String[]{t("fee.etc"), won(0), won(0), won(0)});
        table(new String[]{t("col.item"), t("col.this_month"), t("col.last_month"), t("col.prev_month")}, new float[]{0.31f, 0.23f, 0.23f, 0.23f},
                rows, "");
    }

    private static String won(long v) {
        return String.format("%,d", v);
    }

    private void drawElevator() {
        videoArea(CAM_ELEVATOR, true);
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        button(rx + 14, CY + 22, 47, 40, "▲", false, true, () -> info(t("notice.elevator_called")));
        button(rx + 14, CY + 74, 47, 40, "▼", false, true, () -> info(t("notice.elevator_called")));
    }

    private void drawCctv() {
        videoArea(CAM_CCTV, true);
        String[] cams = {t("cctv.gate"), t("cctv.play1"), t("cctv.play2"), t("cctv.parking")};
        overlayLine(cams[cctvCam], WHITE);
        int rx = CX + 232;
        darkPanel(rx - 4, CY + 4, 83, CH - 30);
        for (int i = 0; i < cams.length; i++) {
            int idx = i;
            button(rx + 2, CY + 12 + i * 28, 71, 24, cams[i], cctvCam == i, true, () -> cctvCam = idx);
        }
    }

    private void drawMemo() {
        table(new String[]{t("col.no"), t("col.memo"), t("col.rec_time")}, new float[]{0.15f, 0.6f, 0.25f}, List.of(), t("inquiry.memo_empty"));
        int y = CY + CH - 26;
        fill(CX + 8, y, CW - 16, 18, 0xC0343A44);
        String[] ctl = {"●", "▶", "■"};
        for (int i = 0; i < 3; i++) button(CX + 120 + i * 26, y + 2, 22, 14, ctl[i], false, true, () -> info(t("notice.no_mic")));
    }

    // ------------------------------------------------------------------ 에너지

    private void drawEnergy() {
        int tb = tab(Page.ENERGY);
        tabRow(79, 20, 317, "energy", ENERGY_TABS, tb, 0, 3, i -> tabs.put(Page.ENERGY, i));
        long[] e = data.energy();
        if (e.length < 15) e = new long[15];
        switch (tb) {
            case 0 -> {
                panel(CX + 4, CY + 4, CW - 8, CH - 8);
                for (int i = 0; i < 5; i++) {
                    int x = CX + 12 + i * 60;
                    fill(x, CY + 12, 54, 26, 0xFF59626E);
                    textC(t("energy." + ENERGY_KEYS[i]), x + 27, CY + 15, WHITE, 0.75f);
                    textC(ENERGY_UNITS[i], x + 27, CY + 27, 0xFFC8D0DA, 0.55f);
                    g.fillGradient(x, CY + 42, x + 54, CY + 160, 0xFFFAFBFC, 0xFFC4CAD2);
                    frame(x, CY + 42, 54, 118, 0xFF8C939D);
                    // 화살표
                    boolean up = energyUp[i];
                    textC(up ? "▲" : "▲", x + 27, CY + 58, up ? 0xFFE5483F : 0xFFB0B6BE, 1.6f);
                    fill(x + 4, CY + 96, 46, 30, 0xFFFFFFFF);
                    frame(x + 4, CY + 96, 46, 30, 0xFF8C939D);
                    long v = e[i] / 1000;
                    textC(String.format("%03d", Math.min(999, v)), x + 27, CY + 104, DARK, 1.5f);
                }
            }
            case 1 -> drawEnergyAverage(e);
            default -> {
                List<String[]> rows = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    rows.add(new String[]{t("energy." + ENERGY_KEYS[i]) + "(" + ENERGY_UNITS[i] + ")", fmt(e[10 + i]), fmt(e[5 + i]), fmt(e[i])});
                }
                table(new String[]{t("col.item"), t("col.prev_month"), t("col.last_month"), t("col.this_month")},
                        new float[]{0.31f, 0.23f, 0.23f, 0.23f}, rows, "");
                text(t("energy.meter_note"), CX + 10, CY + CH - 16, 0xFF3A414C, 0.58f);
            }
        }
    }

    private static String fmt(long milli) {
        return String.format("%.2f", milli / 1000.0);
    }

    private void drawEnergyAverage(long[] e) {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        for (int i = 0; i < 5; i++) {
            int idx = i;
            button(CX + 8, CY + 10 + i * 24, 44, 20, t("energy." + ENERGY_KEYS[i]), energySel == i, true, () -> energySel = idx);
        }
        int gx = CX + 60, gy = CY + 10, gw = 244, gh = 120;
        fill(gx, gy, gw, gh, 0xFFFFFFFF);
        frame(gx, gy, gw, gh, 0xFF8C939D);
        for (int r = 1; r < 5; r++) fill(gx + 1, gy + r * gh / 5, gw - 2, 1, 0xFFE2E5EA);
        int target = parse(data.setting("target_" + energySel, "0"), 0);
        long[] vals = {e[energySel] / 1000, e[5 + energySel] / 1000, e[10 + energySel] / 1000, target};
        String[] labels = {t("energy.this_month"), t("energy.last_month"), t("energy.prev_month"), t("energy.target")};
        int[] colors = {0xFF2E9BDB, 0xFF7FBCE6, 0xFFB8D7EE, 0xFFF0A040};
        long max = 1;
        for (long v : vals) max = Math.max(max, v);
        for (int i = 0; i < 4; i++) {
            int bw = 34, x = gx + 20 + i * 58;
            int h = (int) (vals[i] * (gh - 30) / max);
            fill(x, gy + gh - 16 - h, bw, h, colors[i]);
            textC(String.valueOf(vals[i]), x + bw / 2f, gy + gh - 26 - h, DARK, 0.6f);
            textC(labels[i], x + bw / 2f, gy + gh - 12, DIM, 0.58f);
        }
        int by2 = CY + CH - 30;
        fill(CX + 60, by2, 244, 20, 0xFFE8EBEF);
        text(t("energy.target_line", target, ENERGY_UNITS[energySel], vals[0]), CX + 64, by2 + 7, DARK, 0.6f);
        button(CX + 240, by2 + 2, 62, 16, t("energy.set_target"), false, true, () -> {
            int sel = energySel;
            keypad = new Keypad(t("energy." + ENERGY_KEYS[sel]), false, 6, true,
                    v -> send(Action.SET_SETTING, "target_" + sel + "=" + (v.isEmpty() ? "0" : v)), null);
            keypad.value = target > 0 ? String.valueOf(target) : "";
        });
    }

    // ------------------------------------------------------------------ 설정

    private void onSettingsTab(int idx) {
        draft.clear();
        fieldFocus = 0;
        switch (SETTING_TABS[idx]) {
            case "password" -> {
                if (!pwUnlocked) {
                    keypad = new Keypad(t("settings.pw_title"), true, 8, false, v -> {
                        pendingAuth = v;
                        send(Action.UNLOCK, "pw:" + v);
                    }, () -> tabs.put(Page.SETTINGS, 0));
                }
            }
            case "admin" -> {
                if (!adminUnlocked) {
                    keypad = new Keypad(t("settings.admin_title"), true, 8, false, v -> send(Action.UNLOCK, "admin:" + v),
                            () -> tabs.put(Page.SETTINGS, 0));
                }
            }
            case "sms", "ars" -> {
                if (!data.flag("outing")) info(t("settings.outing_only"));
            }
            case "touch" -> {
                page = Page.TOUCH;
                touchStep = 0;
            }
            default -> {}
        }
    }

    private String dv(String key, String def) {
        return draft.containsKey(key) ? draft.get(key) : data.setting(key, def);
    }

    private void drawSettings() {
        int tb = tab(Page.SETTINGS);
        if (tb < settingScroll) settingScroll = tb;
        if (tb >= settingScroll + 5) settingScroll = tb - 4;
        tabRow(79, 20, 317, "settings", SETTING_TABS, tb, settingScroll, 5, i -> {
            tabs.put(Page.SETTINGS, i);
            onSettingsTab(i);
        });
        switch (SETTING_TABS[tb]) {
            case "sound" -> drawSound();
            case "password" -> drawPassword();
            case "sms" -> drawSms();
            case "ars" -> drawArs();
            case "admin" -> drawAdmin();
            default -> {
                panel(CX + 4, CY + 4, CW - 8, CH - 8);
                button(CX + CW / 2 - 50, CY + 70, 100, 24, t("settings.touch_start"), false, true, () -> {
                    page = Page.TOUCH;
                    touchStep = 0;
                });
            }
        }
    }

    private void checkbox(int x, int y, String label, String key) {
        boolean on = "1".equals(dv(key, "0"));
        fill(x, y, 9, 9, WHITE);
        frame(x, y, 9, 9, 0xFF59626E);
        if (on) text("✔", x + 1, y + 1, BLUE, 0.75f);
        text(label, x + 12, y + 2, DARK, 0.6f);
        hot(x, y - 2, 14 + (int) width(label, 0.6f), 13, () -> draft.put(key, on ? "0" : "1"));
    }

    private void drawSound() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        fill(CX + 10, CY + 10, 30, 14, 0xFF59626E);
        textC(t("settings.notify"), CX + 25, CY + 13, WHITE, 0.65f);
        checkbox(CX + 48, CY + 12, t("settings.notify_notice"), "notify_notice");
        checkbox(CX + 108, CY + 12, t("settings.notify_parking"), "notify_parking");
        checkbox(CX + 168, CY + 12, t("settings.notify_parcel"), "notify_parcel");
        checkbox(CX + 228, CY + 12, t("settings.touch_sound"), "touch_sound");
        String[] keys = {"vol_call", "vol_ring", "vol_system"};
        for (int i = 0; i < 3; i++) {
            int y = CY + 36 + i * 32;
            fill(CX + 10, y, CW - 20, 24, 0xFFE2E5EA);
            frame(CX + 10, y, CW - 20, 24, 0xFFB0B6BE);
            fill(CX + 14, y + 4, 64, 16, 0xFF59626E);
            textC(t("settings." + keys[i]), CX + 46, y + 8, WHITE, 0.65f);
            icon(IC_SPEAKER, CX + 96, y + 5, 14, DARK);
            String k = keys[i];
            slider(CX + 116, y + 6, parse(dv(k, "5"), 5), v -> draft.put(k, String.valueOf(v)));
        }
        button(CX + CW - 120, CY + CH - 28, 52, 18, t("settings.reset"), false, true, () -> {
            draft.clear();
            send(Action.RESET_SOUND);
        });
        button(CX + CW - 62, CY + CH - 28, 52, 18, t("settings.save"), false, true, this::saveDraft);
    }

    private void saveDraft() {
        for (Map.Entry<String, String> en : draft.entrySet()) send(Action.SET_SETTING, en.getKey() + "=" + en.getValue());
        draft.clear();
        info(t("notice.saved"));
    }

    /** 오른쪽 숫자 키패드 (1~9, *, 0, #) */
    private void sideKeypad(int x, int y, Consumer<String> onKey) {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        for (int i = 0; i < 12; i++) {
            String kk = keys[i];
            button(x + (i % 3) * 44, y + (i / 3) * 34, 40, 30, kk, false, true, () -> onKey.accept(kk));
        }
        keyInput = c -> onKey.accept(c);
    }

    private void field(int x, int y, int w, String label, String value, boolean masked, boolean focused, Runnable onFocus) {
        fill(x, y, w, 30, 0xFF59626E);
        text(label, x + 4, y + 3, WHITE, 0.6f);
        fill(x + 3, y + 13, w - 6, 14, focused ? 0xFFFFFFFF : 0xFFE8EBEF);
        frame(x + 3, y + 13, w - 6, 14, focused ? BLUE : 0xFF8C939D);
        String v = masked ? "*".repeat(value.length()) : value;
        text(v + (focused && blink() ? "_" : ""), x + 7, y + 17, DARK, 0.7f);
        hot(x, y, w, 30, onFocus);
    }

    private void drawPassword() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!pwUnlocked) {
            textC(t("settings.pw_locked"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            button(CX + CW / 2 - 40, CY + 90, 80, 20, t("settings.pw_enter"), false, true, () -> onSettingsTab(1));
            return;
        }
        field(CX + 12, CY + 14, 120, t("settings.pw_new"), pw1, true, fieldFocus == 0, () -> fieldFocus = 0);
        field(CX + 12, CY + 52, 120, t("settings.pw_confirm"), pw2, true, fieldFocus == 1, () -> fieldFocus = 1);
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
        button(CX + 12, CY + 92, 56, 20, t("settings.save"), false, true, () -> {
            if (pw1.length() != 4 || !pw1.equals(pw2)) {
                info(t("notice.pw_mismatch"));
                return;
            }
            send(Action.SET_DOOR_PASSWORD, pwAuth + "|" + pw1);
        });
        button(CX + 76, CY + 92, 56, 20, t("settings.edit"), false, true, () -> {
            pw1 = "";
            pw2 = "";
            fieldFocus = 0;
        });
        text(t("settings.pw_note1"), CX + 12, CY + 124, 0xFF3A414C, 0.58f);
        text(t("settings.pw_note2"), CX + 12, CY + 134, 0xFF3A414C, 0.58f);
        text(t(data.flag("pw_default") ? "settings.pw_state_default" : "settings.pw_state_set"), CX + 12, CY + 148, BLUE, 0.6f);
    }

    private void drawSms() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!data.flag("outing")) {
            textC(t("settings.outing_only"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            return;
        }
        String on = dv("sms_on", "");
        for (int i = 0; i < 5; i++) {
            int y = CY + 10 + i * 22, idx = i;
            String k = "sms" + (i + 1);
            fill(CX + 10, y, 100, 18, fieldFocus == i ? WHITE : 0xFFE8EBEF);
            frame(CX + 10, y, 100, 18, fieldFocus == i ? BLUE : 0xFF8C939D);
            text(dv(k, ""), CX + 13, y + 6, DARK, 0.65f);
            hot(CX + 10, y, 100, 18, () -> fieldFocus = idx);
            boolean mms = on.length() > i && on.charAt(i) == '1';
            button(CX + 114, y + 2, 22, 14, "SMS", !mms, true, () -> draft.put("sms_on", setFlag(dv("sms_on", ""), idx, '0')));
            button(CX + 138, y + 2, 22, 14, "MMS", mms, true, () -> draft.put("sms_on", setFlag(dv("sms_on", ""), idx, '1')));
        }
        sideKeypad(CX + 170, CY + 10, k -> typeInto("sms" + (fieldFocus + 1), k, 15));
        button(CX + 10, CY + CH - 30, 50, 18, t("settings.save"), false, true, this::saveDraft);
        button(CX + 66, CY + CH - 30, 50, 18, t("settings.edit"), false, true, () -> draft.put("sms" + (fieldFocus + 1), ""));
    }

    private static String setFlag(String flags, int i, char c) {
        StringBuilder sb = new StringBuilder(flags);
        while (sb.length() <= i) sb.append('0');
        sb.setCharAt(i, c);
        return sb.toString();
    }

    private void typeInto(String key, String k, int max) {
        String cur = dv(key, "");
        if (k.equals("\b")) {
            if (!cur.isEmpty()) draft.put(key, cur.substring(0, cur.length() - 1));
        } else if (k.matches("\\d") && cur.length() < max) draft.put(key, cur + k);
    }

    private void drawArs() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!data.flag("outing")) {
            textC(t("settings.outing_only"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            return;
        }
        fill(CX + 10, CY + 8, 80, 12, 0xFF59626E);
        text(t("settings.ars_phones"), CX + 13, CY + 11, WHITE, 0.6f);
        for (int i = 0; i < 3; i++) {
            int y = CY + 22 + i * 18, idx = i;
            fill(CX + 10, y, 80, 15, fieldFocus == i ? WHITE : 0xFFE8EBEF);
            frame(CX + 10, y, 80, 15, fieldFocus == i ? BLUE : 0xFF8C939D);
            text(dv("ars_phone" + (i + 1), ""), CX + 13, y + 5, DARK, 0.6f);
            hot(CX + 10, y, 80, 15, () -> fieldFocus = idx);
        }
        fill(CX + 10, CY + 82, 80, 12, 0xFF59626E);
        text(t("settings.ars_pw"), CX + 13, CY + 85, WHITE, 0.6f);
        fill(CX + 10, CY + 96, 80, 15, fieldFocus == 3 ? WHITE : 0xFFE8EBEF);
        frame(CX + 10, CY + 96, 80, 15, fieldFocus == 3 ? BLUE : 0xFF8C939D);
        text(dv("ars_pw", "FFFF"), CX + 13, CY + 101, DARK, 0.6f);
        hot(CX + 10, CY + 96, 80, 15, () -> fieldFocus = 3);

        text(t("settings.ars_auto"), CX + 98, CY + 10, DARK, 0.6f);
        boolean auto = "1".equals(dv("ars_auto", "1"));
        button(CX + 98, CY + 20, 30, 13, t("settings.ars_out"), auto, true, () -> draft.put("ars_auto", "1"));
        button(CX + 130, CY + 20, 30, 13, t("settings.ars_off"), !auto, true, () -> draft.put("ars_auto", "0"));
        checkbox(CX + 98, CY + 44, t("settings.ars_use"), "ars_use");
        text(t("settings.ars_rings"), CX + 98, CY + 64, DARK, 0.6f);
        int rings = parse(dv("ars_rings", "10"), 10);
        button(CX + 98, CY + 74, 14, 14, "−", false, rings > 1, () -> draft.put("ars_rings", String.valueOf(rings - 1)));
        textC(rings + t("settings.times"), CX + 125, CY + 78, DARK, 0.65f);
        button(CX + 140, CY + 74, 14, 14, "+", false, rings < 20, () -> draft.put("ars_rings", String.valueOf(rings + 1)));

        sideKeypad(CX + 170, CY + 10, k -> {
            if (fieldFocus == 3) {
                String cur = dv("ars_pw", "FFFF");
                if (k.equals("\b")) draft.put("ars_pw", cur.isEmpty() ? cur : cur.substring(0, cur.length() - 1));
                else if (k.matches("\\d") && cur.length() < 8) draft.put("ars_pw", (cur.equals("FFFF") ? "" : cur) + k);
            } else typeInto("ars_phone" + (fieldFocus + 1), k, 15);
        });
        button(CX + 10, CY + CH - 30, 50, 18, t("settings.save"), false, true, this::saveDraft);
        button(CX + 66, CY + CH - 30, 50, 18, t("settings.edit"), false, true, () -> {
            if (fieldFocus == 3) draft.put("ars_pw", "");
            else draft.put("ars_phone" + (fieldFocus + 1), "");
        });
    }

    private String adminGuard;

    private void drawAdmin() {
        panel(CX + 4, CY + 4, CW - 8, CH - 8);
        if (!adminUnlocked) {
            textC(t("settings.admin_locked"), CX + CW / 2f, CY + 70, DIM, 0.75f);
            button(CX + CW / 2 - 40, CY + 90, 80, 20, t("settings.pw_enter"), false, true, () -> onSettingsTab(5));
            return;
        }
        if (adminGuard == null) adminGuard = data.setting("guard_no", "");
        // 세대 번호
        text(t("settings.admin_unit"), CX + 12, CY + 8, DARK, 0.65f);
        boolean f0 = fieldFocus != 1;
        fill(CX + 12, CY + 18, 140, 16, WHITE);
        frame(CX + 12, CY + 18, 140, 16, f0 ? BLUE : 0xFF8C939D);
        text(adminUnit + (f0 && blink() ? "_" : ""), CX + 16, CY + 22, DARK, 0.72f);
        hot(CX + 12, CY + 18, 140, 16, () -> fieldFocus = 0);
        text(t("settings.admin_current", data.unit().isEmpty() ? "-" : data.unit()), CX + 12, CY + 37, DIM, 0.58f);
        button(CX + 12, CY + 46, 50, 15, t("settings.save"), false, true, () -> send(Action.SET_UNIT, adminUnit.trim()));
        button(CX + 66, CY + 46, 50, 15, t("settings.edit"), false, true, () -> {
            adminUnit = "";
            fieldFocus = 0;
        });
        // 호출 경비실 번호
        text(t("settings.admin_guard"), CX + 12, CY + 68, DARK, 0.58f);
        boolean f1 = fieldFocus == 1;
        fill(CX + 12, CY + 78, 140, 16, WHITE);
        frame(CX + 12, CY + 78, 140, 16, f1 ? BLUE : 0xFF8C939D);
        text(adminGuard + (f1 && blink() ? "_" : ""), CX + 16, CY + 82, DARK, 0.72f);
        hot(CX + 12, CY + 78, 140, 16, () -> fieldFocus = 1);
        String curGuard = data.setting("guard_no", "");
        text(t("settings.admin_guard_current", curGuard.isEmpty() ? t("settings.all_guards") : t("guard_label_no", curGuard)), CX + 12, CY + 97, DIM, 0.58f);
        button(CX + 12, CY + 106, 50, 15, t("settings.save"), false, true, () -> {
            send(Action.SET_SETTING, "guard_no=" + adminGuard);
            info(t("notice.saved"));
        });
        button(CX + 66, CY + 106, 50, 15, t("settings.edit"), false, true, () -> {
            adminGuard = "";
            fieldFocus = 1;
        });

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", t("call.dong"), "0", t("call.ho")};
        for (int i = 0; i < 12; i++) {
            int idx = i;
            boolean enabled = fieldFocus != 1 || (i != 9 && i != 11);
            button(CX + 168 + (i % 3) * 44, CY + 10 + (i / 3) * 34, 40, 30, keys[i], false, enabled, () -> adminKey(idx));
        }
        keyInput = c -> {
            if (fieldFocus == 1) {
                if (c.equals("\b")) adminGuard = adminGuard.isEmpty() ? "" : adminGuard.substring(0, adminGuard.length() - 1);
                else if (c.matches("\\d") && adminGuard.length() < 6) adminGuard += c;
            } else if (c.equals("\b")) adminUnit = backspaceUnit(adminUnit);
            else if (c.matches("\\d") && adminUnit.length() < 14) adminUnit += c;
        };
        text(t("settings.admin_hint"), CX + 12, CY + 128, 0xFF3A414C, 0.55f);
        text("Model : KOCOM KHN-893N (8Type)  ·  S/W Ver : 3.2.15", CX + 12, CY + 142, DIM, 0.55f);
        text(t("settings.devices", data.devices().size()) + "  ·  " + t("settings.visitors", data.visitors().size()), CX + 12, CY + 152, DIM, 0.55f);
    }

    private void adminKey(int idx) {
        if (fieldFocus == 1) {
            if (idx == 9 || idx == 11 || adminGuard.length() >= 6) return;
            adminGuard += idx == 10 ? "0" : String.valueOf(idx + 1);
            return;
        }
        String saved = unitInput;
        unitInput = adminUnit;
        unitKey(idx);
        adminUnit = unitInput;
        unitInput = saved;
    }

    // ------------------------------------------------------------------ 터치보정

    private static final int[][] TOUCH_POINTS = {{30, 30}, {370, 30}, {370, 195}, {30, 195}, {200, 112}};

    private void drawTouch() {
        fill(0, 0, W, H, 0xFFFFFFFF);
        textC(t("touch.line1"), W / 2f, 8, DARK, 0.65f);
        textC(t("touch.line2"), W / 2f, 18, DARK, 0.65f);
        int[] p = TOUCH_POINTS[Math.min(touchStep, 4)];
        fill(p[0] - 10, p[1], 21, 1, DARK);
        fill(p[0], p[1] - 10, 1, 21, DARK);
        hot(p[0] - 12, p[1] - 12, 25, 25, () -> {
            touchStep++;
            if (touchStep >= TOUCH_POINTS.length) {
                page = Page.SETTINGS;
                tabs.put(Page.SETTINGS, 0);
                info(t("notice.touch_done"));
            }
        });
        textC((touchStep + 1) + " / " + TOUCH_POINTS.length, W / 2f, H - 14, DIM, 0.6f);
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
        if (p.cancel == null) {
            button(x + w / 2 - 30, y + h - 20, 60, 15, p.ok, false, true, () -> {
                popup = null;
                if (p.onOk != null) p.onOk.run();
            });
        } else {
            button(x + w / 2 - 64, y + h - 20, 60, 15, p.ok, false, true, () -> {
                popup = null;
                if (p.onOk != null) p.onOk.run();
            });
            button(x + w / 2 + 4, y + h - 20, 60, 15, p.cancel, false, true, () -> {
                popup = null;
                if (p.onCancel != null) p.onCancel.run();
            });
        }
        keyInput = null;
    }

    private void drawKeypad() {
        hots.clear();
        fill(0, 0, W, H, 0x80000000);
        Keypad kp = keypad;
        if (kp.energyStyle) {
            // 설명서 p28: 목표값 설정 (0~9 두 줄 + 저장 / 취소)
            int x = 60, y = 50, w = 280, h = 120;
            fill(x, y, w, h, 0xFF3A414C);
            frame(x, y, w, h, 0xFF1E2329);
            text("⚡ " + kp.title, x + 12, y + 14, WHITE, 1.0f);
            fill(x + 110, y + 10, 150, 20, WHITE);
            text(kp.value, x + 116, y + 16, DARK, 0.9f);
            for (int i = 0; i < 10; i++) {
                String d = String.valueOf(i);
                button(x + 10 + (i % 5) * 44, y + 44 + (i / 5) * 34, 40, 30, d, false, true, () -> {
                    if (kp.value.length() < kp.maxLen) kp.value += d;
                });
            }
            button(x + 230, y + 44, 40, 30, t("settings.save"), false, true, () -> {
                keypad = null;
                kp.onOk.accept(kp.value);
            });
            button(x + 230, y + 78, 40, 30, t("cancel"), false, true, () -> {
                keypad = null;
                if (kp.onCancel != null) kp.onCancel.run();
            });
        } else {
            // 설명서 p31: 비밀번호 설정 (1~9, *, 0, # + 확인 / 취소)
            int x = 130, y = 14, w = 140, h = 198;
            fill(x, y, w, h, 0xFF4A515C);
            frame(x, y, w, h, 0xFF1E2329);
            textC(kp.title, x + w / 2f, y + 6, WHITE, 0.8f);
            fill(x + 10, y + 18, w - 20, 16, WHITE);
            textC(kp.masked ? "*".repeat(kp.value.length()) : kp.value, x + w / 2f, y + 23, DARK, 0.9f);
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
            for (int i = 0; i < 12; i++) {
                String d = keys[i];
                button(x + 10 + (i % 3) * 41, y + 40 + (i / 3) * 34, 38, 30, d, false, true, () -> {
                    if (kp.value.length() < kp.maxLen) kp.value += d;
                });
            }
            button(x + 10, y + h - 22, 58, 16, t("ok"), false, true, () -> {
                String v = kp.value;
                kp.value = "";
                kp.onOk.accept(v);
            });
            button(x + w - 68, y + h - 22, 58, 16, t("cancel"), false, true, () -> {
                keypad = null;
                if (kp.onCancel != null) kp.onCancel.run();
            });
        }
        keyInput = c -> {
            if (c.equals("\b")) {
                if (!kp.value.isEmpty()) kp.value = kp.value.substring(0, kp.value.length() - 1);
            } else if (kp.value.length() < kp.maxLen && c.matches("\\d")) kp.value += c;
        };
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ 본체 버튼

    private void physicalButton(int i) {
        CallState cs = callState();
        switch (i) {
            case 0 -> send(Action.EMERGENCY);
            case 1 -> send(Action.OUTING);
            case 2 -> {
                page = Page.CALL;
                tabs.put(Page.CALL, 2);
                if (cs == CallState.RINGING && incoming() && key().startsWith("guard:")) send(Action.ANSWER);
                else if (cs == CallState.IDLE) send(Action.CALL_GUARD, "guard");
            }
            case 3 -> {
                if (cs == CallState.RINGING && incoming()) {
                    page = Page.CALL;
                    send(Action.ANSWER);
                } else if (cs != CallState.IDLE) send(Action.HANG_UP);
                else {
                    page = Page.CALL;
                    tabs.put(Page.CALL, 0);
                    monitorUntil = System.currentTimeMillis() + 30_000;
                }
            }
            default -> {
                if (cs == CallState.CONNECTED && incoming() && !Intercom.isReceiverKey(key())) send(Action.OPEN_DOOR);
            }
        }
    }

    // ------------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        double ux = (mx - bx) / s, uy = (my - by) / s;
        // 본체 버튼
        if (ux >= BUTTON_X1 - 4 && ux <= BUTTON_X2 + 4) {
            for (int i = 0; i < 5; i++) {
                if (uy >= BUTTON_Y[i] - 10 && uy <= BUTTON_Y[i] + 18) {
                    click();
                    physicalButton(i);
                    return true;
                }
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

    private void click() {
        if (!"1".equals(data.setting("touch_sound", "1"))) return;
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
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && keypad != null) {
            Keypad kp = keypad;
            if (kp.energyStyle) keypad = null;
            String v = kp.value;
            kp.value = "";
            kp.onOk.accept(v);
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
