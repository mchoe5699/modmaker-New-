"""
공동현관 로비폰 텍스처 생성기.

치수 기준: 실제 기기 248(W) x 279(H) x 50.2(D) mm (설명서 제품 사양).
좌표는 모두 "기기 단위"(=mm)로 적고, 출력 해상도에 맞춰 스케일한다.
같은 좌표를 Java 쪽 LobbyLayout 에서도 쓴다.

출력:
  textures/block/lobby_phone_front.png  256x256  (블록 정면, 기기는 x 14.2~241.8 에 그려짐)
  textures/block/lobby_phone_side.png   16x64    (좌우 크롬)
  textures/block/lobby_phone_edge.png   64x64    (위/아래 면)
  textures/gui/lobby_phone.png          496x558  (GUI 배경, LCD 제외)
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 248, 279
ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/qwertys_homenet/textures"
KR_BOLD = ("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 1)
KR_REG = ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 1)
DIGIT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# ---- 레이아웃 (기기 단위) ----
CHROME = 15                     # 좌우 크롬 띠 폭
GLASS = (43, 34, 205, 237)      # 검은 유리 패널
LCD = (55, 45, 142, 199)        # 7인치 LCD
INFO = (61, 58, 135, 101)       # 시계/번호 표시 박스
KEY_X0, KEY_W, KEY_GAP = 57, 26.3, 1.3
KEY_ROWS = (120, 141, 162, 183)
KEY_H = 16
CAMERA = (174, 60, 14)          # cx, cy, r
LED = (156, 80, 190, 113)
RIGHT_KEYS_Y = (133, 154, 174.5, 195)   # 보안 / 호출 / 경비 / 취소
RIGHT_KEY_X = (151, 199)
CARD = (64, 207, 140, 235)
SPEAKER = (CHROME, 264, W - CHROME, 276)

NAVY = (17, 21, 42)
NAVY_DOT = (29, 35, 64)
GLASS_C = (6, 8, 14)
GLASS_EDGE = (46, 52, 66)
ICON = (222, 226, 236)


def font(spec, size):
    if isinstance(spec, tuple):
        return ImageFont.truetype(spec[0], size, index=spec[1])
    return ImageFont.truetype(spec, size)


def draw_device(s, with_lcd):
    """기기 하나를 s px/단위 로 그린 RGBA 이미지 (4배 슈퍼샘플 후 축소)."""
    ss = 4
    k = s * ss
    img = Image.new("RGBA", (round(W * k), round(H * k)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def R(x1, y1, x2, y2):
        return [x1 * k, y1 * k, x2 * k, y2 * k]

    # 몸체 (남색 + 미세 점무늬)
    d.rectangle(R(0, 0, W, H), fill=NAVY)
    step = 3
    for y in range(0, H, step):
        for x in range(CHROME, W - CHROME, step):
            ox = 1.5 if (y // step) % 2 else 0
            d.ellipse(R(x + ox + 0.6, y + 0.6, x + ox + 1.5, y + 1.5), fill=NAVY_DOT)

    # 좌우 크롬 띠: 바깥 밝게 → 안쪽 어둡게
    def chrome(x0, mirror):
        stops = [(0.0, (70, 74, 82)), (0.12, (210, 214, 222)), (0.45, (238, 240, 244)),
                 (0.75, (150, 156, 166)), (0.92, (60, 64, 72)), (1.0, (24, 26, 32))]
        steps = int(CHROME * k)
        for i in range(steps):
            t = i / max(1, steps - 1)
            if mirror:
                t = 1 - t
            for j in range(len(stops) - 1):
                if stops[j][0] <= t <= stops[j + 1][0]:
                    a, ca = stops[j]
                    b, cb = stops[j + 1]
                    f = (t - a) / (b - a)
                    c = tuple(round(ca[n] + (cb[n] - ca[n]) * f) for n in range(3))
                    break
            x = x0 * k + i
            d.line([x, 0, x, H * k], fill=c)
    chrome(0, False)
    chrome(W - CHROME, True)

    # 상단 로고 자리 (모드 이름)
    d.text((58 * k, 18 * k), "HOMENET", font=font(DIGIT, round(5.2 * k)), fill=(196, 200, 212))

    # 유리 패널
    d.rounded_rectangle(R(*GLASS), radius=1.5 * k, fill=GLASS_C, outline=GLASS_EDGE, width=max(1, round(0.6 * k)))

    # 카메라
    cx, cy, r = CAMERA
    d.ellipse(R(cx - r, cy - r, cx + r, cy + r), fill=(22, 25, 32))
    d.ellipse(R(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), fill=(14, 16, 22))
    d.ellipse(R(cx - 6, cy - 6, cx + 6, cy + 6), fill=(30, 34, 44))
    d.ellipse(R(cx - 3.5, cy - 3.5, cx + 3.5, cy + 3.5), fill=(4, 5, 8))
    d.ellipse(R(cx - 2.2, cy - 2.6, cx - 0.8, cy - 1.2), fill=(90, 110, 170))

    # 백색 LED 격자
    d.rectangle(R(*LED), fill=(118, 116, 236))
    n = 10
    for i in range(1, n):
        x = LED[0] + (LED[2] - LED[0]) * i / n
        y = LED[1] + (LED[3] - LED[1]) * i / n
        d.line(R(x, LED[1], x, LED[3]), fill=(92, 90, 205), width=max(1, round(0.35 * k)))
        d.line(R(LED[0], y, LED[2], y), fill=(92, 90, 205), width=max(1, round(0.35 * k)))

    # 우측 터치키: 아이콘 + 글자 + 밑줄
    lab = font(KR_REG, round(4.2 * k))
    labels = ["보  안", "호  출", "경  비", "취  소"]
    for i, ky in enumerate(RIGHT_KEYS_Y):
        ix = 162
        if i == 0:   # 자물쇠
            d.rounded_rectangle(R(ix - 4, ky - 1.5, ix + 4, ky + 5), radius=0.8 * k, outline=ICON, width=round(0.9 * k))
            d.arc(R(ix - 2.8, ky - 6, ix + 2.8, ky + 0.5), 180, 360, fill=ICON, width=round(0.9 * k))
            d.ellipse(R(ix - 0.9, ky + 0.8, ix + 0.9, ky + 2.6), fill=ICON)
        elif i == 1:  # 종(호출)
            d.chord(R(ix - 4.5, ky - 4.5, ix + 4.5, ky + 5), 180, 360, outline=ICON, width=round(0.9 * k))
            d.line(R(ix - 5, ky + 2.5, ix + 5, ky + 2.5), fill=ICON, width=round(0.9 * k))
            d.line(R(ix - 4.5, ky + 0.25, ix - 4.5, ky + 2.5), fill=ICON, width=round(0.9 * k))
            d.line(R(ix + 4.5, ky + 0.25, ix + 4.5, ky + 2.5), fill=ICON, width=round(0.9 * k))
            d.ellipse(R(ix - 1, ky + 3, ix + 1, ky + 5), fill=ICON)
        elif i == 2:  # 경비 모자
            d.chord(R(ix - 5, ky - 4.5, ix + 5, ky + 2.5), 180, 360, outline=ICON, width=round(0.9 * k))
            d.line(R(ix - 5.5, ky - 1, ix + 5.5, ky - 1), fill=ICON, width=round(0.9 * k))
            d.polygon([(ix * k - 1.4 * k, (ky - 2.2) * k), (ix * k + 1.4 * k, (ky - 2.2) * k), (ix * k, (ky - 3.8) * k)], fill=ICON)
            d.arc(R(ix - 3.5, ky - 2, ix + 3.5, ky + 5), 20, 160, fill=ICON, width=round(0.9 * k))
        else:         # 취소 X (노란빛)
            yc = (236, 206, 92)
            d.line(R(ix - 3.6, ky - 3.6, ix + 3.6, ky + 3.6), fill=yc, width=round(1.3 * k))
            d.line(R(ix - 3.6, ky + 3.6, ix + 3.6, ky - 3.6), fill=yc, width=round(1.3 * k))
        d.text((175 * k, (ky - 2.6) * k), labels[i], font=lab, fill=(206, 210, 222))
        d.line(R(RIGHT_KEY_X[0], ky + 8, RIGHT_KEY_X[1], ky + 8), fill=(58, 64, 80), width=max(1, round(0.4 * k)))

    # RF 카드 인식부
    ln = (126, 132, 148)
    w = max(1, round(0.5 * k))
    d.line(R(GLASS[0] + 3, 234, 68, 234), fill=ln, width=w)
    d.line(R(68, 234, 77, 213), fill=ln, width=w)
    d.line(R(77, 213, 124, 213), fill=ln, width=w)
    d.line(R(124, 213, 130, 224), fill=ln, width=w)
    d.line(R(130, 224, GLASS[2] - 6, 224), fill=ln, width=w)
    d.rounded_rectangle(R(91, 217, 110, 228), radius=1 * k, outline=(220, 224, 232), width=round(0.6 * k))
    d.text((93 * k, 219.5 * k), "CARD", font=font(DIGIT, round(3.2 * k)), fill=(220, 224, 232))
    d.arc(R(105, 221, 115, 233), 200, 330, fill=(220, 224, 232), width=round(0.6 * k))
    d.text((138 * k, 214.5 * k), "좌측에 출입 카드를 대 주세요", font=font(KR_REG, round(2.4 * k)), fill=(170, 176, 190))
    d.rectangle(R(166, 226, 172, 232), fill=(18, 20, 26), outline=(90, 94, 104))
    d.rectangle(R(182, 226, 188, 232), fill=(18, 20, 26), outline=(90, 94, 104))
    d.ellipse(R(183.5, 212.5, 186.5, 215.5), fill=(54, 58, 68))

    # 하단 스피커 그릴
    d.rectangle(R(*SPEAKER), fill=(16, 18, 30))
    x = SPEAKER[0] + 1.5
    while x < SPEAKER[2] - 1:
        d.line(R(x, SPEAKER[1] + 1.5, x, SPEAKER[3] - 1.5), fill=(70, 74, 96), width=max(1, round(0.6 * k)))
        x += 2.2

    if with_lcd:
        draw_lcd_idle(d, k)

    return img.resize((round(W * s), round(H * s)), Image.LANCZOS)


def draw_lcd_idle(d, k):
    """블록 정면용 대기화면 (시계 글자는 렌더러가 그림)"""
    x1, y1, x2, y2 = LCD
    for i in range(int((y2 - y1) * k)):
        t = i / ((y2 - y1) * k)
        c = (round(34 - 12 * t), round(52 - 18 * t), round(140 - 46 * t))
        d.line([x1 * k, y1 * k + i, x2 * k, y1 * k + i], fill=c)
    # 상단 아이콘
    d.ellipse([136 * k, 47.5 * k, 141 * k, 52.5 * k], fill=(70, 110, 220), outline=(200, 214, 250), width=max(1, round(0.4 * k)))
    # 정보 박스
    bx = INFO
    d.rounded_rectangle([bx[0] * k, bx[1] * k, bx[2] * k, bx[3] * k], radius=2 * k, fill=(236, 240, 250))
    # 키패드
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "도움말", "0", "공동비밀번호\n입력"]
    fd = font(DIGIT, round(11 * k))
    fs = font(KR_BOLD, round(4.0 * k))
    fs2 = font(KR_BOLD, round(3.0 * k))
    for i, label in enumerate(keys):
        col, row = i % 3, i // 3
        kx = KEY_X0 + col * (KEY_W + KEY_GAP)
        ky = KEY_ROWS[row]
        box = [kx * k, ky * k, (kx + KEY_W) * k, (ky + KEY_H) * k]
        for j in range(int(KEY_H * k)):
            t = j / (KEY_H * k)
            c = (round(78 - 40 * t), round(104 - 54 * t), round(206 - 80 * t))
            d.line([box[0], box[1] + j, box[2], box[1] + j], fill=c)
        d.rectangle(box, outline=(24, 34, 92), width=max(1, round(0.4 * k)))
        cx = (kx + KEY_W / 2) * k
        cy = (ky + KEY_H / 2) * k
        f = fd if len(label) == 1 else (fs2 if "\n" in label else fs)
        d.multiline_text((cx, cy), label, font=f, fill=(255, 255, 255), anchor="mm", align="center", spacing=0)


def main():
    blk = os.path.join(ROOT, "block")
    gui = os.path.join(ROOT, "gui")
    os.makedirs(blk, exist_ok=True)
    os.makedirs(gui, exist_ok=True)

    # 블록 정면 256x256: 높이 279단위 → 256px, 가로는 가운데 정렬
    s = 256 / H
    dev = draw_device(s, True)
    front = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    front.paste(dev, (round((256 - dev.width) / 2), 0))
    front.save(os.path.join(blk, "lobby_phone_front.png"))

    # GUI 배경 2배 (LCD는 코드로 그림)
    draw_device(2.0, False).save(os.path.join(gui, "lobby_phone.png"))

    # 좌우 크롬 면
    side = Image.new("RGBA", (16, 64))
    sd = ImageDraw.Draw(side)
    for x in range(16):
        t = x / 15
        v = round(150 + 90 * (1 - abs(t - 0.4) * 1.6))
        sd.line([x, 0, x, 63], fill=(v, v + 2, v + 6))
    side.save(os.path.join(blk, "lobby_phone_side.png"))

    # 위/아래 면: 가운데 남색, 양 끝 크롬
    edge = Image.new("RGBA", (64, 64), NAVY + (255,))
    ed = ImageDraw.Draw(edge)
    cw = 64 * (14.222 / 16) * CHROME / W   # 크롬 띠 폭(px)
    x0 = 64 * (0.889 / 16)
    ed.rectangle([0, 0, round(x0 + cw), 63], fill=(214, 218, 226))
    ed.rectangle([round(64 - x0 - cw), 0, 63, 63], fill=(214, 218, 226))
    edge.save(os.path.join(blk, "lobby_phone_edge.png"))
    print("ok", dev.size)


if __name__ == "__main__":
    main()
