package com.qwerty.homenet.client.lobby;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalDateTime;

/**
 * 로비폰 LCD 위쪽 정보 박스에 표시할 내용.
 * GUI 와 블록 정면 렌더러가 같이 쓴다.
 */
public record LobbyLcd(Component topLeft, Component topRight, Component big, float bigScale,
                       Component subLeft, Component subRight) {

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable("lobby." + HomeNet.MODID + "." + key, args);
    }

    public static Component msg(String key, String arg) {
        return Component.translatable("lobby_msg." + HomeNet.MODID + "." + key, arg);
    }

    private static final Component EMPTY = Component.empty();

    public static LobbyLcd of(LobbyPhoneBlockEntity be, long gameTime) {
        Screen s = be.getScreen();
        return switch (s) {
            case IDLE -> idle();
            case INPUT -> {
                Component sub;
                if (be.getLobbyType() == 1) {
                    sub = be.isDongStage() ? tr("enter_dong")
                            : tr("dong_ho", be.getInputDong(), be.getInput());
                } else if (!be.getDong().isEmpty()) {
                    sub = tr("dong_ho", be.getDong(), be.getInput());
                } else {
                    sub = tr("ho", be.getInput());
                }
                yield new LobbyLcd(EMPTY, EMPTY, Component.literal(be.getInput()), 3.0f, sub, EMPTY);
            }
            case PASSWORD, COMMON_PASSWORD -> {
                int n = be.getSecretLength();
                Component big = n == 0 ? tr("password") : Component.literal("*".repeat(n));
                yield new LobbyLcd(EMPTY, EMPTY, big, n == 0 ? 2.2f : 3.0f, tr("enter_password"), EMPTY);
            }
            case CALLING, TALKING -> {
                long secs = Math.max(0, (gameTime - be.getStateSince()) / 20);
                Component timer = Component.literal(String.format("%02d:%02d", secs / 60, secs % 60));
                String what = be.isGuardCall() ? "guard" : "unit";
                Component sub = tr((s == Screen.CALLING ? "calling_" : "talking_") + what);
                String label = be.getBigLabel();
                float scale = label.length() > 4 ? 2.2f : 3.0f;
                yield new LobbyLcd(EMPTY, EMPTY, Component.literal(label), scale, sub, timer);
            }
            case HELP -> new LobbyLcd(EMPTY, EMPTY, tr("help_title"), 1.8f, EMPTY, EMPTY);
            case ADMIN_PASSWORD, ADMIN_MENU, ADMIN_EDIT -> new LobbyLcd(EMPTY, EMPTY, tr("admin_title"), 1.6f, EMPTY, EMPTY);
            case MESSAGE -> {
                if ("opened".equals(be.getMsgKey())) {
                    yield new LobbyLcd(EMPTY, EMPTY, tr("opened_big"), 2.6f, msg("opened", ""), EMPTY);
                }
                yield new LobbyLcd(EMPTY, EMPTY, msg(be.getMsgKey(), be.getMsgArg()), 1.0f, EMPTY, EMPTY);
            }
        };
    }

    public static LobbyLcd idle() {
        LocalDateTime now = LocalDateTime.now();
        int h = now.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        Component ampm = tr(h < 12 ? "am" : "pm");
        Component date = tr("date", now.getMonthValue(), now.getDayOfMonth(),
                tr("dow." + now.getDayOfWeek().getValue()));
        Component time = Component.literal(h12 + ":" + String.format("%02d", now.getMinute()));
        return new LobbyLcd(ampm, date, time, 3.2f, EMPTY, EMPTY);
    }
}
