package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomCaller;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.lobby.LobbySettings;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 공동현관 로비폰 (ASTRO KLP-70D 공동 현관기 설명서 + 실제 기기 영상 기준).
 *
 * 화면 상태는 서버가 관리하고 블록엔티티 동기화로 클라이언트(GUI / 블록 정면)에 보낸다.
 *
 *  - 세대 번호 → [호출] : 세대 호출 / [경비] : 경비실 호출
 *  - 세대 번호 → [비밀번호 입력] → 4자리 → [확인] : 세대 비밀번호 문열기
 *  - [공동비밀번호 입력] → 4자리 → [확인] : 공동 비밀번호 문열기
 *    틀리면 비밀번호 입력 화면이 다시 뜨고, 3회 틀리면 경비실 자동 호출
 *  - 문이 열리면 네트워크 아이콘 옆에 문열림 아이콘이 문열림 시간 동안 표시
 *  - [보안] 키패드 임의 배열, 대기화면 [취소] 숫자 크기 (작은→보통→큰)
 *  - 관리자: 대기화면에서 [호출] 다음 [경비] → 관리자 비밀번호
 *    '0'번 RF 카드 설정, '9'번 근접센서 설정, 1~7 항목, ←/→ 페이지, 편집 중 호출=지우기 경비=저장
 *  - 출입 카드 접촉 → 등록된 세대 카드면 문열림, 마스터 카드면 RF 카드 메뉴, 등록용 카드면 세대 카드 등록
 */
public class LobbyPhoneBlockEntity extends CallerBlockEntity {

    // ------------------------------------------------------------------ 키 코드
    public static final int KEY_LEFT = 10;      // 키패드 왼쪽 아래
    public static final int KEY_RIGHT = 11;     // 키패드 오른쪽 아래
    public static final int KEY_SECURITY = 12;  // 보안
    public static final int KEY_CALL = 13;      // 호출
    public static final int KEY_GUARD = 14;     // 경비
    public static final int KEY_CANCEL = 15;    // 취소
    public static final int KEY_MESSAGE = 16;   // 통화 중 메시지 (마인크래프트 전용)
    public static final int KEY_CARD = 17;      // 카드 접촉 (GUI 에서 카드 인식부 클릭)

    public enum Screen {
        IDLE, INPUT, PASSWORD, COMMON_PASSWORD, CALLING, TALKING, HELP,
        ADMIN_PASSWORD, ADMIN_MENU, ADMIN_EDIT, MESSAGE,
        CARD_MENU, CARD_MASTER, CARD_REG, CARD_UNIT_MENU, CARD_UNIT_REG, CARD_DELETE, CARD_UNIT_DELETE, CARD_DELETE_ALL,
        PROX_MENU;

        public static Screen byId(int id) {
            Screen[] v = values();
            return id >= 0 && id < v.length ? v[id] : IDLE;
        }

        public boolean isAdmin() {
            return ordinal() >= ADMIN_PASSWORD.ordinal() && this != MESSAGE;
        }
    }

    public record Card(String type, String dong, String ho) {
        public static final String MASTER = "master", REG = "reg", UNIT = "unit";
    }

    private static final int INPUT_TIMEOUT = 20 * 30;
    private static final int ADMIN_TIMEOUT = 20 * 120;
    private static final int MESSAGE_TICKS = 50;
    private static final int NOTICE_TICKS = 50;
    private static final int ADMIN_ARM_TICKS = 20 * 5;
    private static final int[] DEFAULT_LAYOUT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};

    // ------------------------------------------------------------------ 저장되는 값
    private final Map<String, String> cfg = LobbySettings.defaults();
    private final Map<String, Card> cards = new LinkedHashMap<>();

    // ------------------------------------------------------------------ 화면 상태 (동기화)
    private Screen screen = Screen.IDLE;
    private String input = "";
    private String inputDong = "";
    private boolean dongStage;
    private String unitInput = "";
    private String secret = "";
    private String bigLabel = "";
    private boolean guardCall;
    private int[] layout = DEFAULT_LAYOUT.clone();
    private int digitSize = 2;
    private int adminPage;
    private int editItem = -1;
    private String editValue = "";
    private String editDong = "";
    private String editLine = "";
    private String msgKey = "";
    private String msgArg = "";
    private Screen msgReturn = Screen.IDLE;
    private long stateSince;
    private long lastInput;
    private boolean backlight = true;
    private long doorOpenUntil;
    private long keyLedUntil;
    private boolean adminArmed;
    private long adminArmedAt;
    private int failCount;
    // 카드 화면
    private String cardDong = "0000";
    private String cardHo = "0000";
    private boolean cardHoSet;
    private String pendingCard = "";
    private String noticeKey = "";
    private String noticeArg = "";
    private long noticeUntil;

    // 클라이언트 전용
    private int secretLen;
    private boolean clientConnected;
    private int cardCount;

    private final Random random = new Random();

    public LobbyPhoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOBBY_PHONE.get(), pos, state);
    }

    @Override
    public DeviceRegistry.Kind kind() {
        return DeviceRegistry.Kind.LOBBY_PHONE;
    }

    @Override
    protected int openTicks() {
        return Math.max(1, cfgInt("open_time")) * 20;
    }


    // ------------------------------------------------------------------ 설정 값

    public String cfg(String key) {
        String v = cfg.get(key);
        return v == null ? "" : v;
    }

    private int cfgInt(String key) {
        try {
            return Integer.parseInt(cfg(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 로비 동번호 (앞 0 제거) */
    public String lobbyDong() {
        String d = cfg("lobby_no").split("\\|", -1)[0].replaceFirst("^0+(?=.)", "");
        return "0".equals(d) ? "" : d;
    }

    public boolean isParkingLobby() { return cfgInt("parking_lobby") == 1; }
    public boolean isCommonPasswordUse() { return cfgInt("common_password_use") == 1; }

    // ================================================================== 키 입력

    public void press(ServerPlayer player, int key, String text) {
        if (!(level instanceof ServerLevel)) return;
        long t = now();
        lastInput = t;
        backlight = true;
        keyLedUntil = t + cfgInt("key_led_time") * 20L;

        if (key == KEY_MESSAGE) {
            if (screen == Screen.TALKING && isConnected()) say(player, "lobby", text);
            return;
        }
        if (key == KEY_CARD) {
            String id = com.qwerty.homenet.item.RfCardItem.cardId(player.getMainHandItem());
            if (id != null) tapCard(id);
            return;
        }
        keyTone();

        boolean armedNow = adminArmed && t - adminArmedAt <= ADMIN_ARM_TICKS;
        adminArmed = false;

        switch (screen) {
            case IDLE -> pressIdle(key, armedNow);
            case INPUT -> pressInput(key);
            case PASSWORD, COMMON_PASSWORD -> pressPassword(key);
            case CALLING, TALKING -> {
                if (key == KEY_LEFT || key == KEY_CANCEL) hangUpFromLobby();
            }
            case HELP -> {
                if (key == KEY_LEFT || key == KEY_CANCEL) goIdle();
            }
            case ADMIN_PASSWORD -> pressAdminPassword(key);
            case ADMIN_MENU -> pressAdminMenu(key);
            case ADMIN_EDIT -> pressAdminEdit(key);
            case MESSAGE -> {
                if (key == KEY_CANCEL || key == KEY_LEFT) leaveMessage();
            }
            case CARD_MENU -> pressCardMenu(key);
            case CARD_MASTER, CARD_REG -> pressCardRegisterSpecial(key);
            case CARD_UNIT_MENU -> pressCardUnitMenu(key);
            case CARD_UNIT_REG, CARD_UNIT_DELETE -> pressCardUnitInput(key);
            case CARD_DELETE -> pressCardBack(key, Screen.CARD_UNIT_MENU);
            case CARD_DELETE_ALL -> {
                if (key == 0) {
                    int n = cards.size();
                    cards.clear();
                    setChanged();
                    notice("all_deleted", String.valueOf(n));
                } else {
                    pressCardBack(key, Screen.CARD_UNIT_MENU);
                }
            }
            case PROX_MENU -> pressProxMenu(key);
        }
        sync();
    }

    private void pressIdle(int key, boolean armedNow) {
        if (isDigit(key)) {
            setScreen(Screen.INPUT);
            dongStage = isParkingLobby();
            inputDong = "";
            input = String.valueOf(key);
            digitTone(key);
            return;
        }
        switch (key) {
            case KEY_CALL -> {
                adminArmed = true;
                adminArmedAt = now();
            }
            case KEY_GUARD -> {
                if (armedNow) {
                    secret = "";
                    setScreen(Screen.ADMIN_PASSWORD);
                } else {
                    startGuardCall();
                }
            }
            case KEY_LEFT -> setScreen(Screen.HELP);
            case KEY_RIGHT -> {
                if (isCommonPasswordUse()) {
                    secret = "";
                    failCount = 0;
                    setScreen(Screen.COMMON_PASSWORD);
                }
            }
            case KEY_SECURITY -> toggleLayout();
            case KEY_CANCEL -> digitSize = (digitSize + 1) % 3;
            default -> {}
        }
    }

    private void pressInput(int key) {
        if (isDigit(key)) {
            if (input.length() < 8) input += key;
            digitTone(key);
            return;
        }
        switch (key) {
            case KEY_LEFT, KEY_RIGHT -> {
                if (dongStage) {
                    if (!input.isEmpty()) {
                        inputDong = input;
                        input = "";
                        dongStage = false;
                    }
                } else if (key == KEY_LEFT) {
                    goIdle();
                } else if (!input.isEmpty()) {
                    unitInput = composeUnit();
                    secret = "";
                    failCount = 0;
                    setScreen(Screen.PASSWORD);
                }
            }
            case KEY_CALL -> {
                if (!dongStage && !input.isEmpty()) startUnitCall(composeUnit(), input);
            }
            case KEY_GUARD -> startGuardCall();
            case KEY_SECURITY -> toggleLayout();
            case KEY_CANCEL -> goIdle();
            default -> {}
        }
    }

    private void pressPassword(int key) {
        if (isDigit(key)) {
            if (secret.length() < 4) secret += key;
            digitTone(key);
            return;
        }
        switch (key) {
            case KEY_RIGHT -> checkPassword();
            case KEY_LEFT, KEY_CANCEL -> goIdle();
            case KEY_SECURITY -> toggleLayout();
            case KEY_GUARD -> startGuardCall();
            default -> {}
        }
    }

    private void pressAdminPassword(int key) {
        if (isDigit(key)) {
            if (secret.length() < 4) secret += key;
            if (secret.length() == 4) checkAdminPassword();
            return;
        }
        switch (key) {
            case KEY_RIGHT -> checkAdminPassword();
            case KEY_LEFT, KEY_CANCEL -> goIdle();
            default -> {}
        }
    }

    private void checkAdminPassword() {
        if (secret.length() == 4 && secret.equals(cfg("system_password"))) {
            adminPage = 0;
            setScreen(Screen.ADMIN_MENU);
        } else {
            // 실제 기기처럼 다시 입력 화면
            setScreen(Screen.ADMIN_PASSWORD);
        }
        secret = "";
    }

    private void pressAdminMenu(int key) {
        if (key == 0) {
            setScreen(Screen.CARD_MENU);
            return;
        }
        if (key == 9) {
            setScreen(Screen.PROX_MENU);
            return;
        }
        if (isDigit(key)) {
            LobbySettings.Item item = LobbySettings.item(adminPage, key - 1);
            if (item == null) return;
            if (item.kind() == LobbySettings.Kind.READONLY) {
                showMessage("unsupported", "", Screen.ADMIN_MENU);
                return;
            }
            editItem = key - 1;
            editValue = "";
            if (item.kind() == LobbySettings.Kind.LOBBY_NO) {
                String[] p = cfg("lobby_no").split("\\|", -1);
                editDong = p.length > 0 ? p[0] : "";
                editLine = p.length > 1 ? p[1] : "";
            } else if (item.kind() != LobbySettings.Kind.PASSWORD) {
                editValue = cfg(item.key());
            }
            setScreen(Screen.ADMIN_EDIT);
            return;
        }
        switch (key) {
            case KEY_LEFT -> adminPage = (adminPage + LobbySettings.pageCount() - 1) % LobbySettings.pageCount();
            case KEY_RIGHT -> adminPage = (adminPage + 1) % LobbySettings.pageCount();
            case KEY_CANCEL -> goIdle();
            default -> {}
        }
    }

    private void pressAdminEdit(int key) {
        LobbySettings.Item item = LobbySettings.item(adminPage, editItem);
        if (item == null) {
            setScreen(Screen.ADMIN_MENU);
            return;
        }
        if (isDigit(key)) {
            if (editValue.length() < item.maxInputLength()) editValue += key;
            return;
        }
        boolean lobbyNo = item.kind() == LobbySettings.Kind.LOBBY_NO;
        switch (key) {
            case KEY_LEFT -> {
                if (lobbyNo) { // [동] 버튼
                    if (!editValue.isEmpty()) editDong = LobbySettings.pad(editValue, 4);
                    editValue = "";
                }
            }
            case KEY_RIGHT -> {
                if (lobbyNo) { // [라인] 버튼
                    if (!editValue.isEmpty()) editLine = LobbySettings.pad(editValue, 2);
                    editValue = "";
                }
            }
            case KEY_CALL -> { // 지우기
                if (!editValue.isEmpty()) editValue = editValue.substring(0, editValue.length() - 1);
            }
            case KEY_GUARD -> { // 저장
                if (lobbyNo) {
                    cfg.put("lobby_no", editDong + "|" + editLine);
                    setChanged();
                    setScreen(Screen.ADMIN_MENU);
                    return;
                }
                String err = LobbySettings.validate(item, editValue);
                if (err != null) {
                    showMessage(err, "", Screen.ADMIN_EDIT);
                } else {
                    String v = item.kind() == LobbySettings.Kind.NUMBER ? String.valueOf(Integer.parseInt(editValue)) : editValue;
                    cfg.put(item.key(), v);
                    setChanged();
                    setScreen(Screen.ADMIN_MENU);
                }
            }
            case KEY_CANCEL -> setScreen(Screen.ADMIN_MENU);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ RF 카드 메뉴

    private void pressCardBack(int key, Screen parent) {
        if (key == KEY_CALL || key == KEY_GUARD) setScreen(parent);
        else if (key == KEY_CANCEL) goIdle();
    }

    private void pressCardMenu(int key) {
        switch (key) {
            case 1 -> { pendingCard = ""; setScreen(Screen.CARD_MASTER); }
            case 2 -> { pendingCard = ""; setScreen(Screen.CARD_REG); }
            case 3 -> setScreen(Screen.CARD_UNIT_MENU);
            default -> pressCardBack(key, Screen.ADMIN_MENU);
        }
    }

    private void pressCardRegisterSpecial(int key) {
        if (key == 0) {
            if (!pendingCard.isEmpty()) {
                String type = screen == Screen.CARD_MASTER ? Card.MASTER : Card.REG;
                cards.put(pendingCard, new Card(type, "", ""));
                setChanged();
                notice("registered", pendingCard);
                pendingCard = "";
            }
            return;
        }
        pressCardBack(key, Screen.CARD_MENU);
    }

    private void pressCardUnitMenu(int key) {
        switch (key) {
            case 1 -> { resetCardUnitInput(); setScreen(Screen.CARD_UNIT_REG); }
            case 2 -> setScreen(Screen.CARD_DELETE);
            case 3 -> { resetCardUnitInput(); setScreen(Screen.CARD_UNIT_DELETE); }
            case 4 -> setScreen(Screen.CARD_DELETE_ALL);
            default -> pressCardBack(key, Screen.CARD_MENU);
        }
    }

    private void resetCardUnitInput() {
        cardDong = "0000";
        cardHo = "0000";
        cardHoSet = false;
        editValue = "";
    }

    private void pressCardUnitInput(int key) {
        if (isDigit(key)) {
            if (editValue.length() < 4) editValue += key;
            return;
        }
        switch (key) {
            case KEY_LEFT -> { // * 동 입력
                if (!editValue.isEmpty()) cardDong = LobbySettings.pad(editValue, 4);
                editValue = "";
            }
            case KEY_RIGHT -> { // # 호 입력
                if (!editValue.isEmpty()) {
                    cardHo = LobbySettings.pad(editValue, 4);
                    cardHoSet = true;
                    editValue = "";
                } else if (screen == Screen.CARD_UNIT_DELETE && cardHoSet) {
                    int n = 0;
                    var it = cards.entrySet().iterator();
                    while (it.hasNext()) {
                        Card c = it.next().getValue();
                        if (Card.UNIT.equals(c.type()) && c.dong().equals(cardDong) && c.ho().equals(cardHo)) {
                            it.remove();
                            n++;
                        }
                    }
                    setChanged();
                    notice("unit_deleted", String.valueOf(n));
                }
            }
            case KEY_CALL -> {
                if (!editValue.isEmpty()) editValue = editValue.substring(0, editValue.length() - 1);
                else setScreen(Screen.CARD_UNIT_MENU);
            }
            default -> pressCardBack(key, Screen.CARD_UNIT_MENU);
        }
    }

    private void pressProxMenu(int key) {
        switch (key) {
            case 1 -> {
                adminPage = 3;
                editItem = 0;
                editValue = cfg("prox_use");
                setScreen(Screen.ADMIN_EDIT);
            }
            case 2 -> {
                cfg.put("prox_data", "40");
                setChanged();
                showMessage("prox_auto", "40", Screen.PROX_MENU);
                melody(1.3f);
            }
            default -> pressCardBack(key, Screen.ADMIN_MENU);
        }
    }

    // ------------------------------------------------------------------ 카드 접촉

    /** 출입 카드를 로비폰에 댐 (블록 우클릭 또는 GUI 카드 인식부 클릭) */
    public void tapCard(String id) {
        if (!(level instanceof ServerLevel)) return;
        long t = now();
        lastInput = t;
        backlight = true;
        Card card = cards.get(id);
        switch (screen) {
            case CARD_MASTER, CARD_REG -> {
                pendingCard = id;
                notice("card_touched", id);
                beep(true);
            }
            case CARD_UNIT_REG -> {
                if (!cardHoSet) {
                    notice("check_unit", "");
                    beep(false);
                } else {
                    cards.put(id, new Card(Card.UNIT, cardDong, cardHo));
                    setChanged();
                    notice("unit_registered", id);
                    beep(true);
                }
            }
            case CARD_DELETE -> {
                if (cards.remove(id) != null) {
                    setChanged();
                    notice("deleted", id);
                    beep(true);
                } else {
                    notice("not_registered", id);
                    beep(false);
                }
            }
            case IDLE, INPUT, PASSWORD, COMMON_PASSWORD, HELP -> {
                if (card == null) {
                    beep(false);
                } else if (Card.UNIT.equals(card.type())) {
                    beep(true);
                    openDoor();
                    if (screen != Screen.IDLE) goIdle();
                } else if (Card.MASTER.equals(card.type())) {
                    beep(true);
                    setScreen(Screen.CARD_MENU);
                } else {
                    beep(true);
                    resetCardUnitInput();
                    setScreen(Screen.CARD_UNIT_REG);
                }
            }
            default -> beep(false);
        }
        sync();
    }

    private void notice(String key, String arg) {
        noticeKey = key;
        noticeArg = arg == null ? "" : arg;
        noticeUntil = now() + NOTICE_TICKS;
    }

    // ================================================================== 동작

    private static boolean isDigit(int key) {
        return key >= 0 && key <= 9;
    }

    private String composeUnit() {
        String d = isParkingLobby() ? inputDong : lobbyDong();
        return d.isEmpty() ? input : d + "-" + input;
    }

    private void toggleLayout() {
        if (Arrays.equals(layout, DEFAULT_LAYOUT)) {
            List<Integer> digits = new ArrayList<>();
            for (int i = 0; i < 10; i++) digits.add(i);
            Collections.shuffle(digits, random);
            for (int i = 0; i < 10; i++) layout[i] = digits.get(i);
        } else {
            layout = DEFAULT_LAYOUT.clone();
        }
    }

    private void goIdle() {
        input = "";
        inputDong = "";
        unitInput = "";
        secret = "";
        dongStage = false;
        guardCall = false;
        bigLabel = "";
        editValue = "";
        pendingCard = "";
        noticeUntil = 0;
        layout = DEFAULT_LAYOUT.clone();
        setScreen(Screen.IDLE);
    }

    private void setScreen(Screen s) {
        screen = s;
        stateSince = now();
        if (s != Screen.CARD_MASTER && s != Screen.CARD_REG) noticeUntil = 0;
    }

    private void showMessage(String key, String arg, Screen returnTo) {
        msgKey = key;
        msgArg = arg == null ? "" : arg;
        msgReturn = returnTo;
        setScreen(Screen.MESSAGE);
    }

    private void leaveMessage() {
        if (msgReturn == Screen.IDLE) goIdle();
        else setScreen(msgReturn);
    }

    private void checkPassword() {
        if (secret.length() < 4) return;
        boolean ok;
        if (screen == Screen.COMMON_PASSWORD) {
            String common = cfg("common_password");
            String guard = cfg("guard_password");
            ok = (!common.isEmpty() && common.equals(secret)) || (!guard.isEmpty() && guard.equals(secret));
        } else {
            String pw = secret;
            ok = receiversOf(unitInput).stream().anyMatch(r -> r.checkDoorPassword(pw));
        }
        secret = "";
        if (ok) {
            failCount = 0;
            openDoor();
            goIdle();
            return;
        }
        // 틀리면 비밀번호 입력 화면을 다시 띄움. 3회 오류면 경비실 자동 호출
        failCount++;
        if (failCount >= 3) {
            failCount = 0;
            startGuardCall();
        } else {
            setScreen(screen);
        }
    }

    private void startGuardCall() {
        if (!(level instanceof ServerLevel)) return;
        guardCall = true;
        String guardNo = cfg("guard_no");
        String unit = guardNo.isEmpty() ? "경비실" : guardNo;
        if (receiversOf(unit).isEmpty() && !"경비실".equals(unit) && !receiversOf("경비실").isEmpty()) unit = "경비실";
        CallResult r = startCall(unit);
        if (r == CallResult.NO_UNIT) {
            showMessage("no_guard", "", Screen.IDLE);
            return;
        }
        afterStart(r, unit, guardNo.isEmpty() ? "경비" : guardNo);
    }

    private void startUnitCall(String unit, String label) {
        guardCall = false;
        afterStart(startCall(unit), unit, label);
    }

    private void afterStart(CallResult r, String unit, String label) {
        switch (r) {
            case OK -> {
                bigLabel = label;
                setScreen(Screen.CALLING);
                melody(1.0f);
            }
            case NO_UNIT -> showMessage("no_unit", unit, Screen.IDLE);
            case NO_SIGNAL -> showMessage("no_signal", unit, Screen.IDLE);
            case BUSY -> showMessage("busy", unit, Screen.IDLE);
        }
    }

    private void hangUpFromLobby() {
        hangUpFromCaller();
        goIdle();
    }

    /** 문열림: 연동된 문 + 레드스톤 신호 + 문열림 아이콘 (모두 문열림 시간 동안) */
    private void openDoor() {
        openDoors();
    }

    @Override
    public void openDoors() {
        super.openDoors();
        doorOpenUntil = now() + openTicks();
        melody(1.6f);
    }

    // ================================================================== 호출 이벤트

    @Override
    public String callerKey() {
        return "lobby";
    }

    @Override
    protected void onConnected() {
        setScreen(Screen.TALKING);
    }

    @Override
    protected void onDoorOpenedByReceiver() {
        goIdle();
    }

    @Override
    protected void onEnded(DoorStatus reason) {
        if (reason == DoorStatus.REJECTED || reason == DoorStatus.NO_ANSWER || reason == DoorStatus.TIMEOUT || reason == DoorStatus.ENDED) {
            showMessage(reason.name().toLowerCase(java.util.Locale.ROOT), "", Screen.IDLE);
        } else {
            goIdle();
        }
    }

    @Override
    public void syncScreens() {
        sync();
    }

    public void wake() {
        lastInput = now();
        keyLedUntil = lastInput + cfgInt("key_led_time") * 20L;
        if (!backlight) {
            backlight = true;
            sync();
        }
    }

    // ================================================================== 틱

    public static void serverTick(Level level, BlockPos pos, BlockState state, LobbyPhoneBlockEntity be) {
        long t = level.getGameTime();
        long inState = t - be.stateSince;
        boolean changed = false;
        be.tickCaller();
        // 호출이 끝났는데 화면이 그대로면 대기화면으로
        if ((be.screen == Screen.CALLING || be.screen == Screen.TALKING) && !be.isInCall()) {
            be.goIdle();
            changed = true;
        }

        switch (be.screen) {
            case MESSAGE -> {
                if (inState >= MESSAGE_TICKS) {
                    be.leaveMessage();
                    changed = true;
                }
            }
            case INPUT, PASSWORD, COMMON_PASSWORD, HELP -> {
                if (t - be.lastInput >= INPUT_TIMEOUT) {
                    be.goIdle();
                    changed = true;
                }
            }
            default -> {
                if (be.screen.isAdmin() && t - be.lastInput >= ADMIN_TIMEOUT) {
                    be.goIdle();
                    changed = true;
                }
            }
        }

        // 근접 센서: 사람이 다가오면 화면이 켜짐 (근접센서 데이타 40 = 약 2칸)
        if (t % 10 == 0 && be.cfgInt("prox_use") == 1) {
            double range = Math.max(0.5, be.cfgInt("prox_data") / 20.0);
            Vec3 c = Vec3.atCenterOf(pos);
            boolean near = level.players().stream().anyMatch(p -> p.distanceToSqr(c) <= range * range);
            if (near) {
                be.lastInput = Math.max(be.lastInput, t - 1);
                be.keyLedUntil = Math.max(be.keyLedUntil, t + be.cfgInt("key_led_time") * 20L);
                if (!be.backlight) {
                    be.backlight = true;
                    changed = true;
                }
            }
        }

        // LCD 백라이트: 대기화면에서 설정 시간 지나면 꺼짐
        boolean always = be.cfgInt("backlight_always") == 1;
        long blTicks = Math.max(1, be.cfgInt("backlight_time")) * 20L;
        if (!always && be.backlight && be.screen == Screen.IDLE && t - be.lastInput >= blTicks) {
            be.backlight = false;
            changed = true;
        } else if (always && !be.backlight) {
            be.backlight = true;
            changed = true;
        }

        // 문열림 아이콘이 꺼지는 순간 / 안내 문구가 끝나는 순간 다시 보내기
        if (t == be.doorOpenUntil || t == be.noticeUntil || t == be.keyLedUntil) changed = true;

        if (changed) be.sync();
    }

    // ================================================================== 소리

    private float vol(String key) {
        return cfgInt(key) / 5f;
    }

    private void keyTone() {
        if (level == null || cfgInt("key_tone_volume") <= 0) return;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5f * vol("key_tone_volume"), 1.9f);
    }

    private void digitTone(int digit) {
        if (level == null || cfgInt("digit_voice") != 1 || cfgInt("key_tone_volume") <= 0) return;
        float[] pitch = {0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.2f};
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.3f * vol("key_tone_volume"), pitch[digit]);
    }

    private void melody(float pitch) {
        if (level == null || cfgInt("melody_volume") <= 0) return;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f * vol("melody_volume"), pitch);
    }

    private void beep(boolean ok) {
        if (level == null) return;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6f, ok ? 1.6f : 0.5f);
    }

    // ================================================================== 클라이언트용 읽기

    public Screen getScreen() { return screen; }
    public String getInput() { return input; }
    public String getInputDong() { return inputDong; }
    public boolean isDongStage() { return dongStage; }
    public int getSecretLength() { return secretLen; }
    public String getBigLabel() { return bigLabel; }
    public boolean isGuardCall() { return guardCall; }
    public int[] getLayout() { return layout; }
    public int getDigitSize() { return digitSize; }
    public int getAdminPage() { return adminPage; }
    public int getEditItem() { return editItem; }
    public String getEditValue() { return editValue; }
    public String getEditDong() { return editDong; }
    public String getEditLine() { return editLine; }
    public String getMsgKey() { return msgKey; }
    public String getMsgArg() { return msgArg; }
    public long getStateSince() { return stateSince; }
    public boolean isBacklight() { return backlight; }
    public String getCardDong() { return cardDong; }
    public String getCardHo() { return cardHo; }
    public boolean isCardHoSet() { return cardHoSet; }
    public String getPendingCard() { return pendingCard; }
    public int getCardCount() { return cardCount; }

    public boolean isDoorOpen(long gameTime) { return gameTime < doorOpenUntil; }
    public boolean isKeyLedOn(long gameTime) { return cfgInt("key_led_always") == 1 || gameTime < keyLedUntil; }

    @Nullable
    public String activeNotice(long gameTime) { return gameTime < noticeUntil && !noticeKey.isEmpty() ? noticeKey : null; }
    public String getNoticeArg() { return noticeArg; }

    /** 설정 화면 표시값 (실제 기기처럼 비밀번호도 보임 - 관리자 화면에서만 동기화됨) */
    public String displaySetting(int page, int index) {
        LobbySettings.Item item = LobbySettings.item(page, index);
        if (item == null) return "";
        String v = cfg(item.key());
        return switch (item.kind()) {
            case LOBBY_NO -> LobbySettings.lobbyNoDisplay(v);
            case DIGITS -> v.isEmpty() && item.key().equals("guard_no") ? "경비실" : v;
            case PASSWORD -> v.isEmpty() ? "-" : v;
            default -> v;
        };
    }

    // ================================================================== 동기화 / 저장

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void writeSync(CompoundTag tag) {
        tag.putInt("Screen", screen.ordinal());
        tag.putString("Input", input);
        tag.putString("InputDong", inputDong);
        tag.putBoolean("DongStage", dongStage);
        tag.putInt("SecretLen", secret.length());
        tag.putString("Big", bigLabel);
        tag.putBoolean("Guard", guardCall);
        tag.putIntArray("Layout", layout);
        tag.putInt("DigitSize", digitSize);
        tag.putInt("AdminPage", adminPage);
        tag.putInt("EditItem", editItem);
        LobbySettings.Item item = LobbySettings.item(adminPage, editItem);
        boolean maskEdit = item != null && item.kind() == LobbySettings.Kind.PASSWORD && screen != Screen.ADMIN_EDIT;
        tag.putString("EditValue", maskEdit ? "" : editValue);
        tag.putString("EditDong", editDong);
        tag.putString("EditLine", editLine);
        tag.putString("MsgKey", msgKey);
        tag.putString("MsgArg", msgArg);
        tag.putLong("Since", stateSince);
        tag.putBoolean("Backlight", backlight);
        tag.putLong("DoorUntil", doorOpenUntil);
        tag.putLong("LedUntil", keyLedUntil);
        tag.putBoolean("Connected", isConnected());
        tag.putBoolean("InCall", inCall);
        tag.putString("CardDong", cardDong);
        tag.putString("CardHo", cardHo);
        tag.putBoolean("CardHoSet", cardHoSet);
        tag.putString("Pending", pendingCard);
        tag.putString("Notice", noticeKey);
        tag.putString("NoticeArg", noticeArg);
        tag.putLong("NoticeUntil", noticeUntil);
        tag.putInt("Cards", cards.size());
        ListTag lines = new ListTag();
        for (IntercomLine l : log) {
            CompoundTag c = new CompoundTag();
            c.putString("S", l.side());
            c.putString("N", l.name());
            c.putString("T", l.text());
            lines.add(c);
        }
        tag.put("Log", lines);
        // 설정값: 비밀번호는 관리자 화면일 때만 보냄
        CompoundTag c = new CompoundTag();
        // 관리자 비밀번호 입력 화면에서는 보내지 않음 (인증 후에만)
        boolean admin = (screen.isAdmin() && screen != Screen.ADMIN_PASSWORD)
                || (screen == Screen.MESSAGE && msgReturn.isAdmin() && msgReturn != Screen.ADMIN_PASSWORD);
        for (LobbySettings.Item[] page : LobbySettings.PAGES) {
            for (LobbySettings.Item it : page) {
                if (it.kind() == LobbySettings.Kind.PASSWORD && !admin) continue;
                c.putString(it.key(), cfg(it.key()));
            }
        }
        tag.put("Cfg", c);
    }

    private void readSync(CompoundTag tag) {
        screen = Screen.byId(tag.getInt("Screen"));
        input = tag.getString("Input");
        inputDong = tag.getString("InputDong");
        dongStage = tag.getBoolean("DongStage");
        secretLen = tag.getInt("SecretLen");
        bigLabel = tag.getString("Big");
        guardCall = tag.getBoolean("Guard");
        int[] l = tag.getIntArray("Layout");
        layout = l.length == 10 ? l : DEFAULT_LAYOUT.clone();
        digitSize = tag.getInt("DigitSize");
        adminPage = tag.getInt("AdminPage");
        editItem = tag.getInt("EditItem");
        editValue = tag.getString("EditValue");
        editDong = tag.getString("EditDong");
        editLine = tag.getString("EditLine");
        msgKey = tag.getString("MsgKey");
        msgArg = tag.getString("MsgArg");
        stateSince = tag.getLong("Since");
        backlight = tag.getBoolean("Backlight");
        doorOpenUntil = tag.getLong("DoorUntil");
        keyLedUntil = tag.getLong("LedUntil");
        clientConnected = tag.getBoolean("Connected");
        inCall = tag.getBoolean("InCall");
        cardDong = tag.getString("CardDong");
        cardHo = tag.getString("CardHo");
        cardHoSet = tag.getBoolean("CardHoSet");
        pendingCard = tag.getString("Pending");
        noticeKey = tag.getString("Notice");
        noticeArg = tag.getString("NoticeArg");
        noticeUntil = tag.getLong("NoticeUntil");
        cardCount = tag.getInt("Cards");
        log.clear();
        ListTag lines = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag c = lines.getCompound(i);
            log.add(new IntercomLine(c.getString("S"), c.getString("N"), c.getString("T")));
        }
        CompoundTag c = tag.getCompound("Cfg");
        for (String k : c.getAllKeys()) cfg.put(k, c.getString(k));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeSync(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readSync(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) readSync(tag);
    }

    /** 클라이언트에서는 동기화된 값 */
    @Override
    public boolean isConnected() {
        return level != null && level.isClientSide ? clientConnected : super.isConnected();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag c = tag.getCompound("Settings");
        for (String k : c.getAllKeys()) {
            if (cfg.containsKey(k)) cfg.put(k, c.getString(k));
        }
        cards.clear();
        ListTag list = tag.getList("RfCards", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            cards.put(e.getString("Id"), new Card(e.getString("Type"), e.getString("Dong"), e.getString("Ho")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag c = new CompoundTag();
        cfg.forEach(c::putString);
        tag.put("Settings", c);
        ListTag list = new ListTag();
        cards.forEach((id, card) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Id", id);
            e.putString("Type", card.type());
            e.putString("Dong", card.dong());
            e.putString("Ho", card.ho());
            list.add(e);
        });
        tag.put("RfCards", list);
    }
}
