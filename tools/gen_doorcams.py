"""
도어카메라 2종 (사진 비율 그대로, 월패드보다 작게) 텍스처 / 모델 / 블록상태.
 - door_camera_silver : 세로형 실버 (306 x 450), 큰 크롬 렌즈 링, 스피커, 호출 버튼
 - door_camera_square : 둥근 정사각 화이트 (358 x 383), 가운데 검은 패널, 왼쪽 아래 RF 패드
곡선은 roundmodel(계단 상자 + 투명 모서리 텍스처, 팔각 렌즈)로 표현.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
import roundmodel as rm  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/qwertys_homenet"
TEX = os.path.join(ROOT, "textures")
M = "qwertys_homenet"
KR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
KR_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"


def font(size, bold=False):
    return ImageFont.truetype(KR_B if bold else KR, max(6, int(size)), index=1)


def save(img, rel):
    p = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def wj(rel, obj):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def hgrad(img, box, cols, mask=None):
    """가로 방향 여러 색 그라데이션"""
    x1, y1, x2, y2 = [int(v) for v in box]
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    n = len(cols) - 1
    for x in range(x1, x2):
        t = (x - x1) / max(1, x2 - x1 - 1) * n
        i = min(n - 1, int(t))
        f = t - i
        c = tuple(round(cols[i][k] + (cols[i + 1][k] - cols[i][k]) * f) for k in range(3)) + (255,)
        d.line([x, y1, x, y2 - 1], fill=c)
    if mask is not None:
        layer.putalpha(Image.composite(layer.split()[3], Image.new("L", img.size, 0), mask))
    img.alpha_composite(layer)


def vgrad(img, box, top, bottom, mask=None):
    x1, y1, x2, y2 = [int(v) for v in box]
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(y1, y2):
        t = (y - y1) / max(1, y2 - y1 - 1)
        d.line([x1, y, x2 - 1, y], fill=tuple(round(top[k] + (bottom[k] - top[k]) * t) for k in range(3)) + (255,))
    if mask is not None:
        layer.putalpha(Image.composite(layer.split()[3], Image.new("L", img.size, 0), mask))
    img.alpha_composite(layer)


def rmask(size, box, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def disc(size, ss=4, draw=None):
    """원형 부품 텍스처 (바깥 투명)"""
    big = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    draw(big, ImageDraw.Draw(big), size * ss)
    return big.resize((size, size), Image.LANCZOS)


def solid(rgb):
    im = Image.new("RGBA", (16, 16), rgb + (255,))
    return im


# ====================================================================== 실버 도어카메라 (사진 1~6)
SW_, SH_ = 300, 390      # 정면 비율 (사진 평균 약 0.77)
S = 3                    # 그리기 배율
PLATE = (26, 20, 262, 368)        # 한 단 들어간 앞판
RING_C, RING_R, CHROME_R, LENS_R = (138, 162), 78, 60, 46
BTN_C, BTN_R = (182, 307), 22


def brushed(img, box, mask, strength=20, seed=1):
    import random
    rnd = random.Random(seed)
    x1, y1, x2, y2 = [int(v) for v in box]
    tex = Image.new("RGBA", img.size, (0, 0, 0, 0))
    td = ImageDraw.Draw(tex)
    for y in range(y1, y2):
        a = rnd.randint(0, strength)
        td.line([x1, y, x2, y], fill=(255, 255, 255, a) if rnd.random() < 0.55 else (0, 0, 0, a // 2))
    tex.putalpha(Image.composite(tex.split()[3], Image.new("L", img.size, 0), mask))
    img.alpha_composite(tex)


def silver_front():
    W, H = SW_ * S, SH_ * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body = rmask((W, H), [0, 0, W - 1, H - 1], 8 * S)
    # 테두리(하우징): 왼쪽 띠는 밝은 옆면, 오른쪽 띠는 둥글게 휘며 어두워짐
    hgrad(img, (0, 0, W, H), [(232, 234, 237), (214, 216, 220), (178, 181, 186), (182, 185, 190), (176, 179, 184),
                              (176, 179, 184), (184, 187, 192), (200, 203, 207), (150, 153, 158)], body)
    brushed(img, (0, 0, W, H), body, 14, 3)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=8 * S, outline=(128, 131, 137), width=S)
    # 왼쪽 옆면 / 오른쪽 곡면 경계선
    d.line([10 * S, 6 * S, 10 * S, H - 6 * S], fill=(200, 203, 208), width=S)
    d.line([272 * S, 4 * S, 272 * S, H - 4 * S], fill=(160, 163, 168), width=S)
    d.line([288 * S, 6 * S, 288 * S, H - 6 * S], fill=(214, 216, 220), width=S)
    # 위·아래 테두리 윗면 하이라이트
    d.line([12 * S, 3 * S, 270 * S, 3 * S], fill=(222, 224, 228), width=S)
    # 앞판 (한 단 들어감): 안쪽 그림자 + 밝은 아랫단
    px1, py1, px2, py2 = [v * S for v in PLATE]
    plate = rmask((W, H), [px1, py1, px2, py2], 6 * S)
    grad = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    vgrad(img, (px1, py1, px2, py2), (196, 198, 202), (180, 183, 188), plate)
    brushed(img, (px1, py1, px2, py2), plate, 18, 9)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([px1, py1, px2, py2], radius=6 * S, outline=(112, 115, 121), width=2 * S)
    d.line([px1 + 4 * S, py1 + 2 * S, px2 - 4 * S, py1 + 2 * S], fill=(140, 143, 148), width=S)
    d.line([px1 + 4 * S, py2 - 2 * S, px2 - 4 * S, py2 - 2 * S], fill=(226, 228, 231), width=S)
    # 로고 자리 / 마이크 구멍
    d.text((40 * S, 44 * S), "HOMENET", font=font(15 * S, True), fill=(52, 54, 60))
    d.rounded_rectangle([210 * S, 46 * S, 214 * S, 58 * S], radius=2 * S, fill=(26, 28, 32))
    # 렌즈 링 그림자 (링은 튀어나온 원형 부품)
    cx, cy = RING_C
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([(cx - RING_R) * S, (cy - RING_R + 4) * S, (cx + RING_R + 4) * S, (cy + RING_R + 8) * S],
                               fill=(0, 0, 0, 90))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4 * S)))
    d = ImageDraw.Draw(img)
    # 스피커 그릴 (작은 구멍 격자)
    for r in range(8):
        for c in range(8):
            x, y = (58 + c * 6.4) * S, (282 + r * 6.4) * S
            d.ellipse([x, y, x + 3 * S, y + 3 * S], fill=(46, 48, 54))
    # LED, 종 아이콘
    d.ellipse([178 * S, 274 * S, 186 * S, 282 * S], fill=(150, 30, 24), outline=(70, 20, 16))
    bx, by = 151, 307
    d.chord([(bx - 4) * S, (by - 6) * S, (bx + 4) * S, (by + 4) * S], 180, 360, fill=(64, 66, 72))
    d.rectangle([(bx - 4) * S, (by - 1) * S, (bx + 4) * S, (by + 2) * S], fill=(64, 66, 72))
    d.ellipse([(bx - 1.5) * S, (by + 2) * S, (bx + 1.5) * S, (by + 5) * S], fill=(64, 66, 72))
    # 버튼 자리 그림자
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([(BTN_C[0] - BTN_R) * S, (BTN_C[1] - BTN_R + 2) * S, (BTN_C[0] + BTN_R + 2) * S,
                                (BTN_C[1] + BTN_R + 4) * S], fill=(0, 0, 0, 80))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(2 * S)))
    return img


def chrome_ring(size=256):
    """흰 광택 링 (위: HOME MANAGER, 아래: 그릴) + 안쪽 크롬 링, 가운데 투명 (렌즈가 한 단 들어가 보임)"""
    def draw(img, d, n):
        r = n / 2
        k = r / RING_R
        # 흰 링 (광택 그라데이션)
        for i in range(int(r), int(CHROME_R * k), -1):
            t = (r - i) / (r - CHROME_R * k)
            v = int(246 - 26 * abs(0.4 - t) * 2)
            d.ellipse([r - i, r - i, r + i, r + i], fill=(v, v, min(255, v + 3), 255))
        d.ellipse([1, 1, n - 2, n - 2], outline=(150, 153, 160), width=max(1, n // 100))
        # 크롬 링 (어두움→밝음→어두움)
        for i in range(int(CHROME_R * k), int(LENS_R * k), -1):
            t = (CHROME_R * k - i) / ((CHROME_R - LENS_R) * k)
            v = int(110 + 130 * (1 - abs(t - 0.45) * 2.2))
            v = max(70, min(245, v))
            d.ellipse([r - i, r - i, r + i, r + i], fill=(v, v, v + 4, 255))
        # 위쪽 문구
        f = font(n * 0.07, True)
        ty = r - (CHROME_R + (RING_R - CHROME_R) / 2) * k
        d.text((r - n * 0.13, ty), "H", font=f, fill=(206, 30, 36), anchor="mm")
        d.text((r - n * 0.035, ty), "OME", font=f, fill=(40, 42, 48), anchor="mm")
        d.text((r + n * 0.13, ty + n * 0.006), "MANAGER", font=font(n * 0.035, True), fill=(70, 72, 78), anchor="mm")
        # 아래쪽 그릴 (구멍 뚫린 은색 판)
        gy = r + (CHROME_R + (RING_R - CHROME_R) / 2) * k
        gw, gh = n * 0.2, n * 0.055
        d.rounded_rectangle([r - gw, gy - gh, r + gw, gy + gh], radius=gh, fill=(196, 198, 204), outline=(140, 143, 150))
        for j in range(-9, 10):
            for q in (-1, 1):
                x, y = r + j * gw / 10, gy + q * gh * 0.4
                d.ellipse([x - n * 0.006, y - n * 0.006, x + n * 0.006, y + n * 0.006], fill=(90, 92, 98))
        # 렌즈 구멍 (투명)
        d.ellipse([r - LENS_R * k, r - LENS_R * k, r + LENS_R * k, r + LENS_R * k], fill=(0, 0, 0, 0))
    return disc(size, 2, draw)


def inner_lens(size=128):
    def draw(img, d, n):
        d.ellipse([0, 0, n - 1, n - 1], fill=(18, 19, 22))
        d.ellipse([n * 0.08, n * 0.08, n * 0.92, n * 0.92], fill=(34, 36, 42), outline=(70, 72, 80), width=max(1, n // 60))
        d.ellipse([n * 0.22, n * 0.22, n * 0.78, n * 0.78], fill=(10, 11, 15))
        d.ellipse([n * 0.34, n * 0.34, n * 0.66, n * 0.66], fill=(30, 36, 56), outline=(60, 66, 86), width=max(1, n // 80))
        d.ellipse([n * 0.43, n * 0.43, n * 0.57, n * 0.57], fill=(8, 9, 12))
        # 유리 반사
        d.chord([n * 0.12, n * 0.1, n * 0.88, n * 0.7], 200, 300, fill=(255, 255, 255, 40))
        d.ellipse([n * 0.36, n * 0.3, n * 0.44, n * 0.38], fill=(210, 216, 236))
    return disc(size, 4, draw)


def silver_button(size=64):
    def draw(img, d, n):
        d.ellipse([0, 0, n - 1, n - 1], fill=(150, 153, 158), outline=(100, 102, 108), width=n // 24)
        d.ellipse([n * 0.07, n * 0.06, n * 0.93, n * 0.92], fill=(208, 210, 214))
        d.ellipse([n * 0.12, n * 0.08, n * 0.88, n * 0.52], fill=(228, 230, 234))
        for x0 in (0.3, 0.57):
            for (dx, dy) in ((0, 0), (0.08, 0), (0, 0.09), (0.08, 0.18)):
                cx, cy = n * (x0 + dx), n * (0.4 + dy)
                d.ellipse([cx - n * 0.028, cy - n * 0.028, cx + n * 0.028, cy + n * 0.028], fill=(140, 143, 150))
    return disc(size, 4, draw)


# ====================================================================== 화이트 정사각형
QW, QH = 358, 383


def square_front():
    W, H = QW * S, QH * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body = rmask((W, H), [0, 0, W - 1, H - 1], 40 * S)
    vgrad(img, (0, 0, W, H), (250, 250, 251), (214, 216, 220), body)
    # 오른쪽 아래로 갈수록 은색 광택
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).ellipse([-W * 0.4, -H * 0.4, W * 0.9, H * 0.8], fill=(255, 255, 255, 90))
    gl = gl.filter(ImageFilter.GaussianBlur(40))
    gl.putalpha(Image.composite(gl.split()[3], Image.new("L", (W, H), 0), body))
    img.alpha_composite(gl)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=40 * S, outline=(178, 181, 187), width=2 * S)
    d.text((32 * S, 36 * S), "HOMENET", font=font(13 * S, True), fill=(80, 84, 92))
    d.rounded_rectangle([319 * S, 34 * S, 323 * S, 44 * S], radius=2 * S, fill=(50, 52, 56))
    # 검은 패널 자리 (패널은 튀어나온 부품이 따로 그림) – 그림자
    d.rounded_rectangle([106 * S, 64 * S, 256 * S, 310 * S], radius=10 * S, fill=(150, 152, 158))
    # RF 카드 패드
    d.rounded_rectangle([30 * S, 300 * S, 92 * S, 362 * S], radius=8 * S, outline=(190, 193, 199), width=3 * S, fill=(236, 237, 240))
    d.rounded_rectangle([38 * S, 308 * S, 84 * S, 354 * S], radius=6 * S, outline=(150, 153, 160), width=2 * S)
    return img


def square_panel():
    """검은 패널 정면 (150 x 246)"""
    W, H = 150 * S, 246 * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    m = rmask((W, H), [0, 0, W - 1, H - 1], 10 * S)
    vgrad(img, (0, 0, W, H), (34, 36, 40), (12, 13, 15), m)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=10 * S, outline=(70, 72, 78), width=S)
    # 렌즈
    cx, cy, r = 75 * S, 62 * S, 46 * S
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(26, 28, 32), outline=(60, 62, 68), width=2 * S)
    for i, (rr, c) in enumerate([(34, (40, 42, 48)), (24, (16, 18, 22)), (12, (36, 44, 66))]):
        d.ellipse([cx - rr * S, cy - rr * S, cx + rr * S, cy + rr * S], fill=c)
    d.ellipse([cx - 14 * S, cy - 16 * S, cx - 6 * S, cy - 8 * S], fill=(150, 160, 190))
    for i in range(12):
        import math
        a = i * math.tau / 12
        x, y = cx + math.cos(a) * 40 * S, cy + math.sin(a) * 40 * S
        d.ellipse([x - 2 * S, y - 2 * S, x + 2 * S, y + 2 * S], fill=(48, 50, 56))
    # 아이콘 (스피커 / 집)
    for x0, kind in ((10, "spk"), (120, "home")):
        d.rounded_rectangle([x0 * S, 118 * S, (x0 + 20) * S, 138 * S], radius=3 * S, fill=(235, 236, 240))
        if kind == "spk":
            d.polygon([((x0 + 5) * S, 124 * S), ((x0 + 9) * S, 124 * S), ((x0 + 14) * S, 120 * S), ((x0 + 14) * S, 136 * S), ((x0 + 9) * S, 132 * S), ((x0 + 5) * S, 132 * S)], fill=(30, 30, 34))
        else:
            d.polygon([((x0 + 10) * S, 121 * S), ((x0 + 17) * S, 128 * S), ((x0 + 15) * S, 128 * S), ((x0 + 15) * S, 135 * S), ((x0 + 5) * S, 135 * S), ((x0 + 5) * S, 128 * S), ((x0 + 3) * S, 128 * S)], fill=(30, 30, 34))
    # 흰 띠 (조명)
    vgrad(img, (0, 150 * S, W, 166 * S), (250, 250, 252), (200, 202, 208))
    d.line([0, 150 * S, W, 150 * S], fill=(255, 255, 255), width=S)
    # 점자 / 버튼 영역
    for gx, gy in ((52, 196), (58, 196), (52, 202), (72, 192), (78, 192), (72, 198), (92, 198), (98, 198), (92, 204)):
        d.ellipse([gx * S, gy * S, (gx + 3) * S, (gy + 3) * S], fill=(70, 72, 78))
    return img


# ====================================================================== 모델
def silver_model():
    w = 4.4
    h = w * SH_ / SW_
    x1, x2, y1, y2 = 8 - w / 2, 8 + w / 2, 8 - h / 2, 8 + h / 2
    bbox = (x1, y1, x2, y2)
    sx = w / SW_

    def X(u):
        return x2 - u * sx

    def Y(v):
        return y2 - v * sx

    def part(u1, v1, u2, v2, zf, side="#side"):
        rect = (X(u2), Y(v2), X(u1), Y(v1))
        f = {k: {"uv": [0, 0, 16, 16], "texture": side} for k in ("north", "south", "east", "west", "up", "down")}
        f["north"] = {"uv": rm.front_uv(*rect, bbox), "texture": "#front"}
        return {"from": [round(rect[0], 4), round(rect[1], 4), zf], "to": [round(rect[2], 4), round(rect[3], 4), 16], "faces": f}

    px1, py1, px2, py2 = PLATE
    els = [
        # 한 단 들어간 앞판
        part(px1, py1, px2, py2, 15.3),
        # 테두리: 위 / 아래
        part(4, 0, 296, py1, 15.12), part(4, py2, 296, SH_, 15.12),
        # 왼쪽: 바깥 옆면(낮게) + 안쪽 테두리
        part(0, 4, 10, SH_ - 4, 15.3), part(10, 0, px1, SH_, 15.12),
        # 오른쪽: 둥글게 휘어 내려가는 곡면 (3단)
        part(px2, 0, 272, SH_, 15.12), part(272, 2, 288, SH_ - 2, 15.22), part(288, 5, SW_, SH_ - 5, 15.42),
    ]
    els += rm.round_part(X(RING_C[0]), Y(RING_C[1]), RING_R * 2 * sx, 14.98, 15.3, "#ring", "#ringside")
    els += rm.round_part(X(RING_C[0]), Y(RING_C[1]), LENS_R * 2 * sx, 15.16, 15.3, "#lens", "#dark")
    els += rm.round_part(X(BTN_C[0]), Y(BTN_C[1]), BTN_R * 2 * sx, 15.18, 15.3, "#button", "#chrome")
    return rm.model({"particle": f"{M}:block/door_camera_silver_side", "front": f"{M}:block/door_camera_silver_front",
                     "side": f"{M}:block/door_camera_silver_side", "ring": f"{M}:block/door_camera_ring",
                     "ringside": f"{M}:block/door_camera_ring_side", "lens": f"{M}:block/door_camera_lens",
                     "chrome": f"{M}:block/door_camera_chrome", "button": f"{M}:block/door_camera_button",
                     "dark": f"{M}:block/device_dark"}, els), (w, h, 1.0)


def square_model():
    w = 4.6
    h = w * QH / QW
    x1, x2, y1, y2 = 8 - w / 2, 8 + w / 2, 8 - h / 2, 8 + h / 2
    bbox = (x1, y1, x2, y2)
    sx = w / QW
    r = 40 * sx
    els = rm.rounded_box((x1, y1, x2, y2), 15.6, 16, r, bbox, side="#side")
    i = 0.07
    els += rm.rounded_box((x1 + i, y1 + i, x2 - i, y2 - i), 15.4, 15.6, r - i, bbox, side="#side")
    # 검은 패널 (튀어나옴)
    px1, px2 = x2 - 256 * sx, x2 - 106 * sx
    py1, py2 = y2 - 310 * sx, y2 - 64 * sx
    els += rm.rounded_box((px1, py1, px2, py2), 15.28, 15.4, 10 * sx, (px1, py1, px2, py2), front="#panel", side="#dark", steps=2)
    return rm.model({"particle": f"{M}:block/door_camera_square_side", "front": f"{M}:block/door_camera_square_front",
                     "side": f"{M}:block/door_camera_square_side", "panel": f"{M}:block/door_camera_square_panel",
                     "dark": f"{M}:block/device_dark"}, els), (w, h, 0.75)


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def states(name):
    v = {}
    for f, r in ROT.items():
        for b in ("false", "true"):
            m = {"model": f"{M}:block/{name}"}
            if r:
                m["y"] = r
            v[f"facing={f},powered={b}"] = m
    return {"variants": v}


def main():
    save(silver_front().resize((512, 512), Image.LANCZOS), "block/door_camera_silver_front.png")
    side = Image.new("RGBA", (16, 16))
    hgrad(side, (0, 0, 16, 16), [(150, 153, 158), (214, 216, 220), (170, 173, 178)])
    save(side, "block/door_camera_silver_side.png")
    save(chrome_ring(), "block/door_camera_ring.png")
    save(inner_lens(), "block/door_camera_lens.png")
    save(silver_button(), "block/door_camera_button.png")
    save(solid((236, 238, 241)), "block/door_camera_ring_side.png")
    save(solid((222, 224, 228)), "block/door_camera_chrome.png")
    save(square_front().resize((256, 256), Image.LANCZOS), "block/door_camera_square_front.png")
    save(square_panel().resize((128, 128), Image.LANCZOS), "block/door_camera_square_panel.png")
    save(solid((226, 228, 232)), "block/door_camera_square_side.png")
    for name, fn in (("door_camera_silver", silver_model), ("door_camera_square", square_model)):
        mdl, size = fn()
        wj(f"models/block/{name}.json", mdl)
        wj(f"models/item/{name}.json", {"parent": f"{M}:block/{name}"})
        wj(f"blockstates/{name}.json", states(name))
        print(name, "w %.3f h %.3f depth %.2f" % size)


if __name__ == "__main__":
    main()
