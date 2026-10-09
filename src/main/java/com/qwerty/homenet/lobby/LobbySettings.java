package com.qwerty.homenet.lobby;

/**
 * 관리자 설정 항목 (설정 화면 2페이지 x 7항목).
 * 설명서의 "설정 항목별 화면"을 마인크래프트에서 의미 있는 항목으로 옮긴 것.
 */
public final class LobbySettings {
    private LobbySettings() {}

    public enum Kind {
        /** 숫자 범위 */
        NUMBER,
        /** 숫자 문자열 (비워도 됨) */
        DIGITS,
        /** 4자리 비밀번호 (화면에는 **** 로 표시) */
        PASSWORD
    }

    /**
     * @param key      번역 키 접미사 (lobby_setting.qwertys_homenet.<key>)
     * @param kind     입력 종류
     * @param min      NUMBER 최솟값 / DIGITS·PASSWORD 최소 길이 (0이면 비워둘 수 있음)
     * @param max      NUMBER 최댓값 / DIGITS·PASSWORD 최대 길이
     */
    public record Item(String key, Kind kind, int min, int max) {
        public int maxInputLength() {
            return kind == Kind.NUMBER ? String.valueOf(max).length() : max;
        }
    }

    public static final Item[][] PAGES = {
            {
                    new Item("dong", Kind.DIGITS, 0, 4),
                    new Item("guard_no", Kind.DIGITS, 0, 8),
                    new Item("lobby_type", Kind.NUMBER, 0, 1),
                    new Item("system_password", Kind.PASSWORD, 4, 4),
                    new Item("common_password", Kind.PASSWORD, 0, 4),
                    new Item("common_password_use", Kind.NUMBER, 0, 1),
                    new Item("open_time", Kind.NUMBER, 1, 8),
            },
            {
                    new Item("key_volume", Kind.NUMBER, 0, 5),
                    new Item("melody_volume", Kind.NUMBER, 0, 5),
                    new Item("digit_voice", Kind.NUMBER, 0, 1),
                    new Item("backlight_always", Kind.NUMBER, 0, 1),
                    new Item("key_led_always", Kind.NUMBER, 0, 1),
                    new Item("ring_time", Kind.NUMBER, 10, 30),
                    new Item("talk_time", Kind.NUMBER, 1, 5),
            }
    };

    public static int pageCount() {
        return PAGES.length;
    }

    /** 범위 안 1~7번 항목이면 반환 */
    public static Item item(int page, int index) {
        if (page < 0 || page >= PAGES.length || index < 0 || index >= PAGES[page].length) return null;
        return PAGES[page][index];
    }

    /** 입력값 검증. 통과하면 null, 아니면 오류 메시지 키 */
    public static String validate(Item item, String value) {
        switch (item.kind()) {
            case NUMBER -> {
                if (value.isEmpty()) return "out_of_range";
                int v;
                try {
                    v = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return "out_of_range";
                }
                return v < item.min() || v > item.max() ? "out_of_range" : null;
            }
            case DIGITS -> {
                if (!value.matches("\\d*")) return "out_of_range";
                if (value.isEmpty()) return item.min() == 0 ? null : "out_of_range";
                return value.length() > item.max() ? "out_of_range" : null;
            }
            case PASSWORD -> {
                if (!value.matches("\\d*")) return "out_of_range";
                if (value.isEmpty()) return item.min() == 0 ? null : "need_4_digits";
                return value.length() != item.max() ? "need_4_digits" : null;
            }
        }
        return null;
    }
}
