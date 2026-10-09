"""
월패드 (KOCOM KHN-893N 스타일) 텍스처 / 모델 생성.
 - block/wallpad_front(.png/_ringing): 블록 정면 (본체 비율 705:475 를 정사각형에 늘려 담음)
 - gui/wallpad_body.png: 화면(GUI) 속 본체 (1410 x 950)
 - gui/wallpad_bg.png, wallpad_bg_sub.png: 메인 / 서브 화면 배경 (800 x 450)
 - gui/wallpad_icons.png: 아이콘 (128 칸, 1024 x 1024)
 - gui/wallpad_cams.png: 카메라 화면 (480 x 270 칸)
"""
import json
import math
sys_path_added = __import__("sys").path.insert(0, __import__("os").path.dirname(__file__))
import roundmodel as rm
import os
import random
import sys

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/qwertys_homenet"
TEX = os.path.join(ROOT, "textures")
KR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
KR_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
random.seed(893)

# 본체 기준 좌표 (사진 705 x 475)
BW, BH = 705, 475
SCREEN = (104, 99, 494, 278)          # x, y, w, h
BUTTON_Y = [115, 177, 239, 300, 361]
BUTTON_X = (603, 643)
BUTTON_LABELS = ["비 상", "외 출", "경 비", "통 화", "문열림"]


def font(size, bold=False):
    return ImageFont.truetype(KR_B if bold and os.path.exists(KR_B) else KR, size, index=1)


def save(img, rel):
    p = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def wj(rel, obj):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def vgrad(img, box, top, bottom):
    d = ImageDraw.Draw(img)
    x1, y1, x2, y2 = [int(v) for v in box]
    for y in range(y1, y2):
        t = (y - y1) / max(1, y2 - y1 - 1)
        c = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(len(top)))
        d.line([x1, y, x2 - 1, y], fill=c)


# ====================================================================== 본체
def body(scale=2, screen_mode="black"):
    s = scale
    W, H = BW * s, BH * s
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # 그림자 느낌의 가장자리
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], radius=22 * s, fill=255)
    base = Image.new("RGBA", (W, H))
    vgrad(base, (0, 0, W, H), (253, 253, 254, 255), (236, 237, 240, 255))
    img.paste(base, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=22 * s, outline=(200, 203, 209), width=max(1, s))
    d.rounded_rectangle([3 * s, 3 * s, W - 1 - 3 * s, H - 1 - 3 * s], radius=16 * s, outline=(255, 255, 255), width=max(1, s))
    # 아래쪽 옆면 음영
    d.rounded_rectangle([2 * s, H - 9 * s, W - 2 * s, H - 2 * s], radius=6 * s, fill=(228, 229, 233))

    # 화면 유리
    sx, sy, sw, sh = [v * s for v in SCREEN]
    d.rectangle([sx - 3 * s, sy - 3 * s, sx + sw + 3 * s, sy + sh + 3 * s], fill=(30, 32, 36))
    d.rectangle([sx - 1 * s, sy - 1 * s, sx + sw + 1 * s, sy + sh + 1 * s], fill=(8, 9, 11))
    if screen_mode == "black":
        scr = Image.new("RGBA", (sw, sh))
        vgrad(scr, (0, 0, sw, sh), (22, 24, 30, 255), (6, 7, 9, 255))
        rd = ImageDraw.Draw(scr)
        # 유리 반사
        rd.polygon([(0, 0), (sw * 0.42, 0), (sw * 0.22, sh), (0, sh)], fill=(255, 255, 255, 10))
        img.alpha_composite(scr, (sx, sy))
    elif isinstance(screen_mode, Image.Image):
        img.alpha_composite(screen_mode.resize((sw, sh), Image.LANCZOS).convert("RGBA"), (sx, sy))

    # 카메라
    cx, cy = 352 * s, 45 * s
    d.rounded_rectangle([cx - 21 * s, cy - 9 * s, cx + 21 * s, cy + 9 * s], radius=9 * s, fill=(18, 18, 22))
    d.ellipse([cx - 7 * s, cy - 7 * s, cx + 7 * s, cy + 7 * s], fill=(40, 46, 70))
    d.ellipse([cx - 4 * s, cy - 4 * s, cx + 4 * s, cy + 4 * s], fill=(70, 90, 150))
    d.ellipse([cx - 2 * s, cy - 3 * s, cx, cy - 1 * s], fill=(200, 210, 255))

    # 스피커 그릴
    for row in range(6):
        for col in range(7):
            x = (30 + col * 6.5 + (3 if row % 2 else 0)) * s
            y = (218 + row * 7) * s
            d.rounded_rectangle([x, y, x + 3.6 * s, y + 2.2 * s], radius=1 * s, fill=(70, 72, 78))

    # 전원 LED / 마크
    d.ellipse([637 * s, 68 * s, 645 * s, 76 * s], fill=(190, 194, 200), outline=(150, 154, 160))
    px, py = 656 * s, 72 * s
    d.arc([px - 5 * s, py - 5 * s, px + 5 * s, py + 5 * s], 300, 240, fill=(90, 92, 98), width=max(1, s))
    d.line([px, py - 7 * s, px, py - 1 * s], fill=(90, 92, 98), width=max(1, s))

    # 오른쪽 터치 버튼 (가로 막대 + 라벨)
    f = font(int(8.5 * s))
    for y, label in zip(BUTTON_Y, BUTTON_LABELS):
        d.rounded_rectangle([BUTTON_X[0] * s, (y - 2) * s, BUTTON_X[1] * s, (y + 1.5) * s], radius=2 * s, fill=(206, 208, 213))
        d.line([BUTTON_X[0] * s, (y - 2) * s, BUTTON_X[1] * s, (y - 2) * s], fill=(255, 255, 255), width=max(1, s))
        d.line([BUTTON_X[0] * s, (y + 2) * s, BUTTON_X[1] * s, (y + 2) * s], fill=(180, 183, 189), width=max(1, s))
        tw = d.textlength(label, font=f)
        d.text(((BUTTON_X[0] + BUTTON_X[1]) / 2 * s - tw / 2, (y + 7) * s), label, font=f, fill=(150, 153, 160))

    # 마이크
    d.rounded_rectangle([600 * s, 444 * s, 627 * s, 447 * s], radius=1 * s, fill=(60, 62, 68))
    mx, my = 659 * s, 446 * s
    d.rounded_rectangle([mx - 2 * s, my - 6 * s, mx + 2 * s, my + 1 * s], radius=2 * s, outline=(110, 112, 118), width=max(1, s))
    d.arc([mx - 4 * s, my - 3 * s, mx + 4 * s, my + 4 * s], 0, 180, fill=(110, 112, 118), width=max(1, s))
    d.line([mx, my + 4 * s, mx, my + 7 * s], fill=(110, 112, 118), width=max(1, s))

    # 로고 자리 (상표 대신 HOMENET)
    lf = font(int(15 * s), True)
    text = "H O M E N E T"
    tw = d.textlength(text, font=lf)
    d.text((352 * s - tw / 2, 413 * s), text, font=lf, fill=(128, 131, 138))
    return img


# ====================================================================== 배경
def background(w=800, h=450):
    img = Image.new("RGBA", (w, h))
    vgrad(img, (0, 0, w, h), (22, 92, 186, 255), (150, 205, 245, 255))
    # 구름
    cl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cl)
    for _ in range(26):
        x, y = random.uniform(-50, w), random.uniform(120, 300)
        rw, rh = random.uniform(60, 180), random.uniform(18, 40)
        cd.ellipse([x, y, x + rw, y + rh], fill=(255, 255, 255, random.randint(60, 140)))
    cl = cl.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(cl)
    # 빛
    lt = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(lt).ellipse([w * 0.45, -h * 0.4, w * 1.2, h * 0.5], fill=(255, 255, 255, 70))
    img.alpha_composite(lt.filter(ImageFilter.GaussianBlur(60)))

    # 민들레 홀씨
    def dandelion(layer, cx, cy, r, alpha):
        dd = ImageDraw.Draw(layer)
        dd.line([cx, cy, cx + r * 0.3, h], fill=(150, 175, 120, alpha), width=3)
        for i in range(90):
            a = random.uniform(0, math.tau)
            rr = r * random.uniform(0.75, 1.0)
            ex, ey = cx + math.cos(a) * rr, cy + math.sin(a) * rr
            dd.line([cx, cy, ex, ey], fill=(255, 255, 255, alpha // 2), width=1)
            dd.ellipse([ex - 2.5, ey - 2.5, ex + 2.5, ey + 2.5], fill=(255, 255, 255, alpha))
        dd.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(235, 235, 220, alpha))

    dl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dandelion(dl, w * 0.36, h * 0.48, 42, 200)
    dandelion(dl, w * 0.53, h * 0.36, 30, 150)
    dd = ImageDraw.Draw(dl)
    for _ in range(14):
        x, y = random.uniform(w * 0.35, w * 0.9), random.uniform(h * 0.1, h * 0.5)
        a = random.uniform(-0.6, 0.6)
        dd.line([x, y, x + 12 * math.cos(a - 1.2), y + 12 * math.sin(a - 1.2)], fill=(255, 255, 255, 160), width=1)
        dd.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(255, 255, 255, 200))
    img.alpha_composite(dl.filter(ImageFilter.GaussianBlur(0.6)))

    # 잔디
    def grass(layer, top, count, cols, blur, wmax):
        gd = ImageDraw.Draw(layer)
        for _ in range(count):
            x = random.uniform(-10, w + 10)
            hh = random.uniform(top[0], top[1])
            bend = random.uniform(-25, 25)
            c = random.choice(cols)
            ww = random.uniform(2, wmax)
            gd.polygon([(x - ww, h), (x + ww, h), (x + bend, h - hh)], fill=c)
        return layer.filter(ImageFilter.GaussianBlur(blur)) if blur else layer

    back = grass(Image.new("RGBA", (w, h), (0, 0, 0, 0)), (60, 110), 500,
                 [(120, 170, 70, 255), (100, 150, 60, 255), (140, 185, 80, 255)], 2.5, 5)
    img.alpha_composite(back)
    front = grass(Image.new("RGBA", (w, h), (0, 0, 0, 0)), (40, 95), 650,
                  [(60, 130, 40, 255), (80, 150, 45, 255), (40, 105, 30, 255), (110, 170, 55, 255)], 0, 4)
    img.alpha_composite(front)
    return img


def sub_background(bg):
    g = ImageEnhance.Color(bg.convert("RGB")).enhance(0.18)
    g = ImageEnhance.Brightness(g).enhance(1.12)
    g = ImageEnhance.Contrast(g).enhance(0.8)
    return g.convert("RGBA")


# ====================================================================== 아이콘
CELL = 128


def app_icon(draw_fn):
    """흰 광택 둥근 사각형 앱 아이콘"""
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    sh = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([12, 16, 116, 120], radius=24, fill=(0, 0, 0, 90))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4)))
    plate = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    m = Image.new("L", (CELL, CELL), 0)
    ImageDraw.Draw(m).rounded_rectangle([10, 10, 117, 117], radius=24, fill=255)
    g = Image.new("RGBA", (CELL, CELL))
    vgrad(g, (0, 0, CELL, CELL), (255, 255, 255, 255), (214, 220, 230, 255))
    plate.paste(g, (0, 0), m)
    pd = ImageDraw.Draw(plate)
    pd.rounded_rectangle([10, 10, 117, 117], radius=24, outline=(170, 178, 190), width=2)
    im.alpha_composite(plate)
    d = ImageDraw.Draw(im)
    draw_fn(im, d)
    # 위쪽 광택
    gl = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    ImageDraw.Draw(gl).rounded_rectangle([14, 13, 113, 60], radius=20, fill=(255, 255, 255, 60))
    im.alpha_composite(gl)
    return im


def ic_security(im, d):
    d.ellipse([28, 28, 100, 100], fill=(40, 120, 220))
    d.ellipse([34, 34, 94, 94], fill=(70, 150, 240))
    d.arc([50, 38, 78, 70], 180, 360, fill=(255, 255, 255), width=6)
    d.line([50, 54, 50, 62], fill=(255, 255, 255), width=6)
    d.line([78, 54, 78, 62], fill=(255, 255, 255), width=6)
    d.rounded_rectangle([44, 58, 84, 86], radius=5, fill=(255, 255, 255))
    d.ellipse([60, 66, 68, 74], fill=(40, 120, 220))


def ic_control(im, d):
    for i, (x, k) in enumerate([(40, 70), (64, 48), (88, 80)]):
        d.rounded_rectangle([x - 4, 30, x + 4, 100], radius=4, fill=(90, 96, 108))
        d.rounded_rectangle([x - 10, k - 7, x + 10, k + 7], radius=4, fill=(245, 150, 40), outline=(190, 100, 20), width=2)


def ic_call(im, d):
    d.rounded_rectangle([26, 32, 102, 96], radius=8, fill=(232, 234, 238), outline=(120, 126, 136), width=3)
    d.rounded_rectangle([34, 40, 70, 88], radius=4, fill=(60, 70, 90))
    d.ellipse([78, 50, 96, 68], outline=(120, 126, 136), width=3)
    for r in range(3):
        for c in range(2):
            d.ellipse([78 + c * 9, 74 + r * 6, 82 + c * 9, 78 + r * 6], fill=(120, 126, 136))


def ic_inquiry(im, d):
    d.rounded_rectangle([30, 26, 82, 96], radius=5, fill=(250, 250, 250), outline=(120, 126, 136), width=3)
    for i in range(4):
        d.line([38, 40 + i * 11, 72, 40 + i * 11], fill=(150, 156, 166), width=3)
    d.ellipse([56, 54, 92, 90], fill=(220, 236, 255, 255), outline=(40, 110, 200), width=6)
    d.line([88, 86, 102, 100], fill=(40, 110, 200), width=9)


def ic_settings(im, d):
    d.rounded_rectangle([32, 32, 96, 96], radius=8, fill=(255, 255, 255), outline=(90, 100, 118), width=5)
    d.line([44, 64, 60, 80, 90, 42], fill=(40, 140, 220), width=9, joint="curve")


def ic_energy(im, d):
    d.ellipse([28, 28, 100, 100], fill=(250, 250, 252), outline=(150, 156, 166), width=3)
    d.arc([36, 36, 92, 92], 135, 330, fill=(60, 150, 235), width=8)
    d.arc([36, 36, 92, 92], 330, 405, fill=(240, 120, 60), width=8)
    d.line([64, 64, 82, 48], fill=(70, 74, 84), width=5)
    d.ellipse([58, 58, 70, 70], fill=(70, 74, 84))


def line_icon(fn, width=7):
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fn(d, (255, 255, 255, 255), width)
    return im


def li_sun(d, c, w):
    d.ellipse([40, 40, 88, 88], fill=c)
    for i in range(8):
        a = i * math.pi / 4
        d.line([64 + math.cos(a) * 34, 64 + math.sin(a) * 34, 64 + math.cos(a) * 52, 64 + math.sin(a) * 52], fill=c, width=w)


def cloud(d, c, w, dy=0, fill=None):
    pts = [22, 50 + dy, 106, 92 + dy]
    d.ellipse([22, 58 + dy, 62, 92 + dy], outline=c, width=w, fill=fill)
    d.ellipse([42, 36 + dy, 90, 84 + dy], outline=c, width=w, fill=fill)
    d.ellipse([72, 56 + dy, 106, 92 + dy], outline=c, width=w, fill=fill)
    d.rectangle([42, 70 + dy, 88, 92 + dy], fill=fill or (0, 0, 0, 0))
    d.line([42, 92 + dy, 88, 92 + dy], fill=c, width=w)


def li_cloud_sun(d, c, w):
    d.ellipse([20, 18, 56, 54], outline=c, width=w)
    for i in range(5):
        a = math.pi * (0.75 + i * 0.25)
        d.line([38 + math.cos(a) * 26, 36 + math.sin(a) * 26, 38 + math.cos(a) * 36, 36 + math.sin(a) * 36], fill=c, width=w - 2)
    cloud(d, c, w, 8, fill=(0, 0, 0, 0))


def li_rain(d, c, w):
    cloud(d, c, w, -14)
    for x in (44, 64, 84):
        d.line([x, 90, x - 6, 108], fill=c, width=w - 1)


def li_snow(d, c, w):
    cloud(d, c, w, -14)
    for x in (44, 64, 84):
        d.ellipse([x - 5, 94, x + 5, 104], fill=c)


def li_wifi(d, c, w):
    for r in (14, 30, 46):
        d.arc([64 - r, 92 - r, 64 + r, 92 + r], 225, 315, fill=c, width=w)
    d.ellipse([58, 86, 70, 98], fill=c)


def li_person(d, c, w):
    d.rounded_rectangle([30, 22, 98, 106], radius=6, outline=c, width=w - 2)
    d.ellipse([52, 38, 76, 62], fill=c)
    d.rounded_rectangle([44, 66, 84, 96], radius=10, fill=c)


def li_notice(d, c, w):
    d.rounded_rectangle([24, 28, 104, 100], radius=8, outline=c, width=w - 1)
    for i in range(3):
        d.line([38, 48 + i * 16, 90, 48 + i * 16], fill=c, width=w - 2)


def li_elevator(d, c, w):
    d.rounded_rectangle([28, 22, 100, 106], radius=6, outline=c, width=w - 1)
    d.line([64, 26, 64, 102], fill=c, width=w - 3)
    d.ellipse([36, 40, 52, 56], fill=c)
    d.ellipse([76, 40, 92, 56], fill=c)
    d.polygon([(44, 66), (36, 80), (52, 80)], fill=c)
    d.polygon([(84, 94), (76, 80), (92, 80)], fill=c)


def li_home(d, c, w):
    d.line([20, 64, 64, 24, 108, 64], fill=c, width=w, joint="curve")
    d.line([34, 56, 34, 104, 94, 104, 94, 56], fill=c, width=w, joint="curve")
    d.rectangle([56, 76, 72, 104], outline=c, width=w - 2)


def li_help(d, c, w):
    d.rounded_rectangle([18, 22, 110, 90], radius=14, outline=c, width=w - 1)
    d.polygon([(40, 88), (40, 110), (62, 88)], fill=c)
    f = font(56, True)
    d.text((64, 54), "?", font=f, fill=c, anchor="mm")


def li_shield(d, c, w):
    d.polygon([(64, 18), (104, 32), (100, 70), (64, 110), (28, 70), (24, 32)], outline=c, width=w)
    d.line([46, 64, 60, 78, 84, 50], fill=c, width=w)


def li_wrench(d, c, w):
    d.line([36, 96, 78, 54], fill=c, width=w + 6)
    d.ellipse([66, 22, 106, 62], outline=c, width=w + 2)
    d.rectangle([84, 18, 100, 36], fill=(0, 0, 0, 0))


def li_phone(d, c, w):
    d.arc([24, 24, 104, 104], 90, 180, fill=c, width=w + 8)
    d.rounded_rectangle([18, 30, 44, 70], radius=8, fill=c)
    d.rounded_rectangle([58, 84, 98, 110], radius=8, fill=c)


def li_magnifier(d, c, w):
    d.ellipse([22, 22, 82, 82], outline=c, width=w + 1)
    d.line([76, 76, 106, 106], fill=c, width=w + 5)


def li_gauge(d, c, w):
    d.arc([20, 28, 108, 116], 180, 360, fill=c, width=w + 1)
    d.line([64, 72, 88, 44], fill=c, width=w)
    d.ellipse([56, 64, 72, 80], fill=c)


def li_gear(d, c, w):
    for i in range(8):
        a = i * math.pi / 4
        d.line([64 + math.cos(a) * 26, 64 + math.sin(a) * 26, 64 + math.cos(a) * 46, 64 + math.sin(a) * 46], fill=c, width=16)
    d.ellipse([28, 28, 100, 100], fill=c)
    d.ellipse([50, 50, 78, 78], fill=(0, 0, 0, 0))


def li_speaker(d, c, w):
    d.polygon([(24, 50), (44, 50), (66, 30), (66, 98), (44, 78), (24, 78)], fill=c)
    d.arc([60, 40, 92, 88], 300, 60, fill=c, width=w - 1)
    d.arc([60, 26, 112, 102], 300, 60, fill=c, width=w - 1)


def li_bright(d, c, w):
    d.ellipse([28, 28, 100, 100], outline=c, width=w)
    d.pieslice([28, 28, 100, 100], 90, 270, fill=c)


def li_lock(d, c, w):
    d.arc([40, 20, 88, 72], 180, 360, fill=c, width=w + 2)
    d.line([40, 46, 40, 60], fill=c, width=w + 2)
    d.line([88, 46, 88, 60], fill=c, width=w + 2)
    d.rounded_rectangle([30, 56, 98, 108], radius=8, fill=c)


def bulb(on):
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    if on:
        gl = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
        ImageDraw.Draw(gl).ellipse([18, 8, 110, 100], fill=(255, 230, 120, 170))
        im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(10)))
    d = ImageDraw.Draw(im)
    d.ellipse([34, 18, 94, 78], fill=(255, 240, 170) if on else (236, 238, 242), outline=(150, 154, 164), width=3)
    d.rectangle([48, 70, 80, 82], fill=(255, 240, 170) if on else (236, 238, 242))
    for i in range(3):
        d.rounded_rectangle([48, 84 + i * 9, 80, 90 + i * 9], radius=3, fill=(140, 146, 156))
    d.line([56, 50, 64, 62, 72, 50], fill=(220, 150, 40) if on else (170, 174, 184), width=3)
    return im


def fan_icon():
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(3):
        a = i * math.tau / 3
        cx, cy = 64 + math.cos(a) * 24, 64 + math.sin(a) * 24
        d.ellipse([cx - 22, cy - 13, cx + 22, cy + 13], fill=(90, 100, 118))
    d.ellipse([54, 54, 74, 74], fill=(255, 255, 255), outline=(90, 100, 118), width=4)
    return im


def heater_icon():
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(20, 64), (64, 26), (108, 64), (98, 64), (98, 106), (30, 106), (30, 64)], fill=(110, 118, 134))
    d.polygon([(64, 56), (82, 82), (74, 100), (54, 100), (46, 82)], fill=(255, 140, 40))
    d.polygon([(64, 74), (72, 88), (64, 98), (56, 88)], fill=(255, 220, 80))
    return im


def ac_icon():
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(3):
        a = i * math.pi / 3
        d.line([64 - math.cos(a) * 44, 64 - math.sin(a) * 44, 64 + math.cos(a) * 44, 64 + math.sin(a) * 44], fill=(70, 150, 230), width=8)
    d.ellipse([52, 52, 76, 76], fill=(70, 150, 230))
    return im


def sensor_tile(kind):
    """방범 화면 타일 (방범1 / 방범2 / 가스) – 흰 바탕 그림"""
    im = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind == "door":
        d.rectangle([34, 22, 84, 108], fill=(180, 140, 100), outline=(110, 80, 50), width=3)
        d.ellipse([72, 62, 80, 70], fill=(240, 210, 90))
        d.rectangle([88, 40, 100, 56], fill=(250, 250, 250), outline=(120, 126, 136), width=2)
    elif kind == "window":
        d.rectangle([24, 28, 104, 100], fill=(180, 214, 240), outline=(110, 118, 134), width=4)
        d.line([64, 28, 64, 100], fill=(110, 118, 134), width=4)
        d.line([24, 64, 104, 64], fill=(110, 118, 134), width=4)
        d.rectangle([92, 16, 108, 28], fill=(250, 250, 250), outline=(120, 126, 136), width=2)
    else:
        d.polygon([(64, 18), (92, 64), (84, 100), (44, 100), (36, 64)], fill=(70, 140, 230))
        d.polygon([(64, 52), (78, 76), (72, 96), (56, 96), (50, 76)], fill=(150, 210, 255))
    return im


def gas_valve():
    """제어 > 가스밸브 그림 (256 x 256)"""
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # 세로 배관
    for x0, x1, col in [(108, 148, (150, 156, 166))]:
        d.rectangle([x0, 0, x1, 256], fill=col)
        d.rectangle([x0 + 6, 0, x0 + 16, 256], fill=(200, 205, 212))
    # 밸브 몸통
    d.rounded_rectangle([88, 100, 168, 170], radius=10, fill=(120, 126, 136), outline=(80, 84, 92), width=3)
    d.rectangle([60, 116, 196, 154], fill=(130, 136, 146), outline=(80, 84, 92), width=3)
    d.rectangle([40, 112, 64, 158], fill=(150, 156, 166), outline=(80, 84, 92), width=3)
    d.rectangle([192, 112, 216, 158], fill=(150, 156, 166), outline=(80, 84, 92), width=3)
    d.ellipse([110, 116, 146, 152], fill=(90, 96, 106))
    f = font(20, True)
    d.text((170, 135), "GAS", font=f, fill=(230, 232, 236), anchor="mm")
    return im


def valve_lever(opened):
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if opened:   # 배관과 같은 방향 = 열림
        d.rounded_rectangle([118, 40, 138, 130], radius=8, fill=(220, 60, 50), outline=(140, 30, 25), width=3)
    else:        # 가로 = 닫힘
        d.rounded_rectangle([128, 124, 236, 144], radius=8, fill=(240, 180, 40), outline=(160, 110, 20), width=3)
    d.ellipse([116, 122, 140, 146], fill=(60, 64, 72))
    return im


def icons():
    atlas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    cells = [
        app_icon(ic_security), app_icon(ic_control), app_icon(ic_call), app_icon(ic_inquiry),
        app_icon(ic_settings), app_icon(ic_energy),
        line_icon(li_sun), line_icon(li_cloud_sun), line_icon(li_rain), line_icon(li_snow),
        line_icon(li_wifi), line_icon(li_person), line_icon(li_notice), line_icon(li_elevator),
        line_icon(li_home), line_icon(li_help), line_icon(li_shield), line_icon(li_wrench),
        line_icon(li_phone), line_icon(li_magnifier), line_icon(li_gauge), line_icon(li_gear),
        bulb(True), bulb(False), fan_icon(), heater_icon(), ac_icon(),
        sensor_tile("door"), sensor_tile("window"), sensor_tile("gas"),
        line_icon(li_speaker), line_icon(li_bright), line_icon(li_lock),
    ]
    for i, c in enumerate(cells):
        atlas.alpha_composite(c, ((i % 8) * CELL, (i // 8) * CELL))
    # 가스밸브 (256 칸) : y 640 부터
    atlas.alpha_composite(gas_valve(), (0, 640))
    atlas.alpha_composite(valve_lever(True), (256, 640))
    atlas.alpha_composite(valve_lever(False), (512, 640))
    save(atlas, "gui/wallpad_icons.png")


# ====================================================================== 카메라 화면
CW, CH = 480, 270


def face_person(d, cx, top, scale=1.0, cap=False, shirt=(60, 90, 140), hair=(40, 30, 25)):
    s = scale
    d.rounded_rectangle([cx - 95 * s, top + 120 * s, cx + 95 * s, top + 300 * s], radius=50 * s, fill=shirt)
    d.rectangle([cx - 18 * s, top + 95 * s, cx + 18 * s, top + 130 * s], fill=(225, 185, 155))
    d.ellipse([cx - 46 * s, top, cx + 46 * s, top + 112 * s], fill=(232, 192, 160))
    if cap:
        d.chord([cx - 52 * s, top - 22 * s, cx + 52 * s, top + 52 * s], 180, 360, fill=(30, 40, 60))
        d.rectangle([cx - 56 * s, top + 12 * s, cx + 62 * s, top + 22 * s], fill=(20, 28, 44))
        d.ellipse([cx - 8 * s, top - 10 * s, cx + 8 * s, top + 4 * s], fill=(220, 190, 60))
    else:
        d.chord([cx - 48 * s, top - 6 * s, cx + 48 * s, top + 70 * s], 180, 360, fill=hair)
    for ex in (-17, 17):
        d.ellipse([cx + (ex - 5) * s, top + 50 * s, cx + (ex + 5) * s, top + 60 * s], fill=(40, 30, 30))
    d.arc([cx - 16 * s, top + 66 * s, cx + 16 * s, top + 90 * s], 20, 160, fill=(150, 70, 70), width=max(2, int(4 * s)))


def cam(kind):
    im = Image.new("RGB", (CW, CH))
    d = ImageDraw.Draw(im)
    if kind == "door":
        vgrad(im, (0, 0, CW, CH), (196, 186, 168), (150, 140, 124))
        d.rectangle([60, 20, 150, CH], fill=(120, 96, 70))
        d.rectangle([330, 0, 420, CH], fill=(170, 160, 146))
        face_person(d, 240, 50, 0.95, shirt=(200, 70, 90), hair=(50, 35, 25))
    elif kind == "lobby":
        vgrad(im, (0, 0, CW, CH), (150, 170, 180), (90, 100, 110))
        for x in range(0, CW, 80):
            d.line([x, 0, x, CH], fill=(70, 80, 90), width=6)
        d.rectangle([0, 200, CW, CH], fill=(110, 105, 100))
        face_person(d, 240, 46, 0.95, shirt=(50, 60, 80), hair=(30, 25, 20))
    elif kind == "guard":
        vgrad(im, (0, 0, CW, CH), (205, 205, 200), (170, 170, 165))
        d.rectangle([0, 150, CW, CH], fill=(160, 150, 130))
        face_person(d, 240, 50, 0.95, cap=True, shirt=(40, 55, 80))
        d.polygon([(220, 175), (240, 215), (260, 175)], fill=(230, 230, 235))
    elif kind == "neighbor":
        vgrad(im, (0, 0, CW, CH), (236, 228, 214), (200, 190, 175))
        d.rectangle([340, 40, 440, 140], fill=(160, 200, 230), outline=(240, 240, 240), width=6)
        face_person(d, 220, 52, 0.95, shirt=(90, 140, 90), hair=(70, 45, 30))
    elif kind == "elevator":
        vgrad(im, (0, 0, CW, CH), (215, 215, 212), (180, 178, 174))
        d.rectangle([0, 220, CW, CH], fill=(150, 148, 144))
        for x0 in (70, 270):
            d.rectangle([x0 - 10, 30, x0 + 150, 230], fill=(150, 152, 156))
            d.rectangle([x0, 40, x0 + 140, 230], fill=(196, 198, 202))
            d.line([x0 + 70, 40, x0 + 70, 230], fill=(130, 132, 136), width=3)
        d.rectangle([228, 140, 252, 220], fill=(110, 80, 60))
        d.ellipse([200, 80, 280, 160], fill=(70, 130, 60))
    elif kind == "cctv":
        vgrad(im, (0, 0, CW, CH), (170, 200, 225), (220, 225, 230))
        for i, x in enumerate((0, 110, 330, 400)):
            hgt = 150 + (i % 2) * 40
            d.rectangle([x, CH - hgt - 40, x + 90, CH - 40], fill=(225, 220, 210), outline=(170, 165, 155), width=2)
            for yy in range(CH - hgt - 30, CH - 50, 16):
                d.line([x + 8, yy, x + 82, yy], fill=(140, 160, 180), width=4)
        d.rectangle([0, CH - 50, CW, CH], fill=(120, 150, 90))
        for x in (220, 290):
            d.rectangle([x - 4, 150, x + 4, 225], fill=(100, 80, 60))
            d.ellipse([x - 34, 110, x + 34, 170], fill=(70, 130, 60))
        d.text((10, 8), "CAM 01", font=font(18), fill=(255, 255, 255))
    else:  # no signal
        for y in range(CH):
            for x in range(0, CW, 3):
                v = random.randint(40, 120)
                d.line([x, y, x + 2, y], fill=(v, v, v))
    im = im.filter(ImageFilter.GaussianBlur(0.8))
    return im


def cams():
    atlas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 255))
    for i, k in enumerate(["door", "lobby", "guard", "neighbor", "elevator", "cctv", "none"]):
        atlas.paste(cam(k), ((i % 2) * 512, (i // 2) * 288))
    save(atlas, "gui/wallpad_cams.png")
    return cam("door")


# ====================================================================== 블록 모델
# 처음 크기(가로 14.4픽셀)의 2/3 → 가로 9.6픽셀, 비율 705:475 유지, 두께 0.75픽셀
HALF_W = 4.8
HEIGHT = HALF_W * 2 * BH / BW
Y1 = round(8 - HEIGHT / 2, 4)
Y2 = round(8 + HEIGHT / 2, 4)
DEPTH = 0.75
CORNER = 22 / BW * HALF_W * 2     # 텍스처 모서리 반지름과 같게


def model(front):
    x1, x2 = 8 - HALF_W, 8 + HALF_W
    bbox = (x1, Y1, x2, Y2)
    # 뒤판(벽 쪽) + 앞쪽으로 살짝 작은 판 → 가장자리가 둥글게 깎인 느낌
    els = rm.rounded_box((x1, Y1, x2, Y2), 16 - DEPTH + 0.25, 16, CORNER, bbox, steps=3)
    inset = 0.08
    els += rm.rounded_box((x1 + inset, Y1 + inset, x2 - inset, Y2 - inset), 16 - DEPTH, 16 - DEPTH + 0.25,
                          CORNER - inset, bbox, steps=3)
    return rm.model({"particle": "qwertys_homenet:block/wallpad_edge", "front": "qwertys_homenet:block/" + front,
                     "edge": "qwertys_homenet:block/wallpad_edge"}, els)


def main():
    bg = background()
    save(bg, "gui/wallpad_bg.png")
    save(sub_background(bg), "gui/wallpad_bg_sub.png")
    icons()
    door = cams()
    save(body(2, None), "gui/wallpad_body.png")

    front = body(1, "black")
    save(front.resize((512, 512), Image.LANCZOS), "block/wallpad_front.png")
    # 호출 중: 화면에 방문자 + 통화 버튼 LED
    ring = body(1, door)
    d = ImageDraw.Draw(ring)
    y = BUTTON_Y[3]
    d.rounded_rectangle([BUTTON_X[0], y - 2, BUTTON_X[1], y + 1.5], radius=2, fill=(80, 170, 255))
    save(ring.resize((512, 512), Image.LANCZOS), "block/wallpad_front_ringing.png")
    edge = Image.new("RGBA", (16, 16), (238, 239, 242, 255))
    ImageDraw.Draw(edge).rectangle([0, 15, 15, 15], fill=(214, 216, 221, 255))
    save(edge, "block/wallpad_edge.png")

    wj("models/block/wallpad.json", model("wallpad_front"))
    wj("models/block/wallpad_ringing.json", model("wallpad_front_ringing"))
    print("wallpad: y", Y1, Y2, "half", HALF_W, "depth", DEPTH)


if __name__ == "__main__":
    main()
