package com.qwerty.homenet.lobby;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 설정 화면 (설명서 12~26쪽, 영상 기준) 6페이지 x 7항목.
 * 마인크래프트에서 의미 있는 항목은 실제로 동작하고, 네트워크/DSP 같은 항목은 실제 기기처럼 값만 표시한다.
 */
public final class LobbySettings {
    private LobbySettings() {}

    public enum Kind {
        /** 숫자 범위 */
        NUMBER,
        /** 숫자 문자열 (비워도 됨) */
        DIGITS,
        /** 4자리 비밀번호 */
        PASSWORD,
        /** 로비번호: 동 + 라인 (하단 동/라인 버튼) */
        LOBBY_NO,
        /** 표시만 (네트워크 / DSP 등) */
        READONLY
    }

    /**
     * @param key  설정 키 = 번역 키 접미사
     * @param kind 입력 종류
     * @param min  NUMBER 최솟값 / PASSWORD 0 이면 비워둘 수 있음
     * @param max  NUMBER 최댓값 / DIGITS 최대 길이
     * @param def  기본값
     */
    public record Item(String key, Kind kind, int min, int max, String def) {
        public int maxInputLength() {
            return switch (kind) {
                case NUMBER -> String.valueOf(max).length();
                case PASSWORD -> 4;
                case LOBBY_NO -> 4;
                default -> max;
            };
        }
    }

    public static final String APP_VER = "App Ver : 070.501.73";
    public static final String FW_VER = "FW Ver : 02.09";

    public static final Item[][] PAGES = {
            {
                    new Item("lobby_no", Kind.LOBBY_NO, 0, 0, "0101|13"),
                    new Item("guard_no", Kind.DIGITS, 0, 8, ""),
                    new Item("manage_line", Kind.READONLY, 0, 0, "01 - 10 라인"),
                    new Item("system_password", Kind.PASSWORD, 4, 4, "0000"),
                    new Item("network_type", Kind.READONLY, 0, 0, "0"),
                    new Item("parking_lookup", Kind.READONLY, 0, 0, "0"),
                    new Item("rf_server", Kind.READONLY, 0, 0, "010.254.254.100"),
            },
            {
                    new Item("parking_lobby", Kind.NUMBER, 0, 1, "0"),
                    new Item("rf_server_use", Kind.READONLY, 0, 0, "1"),
                    new Item("ip_address", Kind.READONLY, 0, 0, "010.101.254.013"),
                    new Item("subnet_mask", Kind.READONLY, 0, 0, "255.255.000.000"),
                    new Item("gateway", Kind.READONLY, 0, 0, "010.101.000.254"),
                    new Item("complex_server", Kind.READONLY, 0, 0, "010.254.254.001"),
                    new Item("sip_server", Kind.READONLY, 0, 0, "010.254.254.001"),
            },
            {
                    new Item("guard_password", Kind.PASSWORD, 0, 4, ""),
                    new Item("common_password", Kind.PASSWORD, 0, 4, ""),
                    new Item("elevator_mode", Kind.READONLY, 0, 0, "0"),
                    new Item("monitor_mode", Kind.READONLY, 0, 0, "0"),
                    new Item("door_state", Kind.READONLY, 0, 0, "0"),
                    new Item("sensor1", Kind.NUMBER, 0, 2, "1"),
                    new Item("sensor2", Kind.NUMBER, 0, 2, "2"),
            },
            {
                    new Item("prox_use", Kind.NUMBER, 0, 1, "1"),
                    new Item("prox_data", Kind.NUMBER, 0, 255, "40"),
                    new Item("light_use", Kind.NUMBER, 0, 1, "1"),
                    new Item("light_button_data", Kind.NUMBER, 12, 255, "138"),
                    new Item("light_camera_data", Kind.NUMBER, 12, 255, "250"),
                    new Item("key_led_always", Kind.NUMBER, 0, 1, "1"),
                    new Item("key_led_time", Kind.NUMBER, 1, 20, "10"),
            },
            {
                    new Item("backlight_always", Kind.NUMBER, 0, 1, "0"),
                    new Item("backlight_time", Kind.NUMBER, 1, 20, "20"),
                    new Item("digit_voice", Kind.NUMBER, 0, 1, "1"),
                    new Item("open_time", Kind.NUMBER, 1, 8, "3"),
                    new Item("guard_volume", Kind.NUMBER, 1, 5, "3"),
                    new Item("unit_volume", Kind.NUMBER, 1, 5, "3"),
                    new Item("melody_volume", Kind.NUMBER, 0, 5, "3"),
            },
            {
                    new Item("key_tone_volume", Kind.NUMBER, 0, 5, "3"),
                    new Item("video_out", Kind.READONLY, 0, 0, "255"),
                    new Item("common_password_use", Kind.NUMBER, 0, 1, "1"),
                    new Item("dsp_unit", Kind.READONLY, 0, 0, "01 03 00 20 ..."),
                    new Item("dsp_guard", Kind.READONLY, 0, 0, "01 03 00 20 ..."),
                    new Item("dsp_melody", Kind.READONLY, 0, 0, "FF FF FF FF ..."),
                    new Item("dsp_keytone", Kind.READONLY, 0, 0, "FF FF FF FF ..."),
            }
    };

    public static int pageCount() {
        return PAGES.length;
    }

    public static Item item(int page, int index) {
        if (page < 0 || page >= PAGES.length || index < 0 || index >= PAGES[page].length) return null;
        return PAGES[page][index];
    }

    public static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Item[] page : PAGES) for (Item it : page) m.put(it.key(), it.def());
        return m;
    }

    /** 입력값 검증. 통과하면 null, 아니면 메시지 키 */
    public static String validate(Item item, String value) {
        switch (item.kind()) {
            case NUMBER -> {
                if (value.isEmpty()) return "out_of_range";
                try {
                    int v = Integer.parseInt(value);
                    return v < item.min() || v > item.max() ? "out_of_range" : null;
                } catch (NumberFormatException e) {
                    return "out_of_range";
                }
            }
            case DIGITS -> {
                if (!value.matches("\\d*")) return "out_of_range";
                return value.length() > item.max() ? "out_of_range" : null;
            }
            case PASSWORD -> {
                if (!value.matches("\\d*")) return "out_of_range";
                if (value.isEmpty()) return item.min() == 0 ? null : "need_4_digits";
                return value.length() != 4 ? "need_4_digits" : null;
            }
            default -> {
                return null;
            }
        }
    }

    /** "0101|13" → "0101 동   13 라인" */
    public static String lobbyNoDisplay(String v) {
        String[] p = v.split("\\|", -1);
        String dong = p.length > 0 ? p[0] : "";
        String line = p.length > 1 ? p[1] : "";
        return dong + " 동   " + line + " 라인";
    }

    public static String pad(String digits, int len) {
        String d = digits.length() > len ? digits.substring(digits.length() - len) : digits;
        return "0".repeat(len - d.length()) + d;
    }
}
