package com.qwerty.homenet.intercom;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 통화 중 주고받은 한 줄.
 * @param side "unit"(세대/월패드 쪽), "lobby"(공동현관), "front_door"(세대현관)
 */
public record IntercomLine(String side, String name, String text) {
    public static final int MAX_TEXT = 80;

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(side, 64);
        buf.writeUtf(name, 64);
        buf.writeUtf(text, MAX_TEXT * 4);
    }

    public static IntercomLine read(FriendlyByteBuf buf) {
        return new IntercomLine(buf.readUtf(64), buf.readUtf(64), buf.readUtf(MAX_TEXT * 4));
    }
}
