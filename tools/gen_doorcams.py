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


# ====================================================================== 실버 세로형 (사진 1·3)
SW_, SH_ = 355, 433      # 사진 정면 본체 크기
S = 2                    # 그리기 배율
PLATE = (22, 33, 337, 413)   # 앞판 (튀어나온 실버 판)
RING_C, RING_R, LENS_R = (185, 158), 95, 62
BTN_C, BTN_R = (245, 323), 30


def silver_front():
    W, H = SW_ * S, SH_ * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body = rmask((W, H), [0, 0, W - 1, H - 1], 20 * S)
    # 하우징: 왼쪽 옆면이 밝게, 오른쪽은 어둡게 휘어 보이는 실버
    hgrad(img, (0, 0, W, H), [(236, 238, 240), (250, 250, 251), (204, 207, 212), (186, 189, 194), (196, 199, 204),
                              (176, 179, 184), (128, 131, 136)], body)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=20 * S, outline=(140, 143, 149), width=S)
    # 왼쪽 옆면 경계
    d.line([30 * S, 10 * S, 30 * S, H - 10 * S], fill=(176, 179, 184), width=S)
    # 앞판
    px1, py1, px2, py2 = [v * S for v in PLATE]
    plate = rmask((W, H), [px1, py1, px2, py2], 14 * S)
    vgrad(img, (px1, py1, px2, py2), (214, 216, 220), (176, 179, 184), plate)
    # 헤어라인(브러시드) 질감
    tex = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    td = ImageDraw.Draw(tex)
    import random
    random.seed(7)
    for y in range(py1, py2, 2):
        a = random.randint(0, 22)
        td.line([px1, y, px2, y], fill=(255, 255, 255, a) if random.random() < 0.5 else (0, 0, 0, a // 2))
    tex.putalpha(Image.composite(tex.split()[3], Image.new("L", (W, H), 0), plate))
    img.alpha_composite(tex)
    d.rounded_rectangle([px1, py1, px2, py2], radius=14 * S, outline=(118, 121, 127), width=2 * S)
    d.rounded_rectangle([px1 + 3 * S, py1 + 3 * S, px2 - 3 * S, py2 - 3 * S], radius=12 * S, outline=(232, 234, 237), width=S)
    # 로고 자리, 마이크
    d.text((40 * S, 40 * S), "HOMENET", font=font(15 * S, True), fill=(40, 42, 48))
    d.rounded_rectangle([271 * S, 80 * S, 275 * S, 94 * S], radius=2 * S, fill=(30, 32, 36))
    # 렌즈 둘레 그림자 (실제 링/렌즈는 튀어나온 원형 부품)
    cx, cy = RING_C
    d.ellipse([(cx - RING_R - 3) * S, (cy - RING_R + 2) * S, (cx + RING_R + 3) * S, (cy + RING_R + 6) * S], fill=(120, 123, 128))
    # 스피커 그릴
    for r in range(8):
        for c in range(8):
            x, y = (106 + c * 8) * S, (290 + r * 8.5) * S
            d.ellipse([x, y, x + 4 * S, y + 4 * S], fill=(58, 60, 66))
    # LED, 종 아이콘, 버튼 자리
    d.ellipse([239 * S, 282 * S, 247 * S, 290 * S], fill=(210, 40, 30), outline=(120, 20, 15))
    bx, by = 198, 323
    d.chord([(bx - 6) * S, (by - 8) * S, (bx + 6) * S, (by + 6) * S], 180, 360, fill=(70, 72, 78))
    d.rectangle([(bx - 6) * S, (by - 1) * S, (bx + 6) * S, (by + 4) * S], fill=(70, 72, 78))
    d.ellipse([(bx - 2) * S, (by + 4) * S, (bx + 2) * S, (by + 8) * S], fill=(70, 72, 78))
    d.ellipse([(BTN_C[0] - BTN_R - 2) * S, (BTN_C[1] - BTN_R) * S, (BTN_C[0] + BTN_R + 2) * S, (BTN_C[1] + BTN_R + 4) * S], fill=(130, 133, 138))
    return img


def chrome_ring(size=256):
    """크롬 렌즈 링 (가운데는 투명 → 안쪽 렌즈가 한 단 들어가 보임) + 링 아래 그릴 + HOME manager"""
    def draw(img, d, n):
        r = n / 2
        for i in range(int(r), int(r * LENS_R / RING_R), -1):
            t = (r - i) / (r - r * LENS_R / RING_R)
            v = int(228 - 95 * abs(0.3 - t) * 1.5)
            d.ellipse([r - i, r - i, r + i, r + i], fill=(v, v, min(255, v + 4), 255))
        # 바깥 테두리 / 안쪽 테두리 선
        d.ellipse([1, 1, n - 2, n - 2], outline=(120, 123, 128), width=max(1, n // 120))
        ri = r * LENS_R / RING_R
        # 링 아래쪽 그릴 (밝은 띠)
        gy1, gy2 = r + ri * 0.98, r + ri * 0.98 + n * 0.07
        d.rounded_rectangle([r - n * 0.18, gy1, r + n * 0.18, gy2], radius=n * 0.03, fill=(236, 238, 242), outline=(170, 172, 178))
        for k in range(-8, 9):
            x = r + k * n * 0.019
            d.line([x, gy1 + n * 0.012, x, gy2 - n * 0.012], fill=(170, 173, 180), width=max(1, n // 200))
        # 위쪽 문구
        f = font(n * 0.058, True)
        d.text((r, r - ri - n * 0.06), "HOME manager", font=f, fill=(170, 30, 30), anchor="mm")
        # 가운데 구멍
        d.ellipse([r - ri, r - ri, r + ri, r + ri], fill=(0, 0, 0, 0))
    return disc(size, 2, draw)


def inner_lens(size=128):
    def draw(img, d, n):
        d.ellipse([0, 0, n - 1, n - 1], fill=(22, 24, 28))
        d.ellipse([n * 0.12, n * 0.12, n * 0.88, n * 0.88], fill=(40, 42, 48), outline=(80, 82, 90), width=max(1, n // 50))
        d.ellipse([n * 0.26, n * 0.26, n * 0.74, n * 0.74], fill=(14, 16, 22))
        d.ellipse([n * 0.38, n * 0.38, n * 0.62, n * 0.62], fill=(36, 44, 70))
        d.ellipse([n * 0.33, n * 0.3, n * 0.45, n * 0.42], fill=(200, 208, 230))
        d.ellipse([n * 0.58, n * 0.6, n * 0.64, n * 0.66], fill=(120, 130, 160))
    return disc(size, 4, draw)


def silver_button(size=64):
    def draw(img, d, n):
        d.ellipse([0, 0, n - 1, n - 1], fill=(160, 163, 168), outline=(110, 112, 118), width=n // 24)
        d.ellipse([n * 0.08, n * 0.06, n * 0.92, n * 0.9], fill=(214, 216, 220))
        d.ellipse([n * 0.12, n * 0.08, n * 0.88, n * 0.5], fill=(232, 234, 238))
        # 점자 (호출)
        for x0 in (0.3, 0.56):
            for (dx, dy) in ((0, 0), (0.07, 0), (0, 0.08), (0.07, 0.16)):
                cx, cy = n * (x0 + dx), n * (0.42 + dy)
                d.ellipse([cx - n * 0.025, cy - n * 0.025, cx + n * 0.025, cy + n * 0.025], fill=(150, 153, 160))
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
    sx = w / SW_            # 사진 1px → 블록 픽셀

    def X(u):               # 정면 기준 왼쪽이 +x
        return x2 - u * sx

    def Y(v):
        return y2 - v * sx

    # 하우징 (옆면이 둥근 실버 몸통)
    els = rm.rounded_box((x1, y1, x2, y2), 15.2, 16, 20 * sx, bbox, side="#side", steps=4)
    # 앞판 (한 단 튀어나옴)
    els += rm.rounded_box((X(PLATE[2]), Y(PLATE[3]), X(PLATE[0]), Y(PLATE[1])), 15.0, 15.2, 14 * sx, bbox, side="#side", steps=3)
    # 크롬 링 (가운데 비어 있음) + 한 단 들어간 렌즈
    els += rm.round_part(X(RING_C[0]), Y(RING_C[1]), RING_R * 2 * sx, 14.62, 15.0, "#ring", "#chrome")
    els += rm.round_part(X(RING_C[0]), Y(RING_C[1]), LENS_R * 2 * sx, 14.86, 15.0, "#lens", "#dark")
    # 호출 버튼
    els += rm.round_part(X(BTN_C[0]), Y(BTN_C[1]), BTN_R * 2 * sx, 14.88, 15.0, "#button", "#chrome")
    return rm.model({"particle": f"{M}:block/door_camera_silver_side", "front": f"{M}:block/door_camera_silver_front",
                     "side": f"{M}:block/door_camera_silver_side", "ring": f"{M}:block/door_camera_ring",
                     "lens": f"{M}:block/door_camera_lens", "chrome": f"{M}:block/door_camera_chrome",
                     "button": f"{M}:block/door_camera_button", "dark": f"{M}:block/device_dark"}, els), (w, h, 1.4)


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
    save(silver_front().resize((256, 256), Image.LANCZOS), "block/door_camera_silver_front.png")
    side = Image.new("RGBA", (16, 16))
    hgrad(side, (0, 0, 16, 16), [(150, 153, 158), (214, 216, 220), (170, 173, 178)])
    save(side, "block/door_camera_silver_side.png")
    save(chrome_ring(), "block/door_camera_ring.png")
    save(inner_lens(), "block/door_camera_lens.png")
    save(silver_button(), "block/door_camera_button.png")
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
