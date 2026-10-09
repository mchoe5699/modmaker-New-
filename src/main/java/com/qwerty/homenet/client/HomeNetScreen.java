package com.qwerty.homenet.client;

import com.qwerty.homenet.HomeNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 월패드 / 인터폰 / 설정 화면 공통: 단말기 느낌의 패널 그리기 */
public abstract class HomeNetScreen extends Screen {
    // 색상 (ARGB)
    protected static final int BEZEL = 0xFFE4E6EA;      // 흰색 테두리 (실제 월패드 느낌)
    protected static final int BEZEL_EDGE = 0xFFB8BCC4;
    protected static final int SCREEN = 0xFF0F1722;     // 화면 배경
    protected static final int HEADER = 0xFF1B2B40;
    protected static final int ACCENT = 0xFF3FA9F5;
    protected static final int TEXT = 0xFFFFFFFF;
    protected static final int TEXT_DIM = 0xFF9AA6B6;
    protected static final int TEXT_WARN = 0xFFFFC94D;
    protected static final int TEXT_OK = 0xFF6EE07A;

    protected final int panelW, panelH;
    protected int left, top;

    protected HomeNetScreen(Component title, int panelW, int panelH) {
        super(title);
        this.panelW = panelW;
        this.panelH = panelH;
    }

    protected static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui." + HomeNet.MODID + "." + key, args);
    }

    @Override
    protected void init() {
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    /** 단말기 몸체 + 화면 + 상단 바 */
    protected void drawDevice(GuiGraphics g, Component headerLeft, Component headerRight) {
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BEZEL_EDGE);
        g.fill(left, top, left + panelW, top + panelH, BEZEL);
        g.fill(left + 5, top + 5, left + panelW - 5, top + panelH - 5, SCREEN);
        g.fill(left + 5, top + 5, left + panelW - 5, top + 20, HEADER);
        g.fill(left + 5, top + 20, left + panelW - 5, top + 21, ACCENT);
        g.drawString(font, headerLeft, left + 10, top + 9, TEXT, false);
        if (headerRight != null) {
            int w = font.width(headerRight);
            g.drawString(font, headerRight, left + panelW - 10 - w, top + 9, TEXT_DIM, false);
        }
    }

    /** 마인크래프트 시간 → "HH:MM" */
    protected static String clock(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        int hours = (int) ((t / 1000 + 6) % 24);
        int minutes = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
    }

    protected static long day(long dayTime) {
        return dayTime / 24000L + 1;
    }

    /** 상단 바 오른쪽: 현재 시각 · 날씨 */
    protected Component clockAndWeather() {
        var level = Minecraft.getInstance().level;
        if (level == null) return Component.empty();
        String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
        return Component.literal(clock(level.getDayTime()) + "  ").append(tr("weather." + weather));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
