package com.qwerty.homenet.client;

import com.qwerty.homenet.blockentity.DoorStationBlockEntity.Action;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.network.DoorStationActionPacket;
import com.qwerty.homenet.network.DoorStationDataPacket;
import com.qwerty.homenet.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 인터폰 화면.
 * - 로비폰: 키패드로 세대 번호(예: 101-1203) 입력 후 호출
 * - 세대현관 도어폰: 호출 버튼 하나
 * - 통화 연결되면 메시지 입력
 */
public class DoorStationScreen extends HomeNetScreen {
    private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "0", "←"};

    private final BlockPos pos;
    private DoorStationDataPacket data;

    private String dialDraft = "";
    private String msgDraft = "";
    private EditBox dialBox;
    private EditBox msgBox;

    public DoorStationScreen(DoorStationDataPacket data) {
        super(Component.translatable("block.qwertys_homenet.door_station"), 220, 214);
        this.pos = data.pos();
        this.data = data;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void update(DoorStationDataPacket newData) {
        boolean msgFocused = msgBox != null && msgBox.isFocused();
        boolean dialFocused = dialBox != null && dialBox.isFocused();
        this.data = newData;
        rebuildWidgets();
        if (msgFocused && msgBox != null) setFocused(msgBox);
        if (dialFocused && dialBox != null) setFocused(dialBox);
    }

    private void send(Action action, String text) {
        ModNetwork.sendToServer(new DoorStationActionPacket(pos, action.ordinal(), text));
    }

    @Override
    protected void init() {
        super.init();
        dialBox = null;
        msgBox = null;
        int cx = left + panelW / 2;
        int by = top + panelH - 30;

        if (data.inCall()) {
            if (data.connected()) {
                msgBox = new EditBox(font, left + 11, by - 25, 136, 18, tr("message"));
                msgBox.setMaxLength(IntercomLine.MAX_TEXT);
                msgBox.setValue(msgDraft);
                msgBox.setResponder(s -> msgDraft = s);
                msgBox.setHint(tr("message_hint"));
                addRenderableWidget(msgBox);
                addRenderableWidget(Button.builder(tr("send"), b -> sendMessage())
                        .bounds(left + 152, by - 26, 58, 20).build());
            }
            addRenderableWidget(Button.builder(tr("hang_up").withStyle(ChatFormatting.RED), b -> send(Action.HANG_UP, ""))
                    .bounds(cx - 60, by, 120, 20).build());
            return;
        }

        if (data.lobby()) {
            // 번호 입력칸 + 3x4 키패드 + 호출
            dialBox = new EditBox(font, cx - 60, top + 62, 120, 18, tr("unit"));
            dialBox.setMaxLength(16);
            dialBox.setValue(dialDraft);
            dialBox.setResponder(s -> dialDraft = s);
            dialBox.setHint(tr("dial_hint"));
            addRenderableWidget(dialBox);

            int kx = cx - 52, ky = top + 86;
            for (int i = 0; i < KEYS.length; i++) {
                String k = KEYS[i];
                int col = i % 3, row = i / 3;
                addRenderableWidget(Button.builder(Component.literal(k), b -> pressKey(k))
                        .bounds(kx + col * 36, ky + row * 22, 32, 20).build());
            }
            addRenderableWidget(Button.builder(tr("call").withStyle(ChatFormatting.GREEN), b -> call())
                    .bounds(cx - 60, by, 120, 20).build());
        } else {
            addRenderableWidget(Button.builder(tr("call_unit").withStyle(ChatFormatting.GREEN), b -> call())
                    .bounds(cx - 60, top + 110, 120, 24).build());
        }
    }

    private void pressKey(String k) {
        if (k.equals("←")) {
            if (!dialDraft.isEmpty()) dialDraft = dialDraft.substring(0, dialDraft.length() - 1);
        } else if (dialDraft.length() < 16) {
            dialDraft += k;
        }
        if (dialBox != null) dialBox.setValue(dialDraft);
    }

    private void call() {
        send(Action.CALL, data.lobby() ? dialDraft : "");
    }

    private void sendMessage() {
        String text = Intercom.sanitize(msgDraft, IntercomLine.MAX_TEXT);
        if (text.isEmpty()) return;
        send(Action.SEND_MESSAGE, text);
        msgDraft = "";
        if (msgBox != null) msgBox.setValue("");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Component header = data.lobby() ? tr("lobby_title")
                : tr("front_door_title", data.linkedUnit().isEmpty() ? "-" : data.linkedUnit());
        drawDevice(g, header, null);

        // 카메라 렌즈 장식
        int cx = left + panelW / 2;
        g.fill(cx - 4, top + 26, cx + 4, top + 34, 0xFF2A2F38);
        g.fill(cx - 2, top + 28, cx + 2, top + 32, data.inCall() ? 0xFF3FA9F5 : 0xFF11151B);

        DoorStatus status = DoorStatus.byId(data.status());
        int color = switch (status) {
            case CONNECTED, OPENED -> TEXT_OK;
            case CALLING -> TEXT_WARN;
            case IDLE, ENDED -> TEXT_DIM;
            default -> 0xFFFF7A7A;
        };
        Component statusText = status == DoorStatus.IDLE
                ? (data.lobby() ? tr("lobby_idle") : tr("front_door_idle"))
                : status.display(data.statusArg());
        g.drawCenteredString(font, statusText, cx, top + 44, color);

        if (data.inCall() && data.connected()) {
            if (data.log().isEmpty()) {
                g.drawString(font, tr("log_empty"), left + 12, top + 62, TEXT_DIM, false);
            } else {
                WallpadScreen.renderLog(g, data.log(), left + 12, top + 62, panelW - 24, 8, font);
            }
        } else if (data.inCall()) {
            g.drawCenteredString(font, tr("waiting_answer"), cx, top + 100, TEXT_DIM);
        } else if (!data.lobby()) {
            g.drawCenteredString(font, tr("front_door_hint"), cx, top + 80, TEXT_DIM);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (msgBox != null && msgBox.isFocused()) {
                sendMessage();
                return true;
            }
            if (!data.inCall()) {
                call();
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
    }
}
