package com.qwerty.homenet.client.lobby;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalDateTime;

/**
 * 로비폰 LCD 정보 박스 내용 + LCD 글꼴.
 * GUI 와 블록 정면 렌더러가 같이 쓴다.
 */
public record LobbyLcd(Component topLeft, Component topRight, Component big, float bigScale,
                       Component subLeft, Component subRight) {

    /** 화면 글꼴 (사용자 제공 TTF) */
    public static final ResourceLocation FONT = HomeNet.id("lcd");
    /** 키패드 숫자 (흰색 비트맵, 높이 8단위 중 0.5~7.5) */
    public static final ResourceLocation KEY_FONT = HomeNet.id("lcd_key");
    /** 정보 박스 큰 숫자 (양각 비트맵) */
    public static final ResourceLocation BIG_FONT = HomeNet.id("lcd_big");
    /** 아이콘:  네트워크,  문열림 */
    public static final ResourceLocation ICON_FONT = HomeNet.id("lcd_icons");
    public static final String ICON_NETWORK = "";
    public static final String ICON_DOOR = "";

    public static MutableComponent lcd(Component c) {
        return c.copy().withStyle(s -> s.withFont(FONT));
    }

    public static MutableComponent lcd(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(FONT));
    }

    public static MutableComponent tr(String key, Object... args) {
        return lcd(Component.translatable("lobby." + HomeNet.MODID + "." + key, args));
    }

    public static MutableComponent msg(String key, String arg) {
        return lcd(Component.translatable("lobby_msg." + HomeNet.MODID + "." + key, arg));
    }

    public static MutableComponent keyDigit(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(KEY_FONT));
    }

    public static MutableComponent icon(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(ICON_FONT));
    }

    /** 숫자/콜론/별표만이면 양각 비트맵 글꼴, 아니면 LCD 글꼴 */
    public static MutableComponent big(String s) {
        if (s.matches("[0-9:*\\-]+")) return Component.literal(s).withStyle(st -> st.withFont(BIG_FONT));
        return lcd(s);
    }

    public static boolean isBitmapBig(Component c) {
        return c.getString().matches("[0-9:*\\-]+");
    }

    private static final Component EMPTY = Component.empty();

    public static LobbyLcd of(LobbyPhoneBlockEntity be, long gameTime) {
        Screen s = be.getScreen();
        return switch (s) {
            case IDLE -> idle();
            case INPUT -> {
                Component sub;
                if (be.isParkingLobby()) {
                    sub = be.isDongStage() ? tr("dong", be.getInput()) : tr("dong_ho", be.getInputDong(), be.getInput());
                } else if (!be.lobbyDong().isEmpty()) {
                    sub = tr("dong_ho", be.lobbyDong(), be.getInput());
                } else {
                    sub = tr("ho", be.getInput());
                }
                yield new LobbyLcd(EMPTY, EMPTY, big(be.getInput()), 4.0f, sub, EMPTY);
            }
            case PASSWORD, COMMON_PASSWORD -> {
                int n = be.getSecretLength();
                Component b = n == 0 ? tr("password") : big("*".repeat(n));
                yield new LobbyLcd(EMPTY, EMPTY, b, n == 0 ? 2.6f : 4.0f, tr("enter_password"), EMPTY);
            }
            case CALLING, TALKING -> {
                long secs = Math.max(0, (gameTime - be.getStateSince()) / 20);
                Component timer = lcd(String.format("%02d:%02d", secs / 60, secs % 60));
                String what = be.isGuardCall() ? "guard" : "unit";
                Component sub = tr((s == Screen.CALLING ? "calling_" : "talking_") + what);
                yield new LobbyLcd(EMPTY, EMPTY, big(be.getBigLabel()), 4.0f, sub, timer);
            }
            case MESSAGE -> new LobbyLcd(EMPTY, EMPTY, msg(be.getMsgKey(), be.getMsgArg()), 1.0f, EMPTY, EMPTY);
            case HELP -> new LobbyLcd(EMPTY, EMPTY, tr("help_title"), 1.8f, EMPTY, EMPTY);
            default -> new LobbyLcd(EMPTY, EMPTY, tr("admin_title"), 1.6f, EMPTY, EMPTY);
        };
    }

    public static LobbyLcd idle() {
        LocalDateTime now = LocalDateTime.now();
        int h = now.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        Component ampm = tr(h < 12 ? "am" : "pm");
        Component date = tr("date", now.getMonthValue(), now.getDayOfMonth(), tr("dow." + now.getDayOfWeek().getValue()));
        Component time = big(h12 + ":" + String.format("%02d", now.getMinute()));
        return new LobbyLcd(ampm, date, time, 4.2f, EMPTY, EMPTY);
    }
}
