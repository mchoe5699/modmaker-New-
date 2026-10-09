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


# ====================================================================== 실버 세로형
SW_, SH_ = 306, 450      # 사진 본체 크기
S = 2                    # 그리기 배율


def silver_front():
    W, H = SW_ * S, SH_ * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body = rmask((W, H), [0, 0, W - 1, H - 1], 22 * S)
    # 하우징: 양옆이 둥글게 휘어 밝아지는 실버
    hgrad(img, (0, 0, W, H), [(150, 153, 158), (226, 228, 231), (196, 199, 204), (200, 203, 207), (232, 234, 236), (150, 153, 158)], body)
    d = ImageDraw.Draw(img)
    # 앞판 (조금 어두운 실버, 둥근 모서리)
    px1, py1, px2, py2 = 28 * S, 26 * S, 282 * S, 424 * S
    plate = rmask((W, H), [px1, py1, px2, py2], 16 * S)
    vgrad(img, (px1, py1, px2, py2), (190, 193, 198), (168, 171, 177), plate)
    d.rounded_rectangle([px1, py1, px2, py2], radius=16 * S, outline=(128, 131, 137), width=2 * S)
    d.rounded_rectangle([px1 + 3 * S, py1 + 3 * S, px2 - 3 * S, py2 - 3 * S], radius=14 * S, outline=(214, 216, 220), width=S)
    # 아래쪽 띠 (사진의 앞판 아랫단)
    d.line([px1 + 6 * S, 395 * S, px2 - 6 * S, 395 * S], fill=(150, 153, 158), width=S)
    # 로고 자리
    d.text((52 * S, 56 * S), "HOMENET", font=font(15 * S, True), fill=(46, 48, 54))
    # 마이크 구멍
    d.rounded_rectangle([252 * S, 82 * S, 256 * S, 94 * S], radius=2 * S, fill=(40, 42, 46))
    # 렌즈 위 그릴
    d.rounded_rectangle([140 * S, 104 * S, 196 * S, 116 * S], radius=5 * S, fill=(210, 212, 216), outline=(150, 152, 158))
    for gx in range(144, 194, 4):
        d.line([gx * S, 107 * S, gx * S, 113 * S], fill=(160, 162, 168), width=S)
    # 렌즈 자리 (실제 렌즈는 튀어나온 원형 부품) – 아래 그림자
    d.ellipse([92 * S, 108 * S, 248 * S, 264 * S], fill=(120, 123, 128))
    # 렌즈 아래 문구
    d.text((112 * S, 262 * S), "HOME manager", font=font(10 * S), fill=(160, 40, 40))
    # 스피커
    for r in range(7):
        for c in range(8):
            x, y = (88 + c * 8 + (4 if r % 2 else 0)) * S, (308 + r * 8) * S
            d.ellipse([x, y, x + 3 * S, y + 3 * S], fill=(70, 72, 78))
    # LED, 종 모양
    d.ellipse([222 * S, 300 * S, 230 * S, 308 * S], fill=(200, 40, 30))
    d.polygon([(178 * S, 332 * S), (172 * S, 346 * S), (186 * S, 346 * S)], fill=(70, 72, 78))
    d.ellipse([177 * S, 345 * S, 183 * S, 351 * S], fill=(70, 72, 78))
    # 호출 버튼 자리
    d.ellipse([194 * S, 318 * S, 252 * S, 376 * S], fill=(140, 143, 148))
    return img


def chrome_lens(size=128):
    def draw(img, d, n):
        # 크롬 링
        for i in range(n // 2):
            t = i / (n / 2)
            v = int(150 + 90 * abs(0.5 - t) * 2)
            d.ellipse([i * 0.0 + i, i, n - i, n - i], outline=(v, v, v + 4), width=1)
            if i > n * 0.13:
                break
        d.ellipse([n * 0.13, n * 0.13, n * 0.87, n * 0.87], fill=(30, 32, 36))
        d.ellipse([n * 0.2, n * 0.2, n * 0.8, n * 0.8], fill=(44, 46, 52), outline=(90, 92, 98), width=max(1, n // 60))
        d.ellipse([n * 0.33, n * 0.33, n * 0.67, n * 0.67], fill=(18, 20, 26))
        d.ellipse([n * 0.42, n * 0.42, n * 0.58, n * 0.58], fill=(40, 50, 80))
        d.ellipse([n * 0.38, n * 0.36, n * 0.46, n * 0.44], fill=(220, 225, 240))
    return disc(size, 4, draw)


def silver_button(size=64):
    def draw(img, d, n):
        d.ellipse([0, 0, n - 1, n - 1], fill=(170, 173, 178), outline=(120, 122, 128), width=n // 24)
        d.ellipse([n * 0.12, n * 0.1, n * 0.88, n * 0.86], fill=(206, 208, 212))
        d.ellipse([n * 0.12, n * 0.1, n * 0.88, n * 0.5], fill=(222, 224, 228))
        f = font(n * 0.18)
        d.text((n / 2, n / 2), "호 출", font=f, fill=(140, 142, 148), anchor="mm")
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
    w = 4.0
    h = w * SH_ / SW_
    x1, x2, y1, y2 = 8 - w / 2, 8 + w / 2, 8 - h / 2, 8 + h / 2
    bbox = (x1, y1, x2, y2)
    sx = w / SW_            # 사진 1px → 블록 픽셀
    els = rm.rounded_box((x1, y1, x2, y2), 15.4, 16, 22 * sx, bbox, side="#side")
    # 앞판 (조금 튀어나옴)
    px1, px2 = x2 - 282 * sx, x2 - 28 * sx          # 정면 기준 좌우가 뒤집힘
    py1, py2 = y2 - 424 * sx, y2 - 26 * sx
    els += rm.rounded_box((px1, py1, px2, py2), 15.15, 15.4, 16 * sx, bbox, side="#side")
    # 크롬 렌즈 (원형)
    lcx, lcy, ld = x2 - 170 * sx, y2 - 186 * sx, 156 * sx
    els += rm.round_part(lcx, lcy, ld, 14.9, 15.15, "#lens", "#chrome")
    # 호출 버튼 (원형)
    bcx, bcy, bd = x2 - 223 * sx, y2 - 347 * sx, 58 * sx
    els += rm.round_part(bcx, bcy, bd, 15.07, 15.15, "#button", "#chrome")
    return rm.model({"particle": f"{M}:block/door_camera_silver_side", "front": f"{M}:block/door_camera_silver_front",
                     "side": f"{M}:block/door_camera_silver_side", "lens": f"{M}:block/door_camera_lens",
                     "chrome": f"{M}:block/door_camera_chrome", "button": f"{M}:block/door_camera_button"}, els), (w, h, 1.1)


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
    save(solid((206, 209, 213)), "block/door_camera_silver_side.png")
    save(chrome_lens(), "block/door_camera_lens.png")
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
