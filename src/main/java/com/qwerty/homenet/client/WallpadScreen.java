package com.qwerty.homenet.client;

import com.qwerty.homenet.block.DeviceType;
import com.qwerty.homenet.blockentity.WallpadBlockEntity.Action;
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
public class WallpadScreen extends HomeNetScreen {
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
    private EditBox msgBox;

    public WallpadScreen(WallpadDataPacket data) {
        super(Component.translatable("block.qwertys_homenet.wallpad"), 280, 196);
        this.pos = data.pos();
        this.data = data;
        this.unitDraft = data.unit();
        if (callState() != CallState.IDLE) tab = Tab.INTERCOM;
    }

    public BlockPos getPos() {
        return pos;
    }

    private CallState callState() {
        return CallState.byId(data.callState());
    }

    public void update(WallpadDataPacket newData) {
        CallState before = callState();
        boolean unitChanged = !newData.unit().equals(data.unit());
        boolean msgFocused = msgBox != null && msgBox.isFocused();
        boolean unitFocused = unitBox != null && unitBox.isFocused();
        this.data = newData;
        if (unitChanged && !unitFocused) unitDraft = newData.unit();
        if (callState() == CallState.RINGING && before != CallState.RINGING) tab = Tab.INTERCOM;
        rebuildWidgets();
        // 갱신 중에도 입력하던 칸의 포커스 유지
        if (msgFocused && msgBox != null) setFocused(msgBox);
        if (unitFocused && unitBox != null) setFocused(unitBox);
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
        msgBox = null;

        // 탭
        Tab[] tabs = Tab.values();
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
                addRenderableWidget(Button.builder(tr("open_door").withStyle(ChatFormatting.AQUA), b -> send(Action.OPEN_DOOR))
                        .bounds(left + 98, by, 84, 20).build());
                addRenderableWidget(Button.builder(tr("reject").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP))
                        .bounds(left + 186, by, 84, 20).build());
            }
            case CONNECTED -> {
                msgBox = new EditBox(font, left + 11, by - 25, 186, 18, tr("message"));
                msgBox.setMaxLength(IntercomLine.MAX_TEXT);
                msgBox.setValue(msgDraft);
                msgBox.setResponder(s -> msgDraft = s);
                msgBox.setHint(tr("message_hint"));
                addRenderableWidget(msgBox);
                addRenderableWidget(Button.builder(tr("send"), b -> sendMessage())
                        .bounds(left + 202, by - 26, 68, 20).build());
                addRenderableWidget(Button.builder(tr("open_door").withStyle(ChatFormatting.AQUA), b -> send(Action.OPEN_DOOR))
                        .bounds(left + 10, by, 128, 20).build());
                addRenderableWidget(Button.builder(tr("hang_up").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP))
                        .bounds(left + 142, by, 128, 20).build());
            }
            default -> {
                Button clear = Button.builder(tr("clear_missed"), b -> send(Action.CLEAR_MISSED))
                        .bounds(left + panelW - 100, by, 90, 20).build();
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
        Component caller = Intercom.sideName(data.callerKey());
        switch (callState()) {
            case RINGING -> {
                boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
                g.fill(left + 20, top + 60, left + panelW - 20, top + 120, blink ? 0xFF2A3F5A : 0xFF1F3048);
                g.drawCenteredString(font, tr("ringing_title"), cx, top + 72, TEXT_WARN);
                g.drawCenteredString(font, tr("ringing_from", caller), cx, top + 92, TEXT);
                g.drawCenteredString(font, tr("ringing_hint"), cx, top + 132, TEXT_DIM);
            }
            case CONNECTED -> {
                g.drawString(font, tr("connected_with", caller), left + 12, top + 50, TEXT_OK, false);
                renderLog(g, data.log(), left + 12, top + 64, panelW - 24, 6);
            }
            default -> {
                g.drawString(font, tr("idle"), left + 12, top + 50, TEXT_DIM, false);
                g.drawString(font, tr("missed_title"), left + 12, top + 68, TEXT, false);
                List<MissedCall> missed = data.missed();
                if (missed.isEmpty()) {
                    g.drawString(font, tr("missed_none"), left + 20, top + 82, TEXT_DIM, false);
                }
                for (int i = 0; i < missed.size(); i++) {
                    MissedCall m = missed.get(i);
                    Component line = Component.literal("• ").append(Intercom.sideName(m.caller()))
                            .append("   ").append(tr("day_time", day(m.dayTime()), clock(m.dayTime())));
                    g.drawString(font, line, left + 20, top + 82 + i * 11, TEXT_WARN, false);
                }
            }
        }
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
        g.drawString(font, tr("settings_help_1"), left + 12, top + 96, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_2"), left + 12, top + 108, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_3"), left + 12, top + 120, TEXT_DIM, false);
        g.drawString(font, tr("settings_help_4"), left + 12, top + 132, TEXT_DIM, false);
        g.drawString(font, tr("device_count", data.devices().size()), left + 12, top + 152, TEXT_DIM, false);
    }

    // ------------------------------------------------------------------ 입력 / 틱

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (msgBox != null && msgBox.isFocused()) {
                sendMessage();
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
        if (unitBox != null) unitBox.tick();
        // 다른 플레이어가 바꾼 기기 상태 반영을 위해 2초마다 갱신 요청
        if (tab == Tab.HOME && ++refreshTimer >= 40) {
            refreshTimer = 0;
            send(Action.REFRESH);
        }
    }
}
