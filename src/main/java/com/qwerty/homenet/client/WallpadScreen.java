package com.qwerty.homenet.client;

import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.blockentity.ReceiverBlockEntity.Action;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.CallState;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.intercom.MissedCall;
import com.qwerty.homenet.network.DeviceEntry;
import com.qwerty.homenet.network.ModNetwork;
import com.qwerty.homenet.network.WallpadActionPacket;
import com.qwerty.homenet.network.WallpadDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 월패드 화면.
 * [홈] 연결된 기기 켜기/끄기, 일괄소등
 * [인터폰] 호출 응답 / 문열기 / 거절 / 통화 메시지 / 부재중 기록
 * [설정] 세대 번호
 */
public class WallpadScreen extends HomeNetScreen implements ReceiverScreen {
    private enum Tab { HOME, INTERCOM, SETTINGS }

    private static final int PER_PAGE = 8;

    private final BlockPos pos;
    private WallpadDataPacket data;
    private Tab tab = Tab.HOME;
    private int page;
    private int refreshTimer;

    private String unitDraft;
    private String msgDraft = "";
    private EditBox unitBox;
    private EditBox pwBox;
    private String pwDraft = "";
    private EditBox msgBox;
    private EditBox dialBox;
    private String dialDraft = "";
    private Component notice;
    private long noticeUntil;

    public WallpadScreen(WallpadDataPacket data) {
        super(Component.translatable("block.qwertys_homenet." + DeviceRegistry.Kind.byId(data.kind()).key()), 280, 196);
        this.pos = data.pos();
        this.data = data;
        this.unitDraft = data.unit();
        if (callState() != CallState.IDLE || !isWallpad()) tab = Tab.INTERCOM;
    }

    /** 월패드만 기기 제어(홈) 탭이 있음 */
    private boolean isWallpad() {
        return DeviceRegistry.Kind.byId(data.kind()) == DeviceRegistry.Kind.WALLPAD;
    }

    private boolean isVideo() {
        DeviceRegistry.Kind k = DeviceRegistry.Kind.byId(data.kind());
        return k == DeviceRegistry.Kind.WALLPAD || k == DeviceRegistry.Kind.VIDEO_PHONE || k == DeviceRegistry.Kind.GUARD_CONSOLE;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    private CallState callState() {
        return CallState.byId(data.callState());
    }

    @Override
    public void update(WallpadDataPacket newData) {
        CallState before = callState();
        boolean unitChanged = !newData.unit().equals(data.unit());
        boolean msgFocused = msgBox != null && msgBox.isFocused();
        boolean unitFocused = unitBox != null && unitBox.isFocused();
        boolean dialFocused = dialBox != null && dialBox.isFocused();
        if (!newData.notice().isEmpty()) {
            notice = Component.translatable("gui.qwertys_homenet.wp.notice." + newData.notice());
            noticeUntil = System.currentTimeMillis() + 4000;
        }
        this.data = newData;
        if (unitChanged && !unitFocused) unitDraft = newData.unit();
        if (callState() == CallState.RINGING && before != CallState.RINGING) tab = Tab.INTERCOM;
        rebuildWidgets();
        // 갱신 중에도 입력하던 칸의 포커스 유지
        if (msgFocused && msgBox != null) setFocused(msgBox);
        if (unitFocused && unitBox != null) setFocused(unitBox);
        if (dialFocused && dialBox != null) setFocused(dialBox);
    }

    private boolean isGuard() {
        return DeviceRegistry.Kind.byId(data.kind()) == DeviceRegistry.Kind.GUARD_CONSOLE;
    }

    /** 걸려온 호출이 다른 세대·경비실(문이 없음)에서 온 것인지 */
    private boolean fromReceiver() {
        return data.outgoing() || Intercom.isReceiverKey(data.callerKey());
    }

    private Component peerName() {
        if (data.peer().startsWith("#guard:")) return Component.translatable("caller.qwertys_homenet.guard_no", data.peer().substring(7));
        return switch (data.peer()) {
            case "#guard" -> Component.translatable("caller.qwertys_homenet.guard");
            case "#office" -> Component.translatable("caller.qwertys_homenet.office");
            default -> Component.literal(data.peer());
        };
    }

    private void dial() {
        String u = dialDraft == null ? "" : dialDraft.trim();
        if (u.isEmpty()) return;
        send(Action.DIAL, BlockPos.ZERO, u);
    }

    private void send(Action action) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, action));
    }

    private void send(Action action, BlockPos target, String text) {
        ModNetwork.sendToServer(new WallpadActionPacket(pos, action.ordinal(), target, text));
    }

    // ------------------------------------------------------------------ 위젯 구성

    @Override
    protected void init() {
        super.init();
        unitBox = null;
        pwBox = null;
        msgBox = null;
        dialBox = null;

        // 탭
        Tab[] tabs = isWallpad() ? Tab.values() : new Tab[]{Tab.INTERCOM, Tab.SETTINGS};
        if (!isWallpad() && tab == Tab.HOME) tab = Tab.INTERCOM;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            MutableComponent label = tr("tab." + t.name().toLowerCase(java.util.Locale.ROOT));
            if (t == Tab.INTERCOM && (callState() != CallState.IDLE || !data.missed().isEmpty())) {
                label = label.append(Component.literal(" ●").withStyle(callState() == CallState.RINGING ? ChatFormatting.RED : ChatFormatting.YELLOW));
            }
            Button b = Button.builder(label, btn -> {
                tab = t;
                rebuildWidgets();
            }).bounds(left + 10 + i * 88, top + 25, 84, 18).build();
            b.active = tab != t;
            addRenderableWidget(b);
        }

        switch (tab) {
            case HOME -> initHome();
            case INTERCOM -> initIntercom();
            case SETTINGS -> initSettings();
        }
    }

    private void initHome() {
        List<DeviceEntry> devices = data.devices();
        int pages = Math.max(1, (devices.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= devices.size()) break;
            DeviceEntry e = devices.get(idx);
            int col = i % 2, row = i / 2;
            Button b = Button.builder(deviceLabel(e), btn -> send(Action.TOGGLE, e.pos(), ""))
                    .bounds(left + 10 + col * 132, top + 50 + row * 24, 128, 20).build();
            b.active = e.online();
            addRenderableWidget(b);
        }

        int by = top + panelH - 30;
        Button prev = Button.builder(Component.literal("◀"), b -> { page--; rebuildWidgets(); })
                .bounds(left + 10, by, 20, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal("▶"), b -> { page++; rebuildWidgets(); })
                .bounds(left + 66, by, 20, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(tr("all_lights_off"), b -> send(Action.ALL_LIGHTS_OFF))
                .bounds(left + panelW - 180, by, 84, 20).build());
        addRenderableWidget(Button.builder(tr("all_off"), b -> send(Action.ALL_OFF))
                .bounds(left + panelW - 92, by, 82, 20).build());
    }

    private Component deviceLabel(DeviceEntry e) {
        if (!e.online()) {
            return Component.literal("○ ").append(tr("offline")).withStyle(ChatFormatting.DARK_GRAY);
        }
        DeviceType type = DeviceType.byId(e.type());
        Component name = e.name().isEmpty() ? type.displayName() : Component.literal(e.name());
        return Component.literal(e.on() ? "● " : "○ ").withStyle(e.on() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .append(name.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                .append(type.stateName(e.on()).copy().withStyle(e.on() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    private void initIntercom() {
        int by = top + panelH - 30;
        switch (callState()) {
            case RINGING -> {
                addRenderableWidget(Button.builder(tr("answer").withStyle(ChatFormatting.GREEN), b -> send(Action.ANSWER))
                        .bounds(left + 10, by, 84, 20).build());
                Button open = Button.builder(tr("open_door").withStyle(ChatFormatting.AQUA), b -> send(Action.OPEN_DOOR))
                        .bounds(left + 98, by, 84, 20).build();
                open.active = !fromReceiver();
                addRenderableWidget(open);
                addRenderableWidget(Button.builder(tr("reject").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP))
                        .bounds(left + 186, by, 84, 20).build());
            }
            case DIALING -> addRenderableWidget(Button.builder(tr("cancel_call").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP))
                    .bounds(left + 10, by, 260, 20).build());
            case CONNECTED -> {
                msgBox = new EditBox(font, left + 11, by - 25, 186, 18, tr("message"));
                msgBox.setMaxLength(IntercomLine.MAX_TEXT);
                msgBox.setValue(msgDraft);
                msgBox.setResponder(s -> msgDraft = s);
                msgBox.setHint(tr("message_hint"));
                addRenderableWidget(msgBox);
                addRenderableWidget(Button.builder(tr("send"), b -> sendMessage())
                        .bounds(left + 202, by - 26, 68, 20).build());
                Button open = Button.builder(tr("open_door").withStyle(ChatFormatting.AQUA), b -> send(Action.OPEN_DOOR))
                        .bounds(left + 10, by, 128, 20).build();
                open.active = !fromReceiver();
                addRenderableWidget(open);
                addRenderableWidget(Button.builder(tr("hang_up").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP))
                        .bounds(left + 142, by, 128, 20).build());
            }
            default -> {
                // 세대 번호로 호출 (경비실기 → 세대, 비디오폰/인터폰 → 다른 세대)
                dialBox = new EditBox(font, left + 11, by - 25, 150, 18, tr("dial"));
                dialBox.setMaxLength(16);
                dialBox.setValue(dialDraft);
                dialBox.setResponder(v -> dialDraft = v);
                dialBox.setHint(tr("dial_hint"));
                addRenderableWidget(dialBox);
                addRenderableWidget(Button.builder(tr("dial_call").withStyle(ChatFormatting.GREEN), b -> dial())
                        .bounds(left + 166, by - 26, 104, 20).build());
                if (isGuard()) {
                    addRenderableWidget(Button.builder(tr("call_other_guard"), b -> send(Action.CALL_GUARD, BlockPos.ZERO, "guard"))
                            .bounds(left + 10, by, 84, 20).build());
                } else {
                    addRenderableWidget(Button.builder(tr("call_guard").withStyle(ChatFormatting.AQUA), b -> send(Action.CALL_GUARD, BlockPos.ZERO, "guard"))
                            .bounds(left + 10, by, 84, 20).build());
                    addRenderableWidget(Button.builder(tr("call_office"), b -> send(Action.CALL_GUARD, BlockPos.ZERO, "office"))
                            .bounds(left + 98, by, 72, 20).build());
                }
                Button clear = Button.builder(tr("clear_missed"), b -> send(Action.CLEAR_MISSED))
                        .bounds(left + panelW - 96, by, 86, 20).build();
                clear.active = !data.missed().isEmpty();
                addRenderableWidget(clear);
            }
        }
    }

    private void sendMessage() {
        String text = Intercom.sanitize(msgDraft, IntercomLine.MAX_TEXT);
        if (text.isEmpty()) return;
        send(Action.SEND_MESSAGE, BlockPos.ZERO, text);
        msgDraft = "";
        if (msgBox != null) msgBox.setValue("");
    }

    private void initSettings() {
        unitBox = new EditBox(font, left + 12, top + 66, 170, 18, tr("unit"));
        unitBox.setMaxLength(16);
        unitBox.setValue(unitDraft == null ? "" : unitDraft);
        unitBox.setResponder(s -> unitDraft = s);
        unitBox.setHint(tr("unit_hint"));
        addRenderableWidget(unitBox);
        addRenderableWidget(Button.builder(tr("save"), b -> send(Action.SET_UNIT, BlockPos.ZERO, unitDraft == null ? "" : unitDraft))
                .bounds(left + 190, top + 65, 80, 20).build());

        // 공동현관 세대 비밀번호 (4자리 숫자, 비우고 저장하면 사용 안 함)
        pwBox = new EditBox(font, left + 12, top + 104, 170, 18, tr("door_pw"));
        pwBox.setMaxLength(4);
        pwBox.setFilter(s -> s.matches("\\d*"));
        pwBox.setValue(pwDraft);
        pwBox.setResponder(s -> pwDraft = s);
        pwBox.setHint(data.hasDoorPassword() ? tr("door_pw_hint_set") : tr("door_pw_hint"));
        addRenderableWidget(pwBox);
        addRenderableWidget(Button.builder(tr("save"), b -> savePassword())
                .bounds(left + 190, top + 103, 80, 20).build());
    }

    private void savePassword() {
        send(Action.SET_DOOR_PASSWORD, BlockPos.ZERO, pwDraft);
        pwDraft = "";
        if (pwBox != null) pwBox.setValue("");
    }

    // ------------------------------------------------------------------ 그리기

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Component header = data.unit().isEmpty() ? tr("unit_unset") : tr("unit_header", data.unit());
        drawDevice(g, header, clockAndWeather());

        switch (tab) {
            case HOME -> renderHome(g);
            case INTERCOM -> renderIntercom(g);
            case SETTINGS -> renderSettings(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHome(GuiGraphics g) {
        List<DeviceEntry> devices = data.devices();
        if (devices.isEmpty()) {
            g.drawCenteredString(font, tr("no_devices"), left + panelW / 2, top + 80, TEXT_DIM);
            g.drawCenteredString(font, tr("no_devices_hint"), left + panelW / 2, top + 94, TEXT_DIM);
        }
        int pages = Math.max(1, (devices.size() + PER_PAGE - 1) / PER_PAGE);
        g.drawCenteredString(font, (page + 1) + "/" + pages, left + 48, top + panelH - 24, TEXT_DIM);
    }

    private void renderIntercom(GuiGraphics g) {
        int cx = left + panelW / 2;
        Component caller = data.outgoing() ? peerName() : Intercom.sideName(data.callerKey());
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            g.drawCenteredString(font, notice, cx, top + 26 + 18 + 4, TEXT_WARN);
        }
        switch (callState()) {
            case DIALING -> {
                boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
                g.fill(left + 20, top + 60, left + panelW - 20, top + 120, blink ? 0xFF2A3F5A : 0xFF1F3048);
                g.drawCenteredString(font, tr("dialing_title"), cx, top + 72, TEXT_WARN);
                g.drawCenteredString(font, tr("dialing_to", caller), cx, top + 92, TEXT);
            }
            case RINGING -> {
                boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
                g.fill(left + 20, top + 60, left + panelW - 20, top + 120, blink ? 0xFF2A3F5A : 0xFF1F3048);
                int tx = cx;
                if (isVideo() && !fromReceiver()) {
                    drawCameraView(g, left + panelW - 86, top + 68);
                    tx = left + (panelW - 92) / 2 + 10;
                }
                g.drawCenteredString(font, tr("ringing_title"), tx, top + 72, TEXT_WARN);
                g.drawCenteredString(font, tr("ringing_from", caller), tx, top + 92, TEXT);
                g.drawCenteredString(font, tr("ringing_hint"), cx, top + 132, TEXT_DIM);
            }
            case CONNECTED -> {
                g.drawString(font, tr("connected_with", caller), left + 12, top + 50, TEXT_OK, false);
                renderLog(g, data.log(), left + 12, top + 64, panelW - 24, 6);
            }
            default -> {
                g.drawString(font, tr("idle"), left + 12, top + 50, TEXT_DIM, false);
                g.drawString(font, tr("missed_title"), left + 12, top + 66, TEXT, false);
                List<MissedCall> missed = data.missed();
                if (missed.isEmpty()) {
                    g.drawString(font, tr("missed_none"), left + 20, top + 78, TEXT_DIM, false);
                }
                g.drawString(font, isGuard() ? tr("dial_label_guard") : tr("dial_label"), left + 12, top + panelH - 66, TEXT, false);
                for (int i = 0; i < Math.min(missed.size(), 4); i++) {
                    MissedCall m = missed.get(i);
                    Component line = Component.literal("• ").append(Intercom.sideName(m.caller()))
                            .append("   ").append(tr("day_time", day(m.dayTime()), clock(m.dayTime())));
                    g.drawString(font, line, left + 20, top + 78 + i * 11, m.caller().startsWith("emergency:") ? 0xFFFF6060 : TEXT_WARN, false);
                }
            }
        }
    }

    /** 현관 카메라 화면 (사람 실루엣) */
    private void drawCameraView(GuiGraphics g, int x, int y) {
        int w = 60, h = 44;
        g.fill(x, y, x + w, y + h, 0xFF101418);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF5A6470, 0xFF2A3038);
        // 머리와 어깨
        g.fill(x + 24, y + 9, x + 36, y + 22, 0xFF1A1E24);
        g.fill(x + 15, y + 24, x + 45, y + h - 1, 0xFF1A1E24);
        g.fill(x + 26, y + 22, x + 34, y + 25, 0xFF1A1E24);
        boolean rec = (System.currentTimeMillis() / 500) % 2 == 0;
        if (rec) g.fill(x + 3, y + 3, x + 6, y + 6, 0xFFE04040);
        g.drawString(font, "CAM", x + 8, y + 2, 0xFFCCD4E0, false);
    }

    static void renderLog(GuiGraphics g, List<IntercomLine> log, int x, int y, int w, int maxLines,
                          net.minecraft.client.gui.Font font) {
        int start = Math.max(0, log.size() - maxLines);
        for (int i = start; i < log.size(); i++) {
            IntercomLine l = log.get(i);
            Component c = Component.literal("[").append(Intercom.sideName(l.side())).append("] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(l.name() + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(l.text()).withStyle(ChatFormatting.WHITE));
            g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(c, w)), x, y + (i - start) * 11, TEXT, false);
        }
    }

    private void renderLog(GuiGraphics g, List<IntercomLine> log, int x, int y, int w, int maxLines) {
        if (log.isEmpty()) {
            g.drawString(font, tr("log_empty"), x, y, TEXT_DIM, false);
            return;
        }
        renderLog(g, log, x, y, w, maxLines, font);
    }

    private void renderSettings(GuiGraphics g) {
        g.drawString(font, tr("unit_label"), left + 12, top + 54, TEXT, false);
        g.drawString(font, tr("door_pw_label"), left + 12, top + 92, TEXT, false);
        g.drawString(font, tr("settings_help_1"), left + 12, top + 130, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_2"), left + 12, top + 141, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_3"), left + 12, top + 152, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_4"), left + 12, top + 163, TEXT_DIM, false);
        g.drawString(font, tr("device_count", data.devices().size()), left + 12, top + 177, TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ 입력 / 틱

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (msgBox != null && msgBox.isFocused()) {
                sendMessage();
                return true;
            }
            if (dialBox != null && dialBox.isFocused()) {
                dial();
                return true;
            }
            if (pwBox != null && pwBox.isFocused()) {
                savePassword();
                return true;
            }
            if (unitBox != null && unitBox.isFocused()) {
                send(Action.SET_UNIT, BlockPos.ZERO, unitDraft == null ? "" : unitDraft);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (msgBox != null) msgBox.tick();
        if (dialBox != null) dialBox.tick();
        if (unitBox != null) unitBox.tick();
        if (pwBox != null) pwBox.tick();
        // 다른 플레이어가 바꾼 기기 상태 반영을 위해 2초마다 갱신 요청
        if (tab == Tab.HOME && ++refreshTimer >= 40) {
            refreshTimer = 0;
            send(Action.REFRESH);
        }
    }
}
