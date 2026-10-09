package com.qwerty.homenet.blockentity;

import com.qwerty.homenet.block.LobbyPhoneBlock;
import com.qwerty.homenet.data.UnitRegistry;
import com.qwerty.homenet.intercom.DoorStatus;
import com.qwerty.homenet.intercom.Intercom;
import com.qwerty.homenet.intercom.IntercomCaller;
import com.qwerty.homenet.intercom.IntercomLine;
import com.qwerty.homenet.lobby.LobbySettings;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 공동현관 로비폰 (공동 현관기).
 *
 * 화면 상태는 서버가 관리하고 블록엔티티 동기화로 클라이언트(GUI / 블록 정면 화면)에 전달한다.
 * 실제 기기처럼 같은 로비폰을 보는 사람은 모두 같은 화면을 본다.
 *
 * 설명서(ASTRO KLP-70D 공동 현관기) 동작:
 *  - 세대 번호 입력 → [호출] : 세대 호출 (대기 약 30초, 통화 약 3분)
 *  - [경비] : 경비실 호출
 *  - 세대 번호 입력 → [비밀번호 입력] → 4자리 → [확인] : 세대 비밀번호 문열기
 *  - 대기화면 [공동비밀번호 입력] → 4자리 → [확인] : 공동 비밀번호 문열기
 *  - 비밀번호 3회 오류 → 경비실 자동 호출
 *  - [보안] : 키패드 임의 배열 ↔ 기본 배열 (대기화면 복귀 시 기본 배열)
 *  - 대기화면 [취소] : 숫자 크기 작은 → 보통 → 큰
 *  - [도움말] : 사용방법
 *  - 관리자 모드: 대기화면에서 [호출]을 누른 다음 [경비]를 누르면 관리자 비밀번호 입력
 *    설정 화면: 번호로 항목 선택, ←/→ 페이지 이동, 편집 중 [호출]=지우기 [경비]=확인, [취소]=나가기
 */
public class LobbyPhoneBlockEntity extends BlockEntity implements IntercomCaller {

    // ------------------------------------------------------------------ 키 코드
    public static final int KEY_LEFT = 10;      // 키패드 왼쪽 아래 (도움말 / 취소 / 동 / ←)
    public static final int KEY_RIGHT = 11;     // 키패드 오른쪽 아래 (공동비밀번호입력 / 비밀번호입력 / 확인 / →)
    public static final int KEY_SECURITY = 12;  // 보안
    public static final int KEY_CALL = 13;      // 호출
    public static final int KEY_GUARD = 14;     // 경비
    public static final int KEY_CANCEL = 15;    // 취소
    public static final int KEY_MESSAGE = 16;   // (마인크래프트 전용) 통화 중 메시지

    public enum Screen {
        IDLE, INPUT, PASSWORD, COMMON_PASSWORD, CALLING, TALKING, HELP,
        ADMIN_PASSWORD, ADMIN_MENU, ADMIN_EDIT, MESSAGE;

        public static Screen byId(int id) {
            Screen[] v = values();
            return id >= 0 && id < v.length ? v[id] : IDLE;
        }
    }

    private static final int INPUT_TIMEOUT = 20 * 30;
    private static final int ADMIN_TIMEOUT = 20 * 60;
    private static final int BACKLIGHT_TIMEOUT = 20 * 30;
    private static final int MESSAGE_TICKS = 50;
    private static final int ADMIN_ARM_TICKS = 20 * 5;
    private static final int MAX_LOG = 6;
    private static final int[] DEFAULT_LAYOUT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};

    // ------------------------------------------------------------------ 설정 (저장)
    private String dong = "";
    private String guardNo = "";
    private int lobbyType = 0;                 // 0 일반, 1 주차
    private String systemPassword = "0000";
    private String commonPassword = "";
    private boolean commonPasswordUse = true;
    private int openTime = 3;
    private int keyVolume = 3;
    private int melodyVolume = 3;
    private boolean digitVoice = true;
    private boolean backlightAlways = false;
    private boolean keyLedAlways = true;
    private int ringTime = 30;
    private int talkTime = 3;

    // ------------------------------------------------------------------ 화면 상태 (동기화, 저장 안 함)
    private Screen screen = Screen.IDLE;
    private String input = "";          // 화면에 보이는 번호 입력
    private String inputDong = "";      // 주차 현관: 입력된 동
    private boolean dongStage;          // 주차 현관: 동 입력 단계
    private String unitInput = "";      // 비밀번호 화면으로 넘어갈 때의 세대 번호
    private String secret = "";         // 비밀번호 입력 (서버에만 있음, 길이만 동기화)
    private String bigLabel = "";       // 호출/통화 중 큰 글씨
    private boolean guardCall;
    private int[] layout = DEFAULT_LAYOUT.clone();
    private int digitSize = 2;
    private int adminPage;
    private int editItem = -1;
    private String editValue = "";
    private String msgKey = "";
    private String msgArg = "";
    private Screen msgReturn = Screen.IDLE;
    private long stateSince;
    private long lastInput;
    private boolean backlight = true;
    private boolean adminArmed;
    private long adminArmedAt;
    private int failCount;
    private final List<IntercomLine> log = new ArrayList<>();

    // 통화
    @Nullable
    private BlockPos target;
    private boolean connected;

    private final Random random = new Random();

    public LobbyPhoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOBBY_PHONE.get(), pos, state);
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    // ================================================================== 키 입력

    public void press(ServerPlayer player, int key, String text) {
        if (!(level instanceof ServerLevel)) return;
        long t = now();
        lastInput = t;
        backlight = true;
        if (key != KEY_MESSAGE) keyTone(key);

        if (key == KEY_MESSAGE) {
            if (screen == Screen.TALKING && connected) {
                WallpadBlockEntity w = wallpadAt(target);
                if (w != null) Intercom.say((ServerLevel) level, w, this, player, "lobby", text);
            }
            return;
        }

        // 관리자 모드 진입 순서: 대기화면에서 [호출] → [경비]
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
                // 메시지 중 키를 누르면 바로 넘어감
                if (key == KEY_CANCEL || key == KEY_LEFT) setScreen(msgReturn);
            }
        }
        sync();
    }

    private void pressIdle(int key, boolean armedNow) {
        if (isDigit(key)) {
            setScreen(Screen.INPUT);
            dongStage = lobbyType == 1;
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
                if (commonPasswordUse) {
                    secret = "";
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
                    // 주차 공동현관: [동] 버튼으로 동 확정
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
        if (secret.equals(systemPassword)) {
            adminPage = 0;
            setScreen(Screen.ADMIN_MENU);
            melody(1.2f);
        } else {
            showMessage("admin_wrong", "", Screen.IDLE);
        }
        secret = "";
    }

    private void pressAdminMenu(int key) {
        if (isDigit(key)) {
            if (key >= 1 && key <= 7 && LobbySettings.item(adminPage, key - 1) != null) {
                editItem = key - 1;
                LobbySettings.Item item = LobbySettings.item(adminPage, editItem);
                editValue = item.kind() == LobbySettings.Kind.PASSWORD ? "" : getSetting(adminPage, editItem);
                setScreen(Screen.ADMIN_EDIT);
            } else {
                showMessage("unsupported", "", Screen.ADMIN_MENU);
            }
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
        switch (key) {
            case KEY_CALL -> { // 호출 = 지우기
                if (!editValue.isEmpty()) editValue = editValue.substring(0, editValue.length() - 1);
            }
            case KEY_GUARD -> { // 경비 = 확인
                String err = LobbySettings.validate(item, editValue);
                if (err != null) {
                    showMessage(err, "", Screen.ADMIN_EDIT);
                } else {
                    setSetting(adminPage, editItem, editValue);
                    setChanged();
                    showMessage("saved", "", Screen.ADMIN_MENU);
                    melody(1.4f);
                }
            }
            case KEY_LEFT -> setScreen(Screen.ADMIN_MENU);
            case KEY_CANCEL -> setScreen(Screen.ADMIN_MENU);
            default -> {}
        }
    }

    // ================================================================== 동작

    private static boolean isDigit(int key) {
        return key >= 0 && key <= 9;
    }

    /** 세대 번호 문자열 (동-호) */
    private String composeUnit() {
        String d = lobbyType == 1 ? inputDong : dong;
        return d.isEmpty() ? input : d + "-" + input;
    }

    private void toggleLayout() {
        boolean isDefault = java.util.Arrays.equals(layout, DEFAULT_LAYOUT);
        if (isDefault) {
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
        layout = DEFAULT_LAYOUT.clone();
        setScreen(Screen.IDLE);
    }

    private void setScreen(Screen s) {
        screen = s;
        stateSince = now();
    }

    private void showMessage(String key, String arg, Screen returnTo) {
        msgKey = key;
        msgArg = arg == null ? "" : arg;
        msgReturn = returnTo;
        setScreen(Screen.MESSAGE);
    }

    private void checkPassword() {
        if (secret.length() < 4) return;
        if (screen == Screen.COMMON_PASSWORD) {
            if (!commonPassword.isEmpty() && commonPassword.equals(secret)) {
                openDoor();
                failCount = 0;
                showMessage("opened", "", Screen.IDLE);
            } else if (commonPassword.isEmpty()) {
                showMessage("no_common_pw", "", Screen.IDLE);
            } else {
                wrongPassword();
            }
            secret = "";
            return;
        }

        // 세대 비밀번호
        WallpadBlockEntity w = lookupWallpad(unitInput);
        if (w == null) {
            showMessage(level instanceof ServerLevel sl && UnitRegistry.get(sl).lookup(unitInput) == null ? "no_unit" : "no_signal", unitInput, Screen.IDLE);
        } else if (!w.hasDoorPassword()) {
            showMessage("no_unit_pw", unitInput, Screen.IDLE);
        } else if (w.checkDoorPassword(secret)) {
            openDoor();
            failCount = 0;
            showMessage("opened", "", Screen.IDLE);
        } else {
            wrongPassword();
        }
        secret = "";
    }

    private void wrongPassword() {
        failCount++;
        if (failCount >= 3) {
            failCount = 0;
            showMessage("wrong_pw_guard", "", Screen.IDLE);
            startGuardCall();
        } else {
            showMessage("wrong_pw", failCount + "/3", screen);
        }
    }

    @Nullable
    private WallpadBlockEntity lookupWallpad(String unit) {
        if (!(level instanceof ServerLevel sl) || unit.isEmpty()) return null;
        BlockPos pos = UnitRegistry.get(sl).lookup(unit);
        return wallpadAt(pos);
    }

    @Nullable
    private WallpadBlockEntity wallpadAt(@Nullable BlockPos pos) {
        if (pos == null || level == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof WallpadBlockEntity w ? w : null;
    }

    private void startUnitCall(String unit, String label) {
        guardCall = false;
        startCall(unit, label);
    }

    private void startGuardCall() {
        if (!(level instanceof ServerLevel sl)) return;
        guardCall = true;
        UnitRegistry reg = UnitRegistry.get(sl);
        String unit = guardNo.isEmpty() ? "경비실" : guardNo;
        if (reg.lookup(unit) == null) unit = "경비실";
        if (reg.lookup(unit) == null) {
            showMessage("no_guard", "", Screen.IDLE);
            return;
        }
        startCall(unit, guardNo.isEmpty() ? "경비" : guardNo);
    }

    private void startCall(String unit, String label) {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos pos = UnitRegistry.get(sl).lookup(unit);
        if (pos == null) {
            showMessage("no_unit", unit, Screen.IDLE);
            return;
        }
        WallpadBlockEntity w = wallpadAt(pos);
        if (w == null) {
            showMessage("no_signal", unit, Screen.IDLE);
            return;
        }
        if (!w.startRinging(worldPosition, callerKey())) {
            showMessage("busy", unit, Screen.IDLE);
            return;
        }
        target = pos;
        connected = false;
        bigLabel = label;
        log.clear();
        setScreen(Screen.CALLING);
        melody(1.0f);
    }

    private void hangUpFromLobby() {
        WallpadBlockEntity w = wallpadAt(target);
        if (w != null && worldPosition.equals(w.getCaller())) w.endFromDoor();
        target = null;
        connected = false;
        goIdle();
    }

    private void openDoor() {
        if (level instanceof ServerLevel sl) {
            LobbyPhoneBlock.pulse(sl, worldPosition, openTime * 20);
            melody(1.6f);
        }
    }

    // ================================================================== IntercomCaller

    @Override
    public String callerKey() {
        return "lobby";
    }

    @Override
    public void onAnswered() {
        connected = true;
        setScreen(Screen.TALKING);
        sync();
    }

    @Override
    public void onDoorOpened() {
        target = null;
        connected = false;
        openDoor();
        showMessage("opened", "", Screen.IDLE);
        sync();
    }

    @Override
    public void onCallEnded(DoorStatus reason) {
        target = null;
        connected = false;
        showMessage(reason.name().toLowerCase(java.util.Locale.ROOT), "", Screen.IDLE);
        sync();
    }

    @Override
    public void addLine(IntercomLine line) {
        log.add(line);
        while (log.size() > MAX_LOG) log.remove(0);
    }

    @Override
    public void syncScreens() {
        sync();
    }

    /** 블록이 부서질 때 */
    public void onBroken() {
        if (target != null) {
            WallpadBlockEntity w = wallpadAt(target);
            if (w != null && worldPosition.equals(w.getCaller())) w.endFromDoor();
            target = null;
        }
    }

    /** 플레이어가 기기를 우클릭 (화면 켜기) */
    public void wake() {
        lastInput = now();
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

        switch (be.screen) {
            case MESSAGE -> {
                if (inState >= MESSAGE_TICKS) {
                    Screen back = be.msgReturn;
                    if (back == Screen.IDLE) be.goIdle();
                    else be.setScreen(back);
                    changed = true;
                }
            }
            case INPUT, PASSWORD, COMMON_PASSWORD, HELP -> {
                if (t - be.lastInput >= INPUT_TIMEOUT) {
                    be.goIdle();
                    changed = true;
                }
            }
            case ADMIN_PASSWORD, ADMIN_MENU, ADMIN_EDIT -> {
                if (t - be.lastInput >= ADMIN_TIMEOUT) {
                    be.goIdle();
                    changed = true;
                }
            }
            case CALLING -> {
                if (inState >= be.ringTime * 20L) {
                    WallpadBlockEntity w = be.wallpadAt(be.target);
                    if (w != null && pos.equals(w.getCaller())) w.endFromDoor();
                    be.target = null;
                    be.showMessage("no_answer", "", Screen.IDLE);
                    changed = true;
                } else if (inState % 20 == 10 && !be.stillLinked()) {
                    be.target = null;
                    be.showMessage("no_signal", "", Screen.IDLE);
                    changed = true;
                }
            }
            case TALKING -> {
                if (inState >= be.talkTime * 1200L) {
                    WallpadBlockEntity w = be.wallpadAt(be.target);
                    if (w != null && pos.equals(w.getCaller())) w.hangUp(DoorStatus.TIMEOUT);
                    be.target = null;
                    be.connected = false;
                    be.showMessage("timeout", "", Screen.IDLE);
                    changed = true;
                } else if (inState % 20 == 10 && !be.stillLinked()) {
                    be.target = null;
                    be.connected = false;
                    be.showMessage("ended", "", Screen.IDLE);
                    changed = true;
                }
            }
            default -> {}
        }

        // LCD 백라이트: 대기화면에서 30초 지나면 꺼짐 (상시 ON 설정 아니면)
        if (!be.backlightAlways && be.backlight && be.screen == Screen.IDLE && t - be.lastInput >= BACKLIGHT_TIMEOUT) {
            be.backlight = false;
            changed = true;
        } else if (be.backlightAlways && !be.backlight) {
            be.backlight = true;
            changed = true;
        }

        if (changed) be.sync();
    }

    private boolean stillLinked() {
        WallpadBlockEntity w = wallpadAt(target);
        return w != null && worldPosition.equals(w.getCaller()) && w.isBusy();
    }

    // ================================================================== 소리

    private void keyTone(int key) {
        if (level == null || keyVolume <= 0) return;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS,
                0.15f * keyVolume, 1.9f);
    }

    private void digitTone(int digit) {
        if (level == null || !digitVoice || keyVolume <= 0) return;
        float[] pitch = {0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.2f};
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS,
                0.08f * keyVolume, pitch[digit]);
    }

    private void melody(float pitch) {
        if (level == null || melodyVolume <= 0) return;
        SoundEvent s = SoundEvents.NOTE_BLOCK_CHIME.value();
        level.playSound(null, worldPosition, s, SoundSource.BLOCKS, 0.15f * melodyVolume, pitch);
    }

    // ================================================================== 설정 접근

    public String getSetting(int page, int index) {
        return switch (page * 10 + index) {
            case 0 -> dong;
            case 1 -> guardNo;
            case 2 -> String.valueOf(lobbyType);
            case 3 -> systemPassword;
            case 4 -> commonPassword;
            case 5 -> commonPasswordUse ? "1" : "0";
            case 6 -> String.valueOf(openTime);
            case 10 -> String.valueOf(keyVolume);
            case 11 -> String.valueOf(melodyVolume);
            case 12 -> digitVoice ? "1" : "0";
            case 13 -> backlightAlways ? "1" : "0";
            case 14 -> keyLedAlways ? "1" : "0";
            case 15 -> String.valueOf(ringTime);
            case 16 -> String.valueOf(talkTime);
            default -> "";
        };
    }

    private void setSetting(int page, int index, String v) {
        int n = 0;
        try {
            n = v.isEmpty() ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException ignored) {
        }
        switch (page * 10 + index) {
            case 0 -> dong = v;
            case 1 -> guardNo = v;
            case 2 -> lobbyType = n;
            case 3 -> systemPassword = v;
            case 4 -> commonPassword = v;
            case 5 -> commonPasswordUse = n == 1;
            case 6 -> openTime = n;
            case 10 -> keyVolume = n;
            case 11 -> melodyVolume = n;
            case 12 -> digitVoice = n == 1;
            case 13 -> backlightAlways = n == 1;
            case 14 -> keyLedAlways = n == 1;
            case 15 -> ringTime = n;
            case 16 -> talkTime = n;
            default -> {}
        }
    }

    // ================================================================== 클라이언트용 읽기

    public Screen getScreen() { return screen; }
    public String getInput() { return input; }
    public String getInputDong() { return inputDong; }
    public boolean isDongStage() { return dongStage; }
    public String getUnitInput() { return unitInput; }
    public int getSecretLength() { return secretLen; }
    public String getBigLabel() { return bigLabel; }
    public boolean isGuardCall() { return guardCall; }
    public int[] getLayout() { return layout; }
    public int getDigitSize() { return digitSize; }
    public int getAdminPage() { return adminPage; }
    public int getEditItem() { return editItem; }
    public String getEditValue() { return editValue; }
    public String getMsgKey() { return msgKey; }
    public String getMsgArg() { return msgArg; }
    public long getStateSince() { return stateSince; }
    public boolean isBacklight() { return backlight; }
    public boolean isConnected() { return connected; }
    public List<IntercomLine> getLog() { return log; }
    public String getDong() { return dong; }
    public int getLobbyType() { return lobbyType; }
    public boolean isCommonPasswordUse() { return commonPasswordUse; }
    public boolean isKeyLedAlways() { return keyLedAlways; }
    public int getTalkTime() { return talkTime; }
    public int getRingTime() { return ringTime; }

    /** 설정 화면 표시값 (비밀번호는 **** / 미설정) */
    public String displaySetting(int page, int index) {
        LobbySettings.Item item = LobbySettings.item(page, index);
        if (item == null) return "";
        if (item.kind() == LobbySettings.Kind.PASSWORD) {
            boolean set = index == 3 ? hasSystemPw : hasCommonPw;
            return set ? "****" : "-";
        }
        String v = clientSettings[page * 7 + index];
        return v == null ? "" : v;
    }

    // 클라이언트에 동기화된 값
    private int secretLen;
    private boolean hasSystemPw = true;
    private boolean hasCommonPw;
    private final String[] clientSettings = new String[14];

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
        tag.putString("UnitInput", unitInput);
        tag.putInt("SecretLen", secret.length());
        tag.putString("Big", bigLabel);
        tag.putBoolean("Guard", guardCall);
        tag.putIntArray("Layout", layout);
        tag.putInt("DigitSize", digitSize);
        tag.putInt("AdminPage", adminPage);
        tag.putInt("EditItem", editItem);
        LobbySettings.Item item = LobbySettings.item(adminPage, editItem);
        boolean maskEdit = item != null && item.kind() == LobbySettings.Kind.PASSWORD;
        tag.putString("EditValue", maskEdit ? "*".repeat(editValue.length()) : editValue);
        tag.putString("MsgKey", msgKey);
        tag.putString("MsgArg", msgArg);
        tag.putLong("Since", stateSince);
        tag.putBoolean("Backlight", backlight);
        tag.putBoolean("Connected", connected);
        ListTag lines = new ListTag();
        for (IntercomLine l : log) {
            CompoundTag c = new CompoundTag();
            c.putString("S", l.side());
            c.putString("N", l.name());
            c.putString("T", l.text());
            lines.add(c);
        }
        tag.put("Log", lines);
        // 설정 표시값 (비밀번호 제외)
        ListTag settings = new ListTag();
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 7; i++) {
                LobbySettings.Item it = LobbySettings.item(p, i);
                boolean secretItem = it != null && it.kind() == LobbySettings.Kind.PASSWORD;
                settings.add(StringTag.valueOf(secretItem ? "" : getSetting(p, i)));
            }
        }
        tag.put("Settings", settings);
        tag.putBoolean("HasSysPw", !systemPassword.isEmpty());
        tag.putBoolean("HasCommonPw", !commonPassword.isEmpty());
    }

    private void readSync(CompoundTag tag) {
        screen = Screen.byId(tag.getInt("Screen"));
        input = tag.getString("Input");
        inputDong = tag.getString("InputDong");
        dongStage = tag.getBoolean("DongStage");
        unitInput = tag.getString("UnitInput");
        secretLen = tag.getInt("SecretLen");
        bigLabel = tag.getString("Big");
        guardCall = tag.getBoolean("Guard");
        int[] l = tag.getIntArray("Layout");
        layout = l.length == 10 ? l : DEFAULT_LAYOUT.clone();
        digitSize = tag.getInt("DigitSize");
        adminPage = tag.getInt("AdminPage");
        editItem = tag.getInt("EditItem");
        editValue = tag.getString("EditValue");
        msgKey = tag.getString("MsgKey");
        msgArg = tag.getString("MsgArg");
        stateSince = tag.getLong("Since");
        backlight = tag.getBoolean("Backlight");
        connected = tag.getBoolean("Connected");
        log.clear();
        ListTag lines = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag c = lines.getCompound(i);
            log.add(new IntercomLine(c.getString("S"), c.getString("N"), c.getString("T")));
        }
        ListTag settings = tag.getList("Settings", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(14, settings.size()); i++) clientSettings[i] = settings.getString(i);
        // 자주 쓰는 설정은 필드에도 반영 (화면 그리기용)
        dong = settingOr(0, dong);
        lobbyType = parse(settingOr(2, "0"));
        commonPasswordUse = "1".equals(settingOr(5, "1"));
        keyLedAlways = "1".equals(settingOr(11, "1"));
        ringTime = parse(settingOr(12, "30"));
        talkTime = parse(settingOr(13, "3"));
        hasSystemPw = tag.getBoolean("HasSysPw");
        hasCommonPw = tag.getBoolean("HasCommonPw");
    }

    private String settingOr(int idx, String def) {
        String v = clientSettings[idx];
        return v == null ? def : v;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!tag.contains("Cfg")) return;
        CompoundTag c = tag.getCompound("Cfg");
        dong = c.getString("Dong");
        guardNo = c.getString("GuardNo");
        lobbyType = c.getInt("Type");
        systemPassword = c.contains("SysPw") ? c.getString("SysPw") : "0000";
        commonPassword = c.getString("CommonPw");
        commonPasswordUse = !c.contains("CommonUse") || c.getBoolean("CommonUse");
        openTime = c.contains("OpenTime") ? c.getInt("OpenTime") : 3;
        keyVolume = c.contains("KeyVol") ? c.getInt("KeyVol") : 3;
        melodyVolume = c.contains("MelVol") ? c.getInt("MelVol") : 3;
        digitVoice = !c.contains("DigitVoice") || c.getBoolean("DigitVoice");
        backlightAlways = c.getBoolean("Backlight");
        keyLedAlways = !c.contains("KeyLed") || c.getBoolean("KeyLed");
        ringTime = c.contains("Ring") ? c.getInt("Ring") : 30;
        talkTime = c.contains("Talk") ? c.getInt("Talk") : 3;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag c = new CompoundTag();
        c.putString("Dong", dong);
        c.putString("GuardNo", guardNo);
        c.putInt("Type", lobbyType);
        c.putString("SysPw", systemPassword);
        c.putString("CommonPw", commonPassword);
        c.putBoolean("CommonUse", commonPasswordUse);
        c.putInt("OpenTime", openTime);
        c.putInt("KeyVol", keyVolume);
        c.putInt("MelVol", melodyVolume);
        c.putBoolean("DigitVoice", digitVoice);
        c.putBoolean("Backlight", backlightAlways);
        c.putBoolean("KeyLed", keyLedAlways);
        c.putInt("Ring", ringTime);
        c.putInt("Talk", talkTime);
        tag.put("Cfg", c);
    }
}
