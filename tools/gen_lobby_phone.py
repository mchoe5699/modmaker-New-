"""
공동현관 로비폰 텍스처 생성기 (v2).

기준: 실제 기기 248(W) x 279(H) x 50.2(D) mm, 사진 4장 + 영상 + 설명서.
모든 좌표는 "기기 단위"(=mm). 같은 좌표를 Java 쪽(LobbyPhoneScreen / LobbyPhoneRenderer)에서도 쓴다.

출력 (assets/qwertys_homenet/textures 아래):
  block/lobby_phone_front.png  512x512   블록 정면 (기기 전체를 정사각형에 늘려 그림 → 모델 UV 0~16 전체 사용)
  block/lobby_phone_side.png   16x64     좌우 크롬
  block/lobby_phone_edge.png   64x64     위/아래/뒷면
  gui/lobby_phone.png          992x1116  GUI 배경 (4배, LCD 패턴과 키 모양 포함, 글자 제외)
  font/lcd_icons.png           64x32     LCD 아이콘 글리프 (네트워크, 문열림)
  item/rf_card.png             16x16     출입 카드
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

W, H = 248, 279
ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/qwertys_homenet"
TEX = os.path.join(ROOT, "textures")
LCD_FONT = os.path.join(ROOT, "font", "lcd.ttf")
KR_REG = ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 1)
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# ---------------------------------------------------------------- 레이아웃 (기기 단위)
CHROME = 15
GLASS = (43, 34, 205, 237)
LCD = (55, 45, 142, 199)
INFO = (61, 60, 135, 102)
NET_ICON = (134.8, 51.8)          # 네트워크 아이콘 중심
KEY_X0, KEY_W, KEY_STEP = 57, 27, 28
KEY_ROWS = (120, 141, 162, 183)
KEY_H = 16
CAMERA = (174, 60, 14)
LED = (156, 80, 190, 113)
RIGHT_KEYS_Y = (133, 154, 174.5, 195)
RIGHT_KEY_X = (151, 199)
SPEAKER = (CHROME, 264, W - CHROME, 276)

# 본체: 검은 바탕에 촘촘한 육각 점 무늬 (사진 7~11)
NAVY = (13, 14, 16)
NAVY_DOT = (38, 40, 45)
GLASS_C = (7, 8, 10)
GLASS_EDGE = (176, 180, 188)
ICON = (222, 226, 236)

# LCD 색 (영상 + 사진 기준)
LCD_BASE = (60, 76, 172)
LCD_TRI_LIGHT = (98, 116, 210)
LCD_TRI_DARK = (44, 56, 140)
KEY_TOP = (88, 108, 206)
KEY_BOTTOM = (40, 54, 138)
KEY_HILITE = (138, 156, 232)
KEY_GAP = (24, 32, 88)
INFO_TOP = (240, 242, 250)
INFO_BOTTOM = (206, 213, 236)
INFO_EDGE = (78, 88, 140)
DIGIT_DARK = (38, 48, 112)


def font(spec, size):
    if isinstance(spec, tuple):
        return ImageFont.truetype(spec[0], size, index=spec[1])
    return ImageFont.truetype(spec, size)


class Canvas:
    """기기 단위로 그리는 도우미 (kx, ky = 단위당 픽셀)"""

    def __init__(self, kx, ky):
        self.kx, self.ky = kx, ky
        self.img = Image.new("RGBA", (round(W * kx), round(H * ky)), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def R(self, x1, y1, x2, y2):
        return [x1 * self.kx, y1 * self.ky, x2 * self.kx - 1, y2 * self.ky - 1]

    def P(self, x, y):
        return (x * self.kx, y * self.ky)

    def w(self, units):
        return max(1, round(units * min(self.kx, self.ky)))

    def vgrad(self, x1, y1, x2, y2, c1, c2):
        h = max(1, round((y2 - y1) * self.ky))
        for i in range(h):
            t = i / max(1, h - 1)
            c = tuple(round(c1[j] + (c2[j] - c1[j]) * t) for j in range(3))
            self.d.line([x1 * self.kx, y1 * self.ky + i, x2 * self.kx - 1, y1 * self.ky + i], fill=c)


def draw_body(c):
    d = c.d
    d.rectangle(c.R(0, 0, W, H), fill=NAVY)
    def mesh(x1, y1, x2, y2, col, step=1.7):
        yy, row = y1, 0
        while yy < y2:
            xx = x1 + (step / 2 if row % 2 else 0)
            while xx < x2:
                d.ellipse(c.R(xx + 0.35, yy + 0.35, xx + 1.05, yy + 1.05), fill=col)
                xx += step
            yy += step * 0.866
            row += 1
    mesh(CHROME, 0, W - CHROME, H, NAVY_DOT)

    def chrome(x0, mirror):
        # 둥근 크롬 막대: 바깥 어두운 테 → 밝은 반사 → 회색 → 안쪽 반사 → 어두운 경계
        stops = [(0.0, (46, 48, 54)), (0.1, (150, 154, 162)), (0.28, (248, 249, 250)), (0.45, (222, 225, 230)),
                 (0.66, (128, 132, 140)), (0.84, (196, 200, 206)), (0.94, (90, 94, 100)), (1.0, (30, 32, 36))]
        steps = max(1, round(CHROME * c.kx))
        for i in range(steps):
            t = i / max(1, steps - 1)
            if mirror:
                t = 1 - t
            col = stops[-1][1]
            for j in range(len(stops) - 1):
                if stops[j][0] <= t <= stops[j + 1][0]:
                    a, ca = stops[j]
                    b, cb = stops[j + 1]
                    f = (t - a) / (b - a)
                    col = tuple(round(ca[n] + (cb[n] - ca[n]) * f) for n in range(3))
                    break
            x = x0 * c.kx + i
            d.line([x, 0, x, H * c.ky], fill=col)
    chrome(0, False)
    chrome(W - CHROME, True)

    # 로고 자리 (모드 이름)
    # 로고: 유리판 왼쪽 위 바로 위 (사진)
    d.text(c.P(GLASS[0] + 1, GLASS[1] - 9.5), "HOMENET", font=font(SANS, max(6, round(4.6 * c.ky))), fill=(232, 234, 238))

    d.rounded_rectangle(c.R(*GLASS), radius=1.5 * c.kx, fill=GLASS_C)
    mesh(GLASS[0] + 1, GLASS[1] + 1, GLASS[2] - 1, GLASS[3] - 1, (20, 21, 24))
    d.rounded_rectangle(c.R(*GLASS), radius=1.5 * c.kx, outline=GLASS_EDGE, width=c.w(0.5))

    cx, cy, r = CAMERA
    d.ellipse(c.R(cx - r, cy - r, cx + r, cy + r), fill=(22, 25, 32))
    d.ellipse(c.R(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), fill=(14, 16, 22))
    d.ellipse(c.R(cx - 6, cy - 6, cx + 6, cy + 6), fill=(30, 34, 44))
    d.ellipse(c.R(cx - 3.5, cy - 3.5, cx + 3.5, cy + 3.5), fill=(4, 5, 8))
    d.ellipse(c.R(cx - 2.2, cy - 2.6, cx - 0.8, cy - 1.2), fill=(90, 110, 170))

    # 카메라 아래 흰 사각창 (조명 / 근접 센서)
    d.rectangle(c.R(*LED), fill=(214, 218, 222))
    d.rectangle(c.R(LED[0] + 1, LED[1] + 1, LED[2] - 1, LED[3] - 1), fill=(232, 235, 238))
    d.rectangle(c.R(*LED), outline=(120, 124, 132), width=c.w(0.4))

    # 우측 터치키
    lab = font(KR_REG, max(6, round(4.0 * c.ky)))
    labels = ["보  안", "호  출", "경  비", "취  소"]
    lw = c.w(0.9)
    for i, ky in enumerate(RIGHT_KEYS_Y):
        ix = 162
        if i == 0:
            d.rounded_rectangle(c.R(ix - 4, ky - 1.5, ix + 4, ky + 5), radius=0.8 * c.kx, outline=ICON, width=lw)
            d.arc(c.R(ix - 2.8, ky - 6, ix + 2.8, ky + 0.5), 180, 360, fill=ICON, width=lw)
            d.ellipse(c.R(ix - 0.9, ky + 0.8, ix + 0.9, ky + 2.6), fill=ICON)
        elif i == 1:
            d.chord(c.R(ix - 4.5, ky - 4.5, ix + 4.5, ky + 5), 180, 360, outline=ICON, width=lw)
            d.line(c.R(ix - 5, ky + 2.5, ix + 5, ky + 2.51), fill=ICON, width=lw)
            d.line(c.R(ix - 4.5, ky + 0.25, ix - 4.49, ky + 2.5), fill=ICON, width=lw)
            d.line(c.R(ix + 4.5, ky + 0.25, ix + 4.51, ky + 2.5), fill=ICON, width=lw)
            d.ellipse(c.R(ix - 1, ky + 3, ix + 1, ky + 5), fill=ICON)
        elif i == 2:
            d.chord(c.R(ix - 5, ky - 4.5, ix + 5, ky + 2.5), 180, 360, outline=ICON, width=lw)
            d.line(c.R(ix - 5.5, ky - 1, ix + 5.5, ky - 0.99), fill=ICON, width=lw)
            d.polygon([c.P(ix - 1.4, ky - 2.2), c.P(ix + 1.4, ky - 2.2), c.P(ix, ky - 3.8)], fill=ICON)
            d.arc(c.R(ix - 3.5, ky - 2, ix + 3.5, ky + 5), 20, 160, fill=ICON, width=lw)
        else:
            yc = ICON
            d.line([c.P(ix - 3.6, ky - 3.6), c.P(ix + 3.6, ky + 3.6)], fill=yc, width=c.w(1.3))
            d.line([c.P(ix - 3.6, ky + 3.6), c.P(ix + 3.6, ky - 3.6)], fill=yc, width=c.w(1.3))
        d.text(c.P(175, ky - 2.6), labels[i], font=lab, fill=(206, 210, 222))
        d.line([c.P(RIGHT_KEY_X[0], ky + 8), c.P(RIGHT_KEY_X[1], ky + 8)], fill=(58, 64, 80), width=c.w(0.4))

    # RF 카드 인식부
    ln = (126, 132, 148)
    wl = c.w(0.5)
    pts = [(GLASS[0] + 3, 234), (68, 234), (77, 213), (124, 213), (130, 224), (GLASS[2] - 6, 224)]
    d.line([c.P(*p) for p in pts], fill=ln, width=wl)
    d.rounded_rectangle(c.R(91, 217, 110, 228), radius=1 * c.kx, outline=(220, 224, 232), width=c.w(0.6))
    d.text(c.P(93, 219.5), "CARD", font=font(SANS, max(6, round(3.0 * c.ky))), fill=(220, 224, 232))
    d.arc(c.R(105, 221, 115, 233), 200, 330, fill=(220, 224, 232), width=c.w(0.6))
    d.text(c.P(138, 214.5), "좌측에 출입 카드를 대 주세요", font=font(KR_REG, max(6, round(2.4 * c.ky))), fill=(170, 176, 190))
    d.rectangle(c.R(166, 226, 172, 232), fill=(18, 20, 26), outline=(90, 94, 104))
    d.rectangle(c.R(182, 226, 188, 232), fill=(18, 20, 26), outline=(90, 94, 104))
    d.ellipse(c.R(183.5, 212.5, 186.5, 215.5), fill=(54, 58, 68))

    # 하단 스피커 그릴: 검은 띠에 촘촘한 세로 홈 (사진)
    d.rectangle(c.R(*SPEAKER), fill=(6, 6, 8))
    x = SPEAKER[0] + 1.0
    while x < SPEAKER[2] - 1.0:
        d.rectangle(c.R(x, SPEAKER[1] + 1.5, x + 0.7, SPEAKER[3] - 1.5), fill=(58, 60, 66))
        x += 1.6


def draw_lcd_background(c):
    """LCD 배경: 사진처럼 작은 삼각형(나비 모양) 무늬"""
    x1, y1, x2, y2 = LCD
    # 무늬는 별도 레이어에 그린 뒤 LCD 사각형만 잘라 붙인다 (밖으로 넘치지 않게)
    layer = Canvas(c.kx / 1, c.ky / 1)
    d = layer.d
    d.rectangle(layer.R(x1, y1, x2, y2), fill=LCD_BASE)
    cell = 2.6
    row = 0
    y = y1
    while y < y2:
        x = x1 + (cell / 2 if row % 2 else 0) - cell
        while x < x2:
            # 좌우 삼각형 (밝게) + 위아래 삼각형 (어둡게) = 나비 무늬
            cx, cy = x + cell / 2, y + cell / 2
            d.polygon([layer.P(x, y), layer.P(cx, cy), layer.P(x, y + cell)], fill=LCD_TRI_LIGHT)
            d.polygon([layer.P(x + cell, y), layer.P(cx, cy), layer.P(x + cell, y + cell)], fill=LCD_TRI_LIGHT)
            d.polygon([layer.P(x, y), layer.P(cx, cy), layer.P(x + cell, y)], fill=LCD_TRI_DARK)
            x += cell
        y += cell
        row += 1
    # 살짝 위가 밝은 그라데이션
    over = Image.new("RGBA", layer.img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(over)
    h = round((y2 - y1) * c.ky)
    for i in range(h):
        t = i / max(1, h - 1)
        a = round(60 * (1 - t))
        od.line([x1 * c.kx, y1 * c.ky + i, x2 * c.kx - 1, y1 * c.ky + i], fill=(255, 255, 255, a))
    layer.img.alpha_composite(over)
    box = (round(x1 * c.kx), round(y1 * c.ky), round(x2 * c.kx), round(y2 * c.ky))
    c.img.paste(layer.img.crop(box), box[:2])
    # 네트워크 아이콘
    draw_net_icon(c.d, c.P(*NET_ICON), 3.3 * c.kx)
    c.d.rectangle(c.R(x1, y1, x2, y2), outline=(20, 26, 70), width=c.w(0.3))


def draw_net_icon(d, center, r):
    cx, cy = center
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(40, 86, 200), outline=(225, 232, 252), width=max(1, round(r * 0.18)))
    # 서버 모양 (가로줄 3개 박스)
    bw, bh = r * 0.95, r * 1.15
    d.rounded_rectangle([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], radius=r * 0.15,
                        outline=(240, 244, 255), width=max(1, round(r * 0.16)))
    for k in (-0.2, 0.15):
        yy = cy + bh * k
        d.line([cx - bw / 2, yy, cx + bw / 2, yy], fill=(240, 244, 255), width=max(1, round(r * 0.12)))


def draw_door_icon(d, center, r):
    cx, cy = center
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(46, 160, 88), outline=(225, 245, 232), width=max(1, round(r * 0.18)))
    # 열린 문: 문틀 + 비스듬한 문짝
    fw, fh = r * 0.9, r * 1.25
    d.rectangle([cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2], outline=(240, 255, 244), width=max(1, round(r * 0.14)))
    d.polygon([(cx - fw / 2, cy - fh / 2), (cx + fw * 0.1, cy - fh * 0.35), (cx + fw * 0.1, cy + fh * 0.62),
               (cx - fw / 2, cy + fh / 2)], fill=(240, 255, 244))
    d.ellipse([cx - fw * 0.05, cy, cx + fw * 0.05 + r * 0.05, cy + r * 0.12], fill=(46, 160, 88))


def draw_info_box(c):
    """정보 박스 (빈 상태, 글자는 렌더러가 그림)"""
    d = c.d
    x1, y1, x2, y2 = INFO
    rr = 2.2
    mask = Image.new("L", c.img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(c.R(x1, y1, x2, y2), radius=rr * c.kx, fill=255)
    grad = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    h = round((y2 - y1) * c.ky)
    for i in range(h):
        t = i / max(1, h - 1)
        col = tuple(round(INFO_TOP[j] + (INFO_BOTTOM[j] - INFO_TOP[j]) * t) for j in range(3))
        gd.line([x1 * c.kx, y1 * c.ky + i, x2 * c.kx, y1 * c.ky + i], fill=col + (255,))
    c.img.paste(grad, (0, 0), mask)
    d.rounded_rectangle(c.R(x1, y1, x2, y2), radius=rr * c.kx, outline=INFO_EDGE, width=c.w(0.45))
    d.rounded_rectangle(c.R(x1 + 0.6, y1 + 0.6, x2 - 0.6, y2 - 0.6), radius=(rr - 0.6) * c.kx,
                        outline=(255, 255, 255), width=c.w(0.3))


def draw_keys(c, with_labels):
    d = c.d
    # 키 사이 어두운 틈
    d.rectangle(c.R(LCD[0] + 1, KEY_ROWS[0] - 1.5, LCD[2] - 1, LCD[3]), fill=KEY_GAP)
    fd = font(LCD_FONT, max(8, round(14 * c.ky))) if with_labels else None
    fs = font(LCD_FONT, max(6, round(4.6 * c.ky))) if with_labels else None
    fs2 = font(LCD_FONT, max(6, round(3.6 * c.ky))) if with_labels else None
    labels = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "도움말", "0", "공동비밀번호\n입력"]
    for slot in range(12):
        kx = KEY_X0 + (slot % 3) * KEY_STEP
        ky = KEY_ROWS[slot // 3]
        c.vgrad(kx, ky, kx + KEY_W, ky + KEY_H, KEY_TOP, KEY_BOTTOM)
        d.line([c.P(kx + 0.3, ky + 0.25), c.P(kx + KEY_W - 0.3, ky + 0.25)], fill=KEY_HILITE, width=c.w(0.5))
        # 위쪽 광택
        gl = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(gl).rectangle(c.R(kx + 0.4, ky + 0.6, kx + KEY_W - 0.4, ky + KEY_H * 0.45), fill=(255, 255, 255, 28))
        c.img.alpha_composite(gl)
        if not with_labels:
            continue
        label = labels[slot]
        cx, cy = c.P(kx + KEY_W / 2, ky + KEY_H / 2)
        if len(label) == 1:
            d.text((cx + 0.5 * c.kx, cy + 0.6 * c.ky), label, font=fd, fill=(10, 16, 50), anchor="mm")
            d.text((cx, cy), label, font=fd, fill=(255, 255, 255), anchor="mm")
        else:
            f = fs2 if "\n" in label else fs
            d.multiline_text((cx, cy), label, font=f, fill=(255, 255, 255), anchor="mm", align="center", spacing=0)


def draw_device(kx, ky, mode):
    """mode: 'block' (LCD 대기화면 포함) / 'gui' (LCD 배경과 키 모양만)"""
    ss = 3
    c = Canvas(kx * ss, ky * ss)
    draw_body(c)
    draw_lcd_background(c)
    if mode == "block":
        draw_info_box(c)
        draw_keys(c, True)
    else:
        draw_keys(c, False)
    return c.img.resize((round(W * kx), round(H * ky)), Image.LANCZOS)


GLYPH_W, GLYPH_H = 48, 64     # 셀 크기 (폰트 json 의 height 8 = 64px → 1단위 8px)


def make_glyph_sheet(path, chars, style):
    """글자를 셀 가운데에 그린다. 숫자 윗부분 4px, 아랫부분 60px → 높이 8단위 중 0.5~7.5 차지"""
    from PIL import ImageFilter, ImageChops
    sheet = Image.new("RGBA", (GLYPH_W * len(chars), GLYPH_H), (0, 0, 0, 0))
    f = font(LCD_FONT, 76)
    for i, ch in enumerate(chars):
        cell = Image.new("L", (GLYPH_W * 4, GLYPH_H * 4), 0)
        cd = ImageDraw.Draw(cell)
        # 숫자 높이를 셀 안 4~60px(4배 해상도 16~240)에 맞춤
        bbox = cd.textbbox((0, 0), "8", font=font(LCD_FONT, 76 * 4))
        h8 = bbox[3] - bbox[1]
        size = round(76 * 4 * (224 / h8))
        ff = font(LCD_FONT, size)
        b8 = cd.textbbox((0, 0), "8", font=ff)
        if ch in ":*-":
            bb = cd.textbbox((0, 0), ch, font=ff)
            gx = 8 - bb[0]
            if ch == "*":
                gy = 16 + 20 - bb[1]
            else:
                gy = 16 - b8[1] + (224 - (bb[3] - bb[1])) / 2 - (bb[1] - b8[1]) + (bb[1] - b8[1])
                gy = 16 + ((224 - (bb[3] - bb[1])) / 2) - bb[1]
        else:
            bb = cd.textbbox((0, 0), ch, font=ff)
            gx = 8 - bb[0]
            gy = 16 - b8[1]
        cd.text((gx, gy), ch, font=ff, fill=255)
        mask = cell
        if style == "key":
            shadow = mask.filter(ImageFilter.GaussianBlur(5))
            img = Image.new("RGBA", mask.size, (0, 0, 0, 0))
            sh = Image.new("RGBA", mask.size, (8, 12, 40, 255))
            img.paste(sh, (6, 8), shadow.point(lambda v: min(255, int(v * 0.9))))
            white = Image.new("RGBA", mask.size, (255, 255, 255, 255))
            img.paste(white, (0, 0), mask)
        else:
            img = Image.new("RGBA", mask.size, (0, 0, 0, 0))
            dark = Image.new("RGBA", mask.size, (30, 38, 96, 255))
            outer = mask.filter(ImageFilter.MaxFilter(13))
            img.paste(dark, (0, 0), outer)
            inner = mask.filter(ImageFilter.MinFilter(3))
            grad = Image.new("RGBA", mask.size, (0, 0, 0, 0))
            gd = ImageDraw.Draw(grad)
            for y in range(mask.size[1]):
                t = y / mask.size[1]
                c1, c2 = (176, 188, 232), (88, 100, 170)
                col = tuple(round(c1[j] + (c2[j] - c1[j]) * t) for j in range(3))
                gd.line([0, y, mask.size[0], y], fill=col + (255,))
            img.paste(grad, (0, 0), inner)
            hi = inner.filter(ImageFilter.MinFilter(5))
            hi = ImageChops.subtract(inner, ImageChops.offset(hi, 0, 4))
            img.paste(Image.new("RGBA", mask.size, (255, 255, 255, 200)), (0, 0), hi)
        img = img.resize((GLYPH_W, GLYPH_H), Image.LANCZOS)
        sheet.alpha_composite(img, (i * GLYPH_W, 0))
    sheet.save(path)


def write_model():
    """평평한 검은 본체 + 양옆 크롬 막대만 살짝 둥글게 (계단 3단)"""
    import json
    height = 16 * 0.6
    half = height * W / H / 2
    depth = 0.57
    x1, x2, y1, y2 = 8 - half, 8 + half, 8 - height / 2, 8 + height / 2
    k = (x2 - x1) / W
    zf = 16 - depth

    def uv(sx1, sx2):
        return [round((x2 - sx2) / (x2 - x1) * 16, 4), 0, round((x2 - sx1) / (x2 - x1) * 16, 4), 16]

    def el(sx1, sx2, za, zb, side):
        faces = {f: {"uv": [0, 0, 16, 16], "texture": side} for f in ("south", "east", "west", "up", "down")}
        faces["north"] = {"uv": uv(sx1, sx2), "texture": "#front"}
        return {"from": [round(sx1, 4), round(y1, 4), round(za, 4)], "to": [round(sx2, 4), round(y2, 4), round(zb, 4)], "faces": faces}

    els = [el(x1 + CHROME * k, x2 - CHROME * k, zf, 16, "#black")]
    # 크롬 (위에서 본 단면, 사용자 그림): 앞면은 검은 면과 같은 높이로 평평하게 이어지고,
    # 바깥 앞 모서리만 둥글게(반지름 = 크롬 폭) 옆면으로 말려 들어감. 벽 쪽 뒤 모서리는 직각
    import math
    cw = CHROME * k
    r = min(cw, depth)
    n = 6
    for side in (-1, 1):
        outer = x1 if side < 0 else x2
        for i in range(n):
            d1, d2 = r * i / n, r * (i + 1) / n
            dm = (d1 + d2) / 2
            zfront = zf + r - math.sqrt(max(0.0, r * r - (r - dm) ** 2))
            a, b = (outer + d1, outer + d2) if side < 0 else (outer - d2, outer - d1)
            els.append(el(a, b, zfront, 16, "#side"))
        if cw > r:
            a, b = (outer + r, outer + cw) if side < 0 else (outer - cw, outer - r)
            els.append(el(a, b, zf, 16, "#side"))

    model = {"parent": "block/block", "render_type": "minecraft:cutout",
             "textures": {"particle": "qwertys_homenet:block/lobby_phone_edge", "front": "qwertys_homenet:block/lobby_phone_front",
                          "side": "qwertys_homenet:block/lobby_phone_side", "edge": "qwertys_homenet:block/lobby_phone_edge",
                          "black": "qwertys_homenet:block/guard_master_black"},
             "elements": els,
             "display": {"gui": {"rotation": [0, 180, 0], "scale": [1.45, 1.45, 1.45]},
                         "fixed": {"rotation": [0, 180, 0], "scale": [1.45, 1.45, 1.45]},
                         "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                         "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
                         "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [0.6, 0.6, 0.6]}}}
    with open(os.path.join(ROOT, "models/block/lobby_phone.json"), "w") as f:
        json.dump(model, f, indent=2)


def main():
    blk = os.path.join(TEX, "block")
    gui = os.path.join(TEX, "gui")
    fnt = os.path.join(ROOT, "textures", "font")
    itm = os.path.join(TEX, "item")
    for p in (blk, gui, fnt, itm):
        os.makedirs(p, exist_ok=True)

    # 블록 정면: 기기 전체를 512x512 에 늘려 그림 (모델 UV 가 정면 전체에 펼침)
    draw_device(512 / W, 512 / H, "block").save(os.path.join(blk, "lobby_phone_front.png"))
    # GUI: 4배
    draw_device(4.0, 4.0, "gui").save(os.path.join(gui, "lobby_phone.png"))

    side = Image.new("RGBA", (16, 64))
    sd = ImageDraw.Draw(side)
    for x in range(16):
        t = x / 15
        v = round(150 + 90 * (1 - abs(t - 0.4) * 1.6))
        sd.line([x, 0, x, 63], fill=(v, v + 2, v + 6))
    side.save(os.path.join(blk, "lobby_phone_side.png"))

    write_model()

    edge = Image.new("RGBA", (64, 64), (12, 14, 24, 255))
    ed = ImageDraw.Draw(edge)
    cw = round(64 * CHROME / W)
    ed.rectangle([0, 0, cw, 63], fill=(214, 218, 226))
    ed.rectangle([63 - cw, 0, 63, 63], fill=(214, 218, 226))
    edge.save(os.path.join(blk, "lobby_phone_edge.png"))

    # 아이콘 글리프 (32x32 x 2) - 비트맵 폰트
    ic = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    big = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    bd = ImageDraw.Draw(big)
    draw_net_icon(bd, (64, 64), 56)
    draw_door_icon(bd, (192, 64), 56)
    ic = big.resize((64, 32), Image.LANCZOS)
    ic.save(os.path.join(fnt, "lcd_icons.png"))

    # 비트맵 글리프 폰트: 키패드 숫자(흰색) / 정보 박스 큰 숫자(사진처럼 테두리 진하고 안쪽 밝은 양각)
    make_glyph_sheet(os.path.join(fnt, "lcd_key.png"), "0123456789*#", "key")
    make_glyph_sheet(os.path.join(fnt, "lcd_big.png"), "0123456789:*-", "big")

    # 출입 카드 아이템
    card = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle([1, 3, 14, 12], radius=1, fill=(236, 238, 244), outline=(150, 156, 170))
    cd.rectangle([1, 4, 14, 5], fill=(46, 86, 200))
    cd.rectangle([3, 7, 5, 9], fill=(214, 176, 72))
    cd.line([7, 8, 12, 8], fill=(150, 156, 170))
    cd.line([7, 10, 11, 10], fill=(170, 176, 190))
    card.save(os.path.join(itm, "rf_card.png"))
    print("ok")


if __name__ == "__main__":
    main()
