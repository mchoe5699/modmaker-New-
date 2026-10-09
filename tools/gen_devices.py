"""
비디오폰 / 인터폰 / 경비실기 / 도어폰 / 도어카메라 텍스처·모델·블록상태 + 도어 링커·대시보드 아이템 아이콘.
각 기기 정면은 실제 비율로 그린 뒤 정사각형 텍스처에 늘려 담고, 모델 UV 0~16 전체로 펼친다.
"""
import json
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/qwertys_homenet"
M = "qwertys_homenet"
TEX = os.path.join(ROOT, "textures")
KR = ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 1)


def save(img, rel):
    p = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def wj(rel, obj):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def canvas(w_px, h_px, s=24):
    """w_px x h_px 블록픽셀 크기 기기를 s배로 그림"""
    img = Image.new("RGBA", (round(w_px * s), round(h_px * s)), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img), s


def finish(img, size=128):
    return img.resize((size, size), Image.LANCZOS)


def person(d, box):
    """카메라 화면 속 방문자 실루엣"""
    x1, y1, x2, y2 = box
    w, h = x2 - x1, y2 - y1
    for i in range(int(h)):
        t = i / h
        c = (round(110 - 40 * t), round(122 - 40 * t), round(136 - 40 * t))
        d.line([x1, y1 + i, x2, y1 + i], fill=c)
    cx = (x1 + x2) / 2
    d.ellipse([cx - w * 0.13, y1 + h * 0.18, cx + w * 0.13, y1 + h * 0.52], fill=(34, 38, 46))
    d.rounded_rectangle([cx - w * 0.3, y1 + h * 0.56, cx + w * 0.3, y2 + h * 0.2], radius=w * 0.12, fill=(34, 38, 46))


# ---------------------------------------------------------------- 비디오폰 12 x 8
def video_phone(ringing):
    img, d, s = canvas(12, 8)
    W, H = img.size
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=s * 0.6, fill=(242, 243, 246), outline=(176, 180, 190), width=3)
    sx1, sy1, sx2, sy2 = s * 0.6, s * 0.7, s * 8.4, s * 7.3
    d.rounded_rectangle([sx1, sy1, sx2, sy2], radius=s * 0.15, fill=(14, 16, 22))
    if ringing:
        person(d, (sx1 + 6, sy1 + 6, sx2 - 6, sy2 - 6))
        d.rectangle([sx1 + 10, sy1 + 10, sx1 + 22, sy1 + 22], fill=(220, 60, 60))
    else:
        d.polygon([(sx1, sy1), (sx1 + s * 3, sy1), (sx1, sy1 + s * 4)], fill=(28, 32, 42))
    # 오른쪽 버튼 4개: 통화 / 문열림 / 경비 / 인터폰
    cols = [(60, 170, 90), (60, 130, 220), (230, 150, 50), (150, 150, 160)]
    for i in range(4):
        y = s * (1.1 + i * 1.55)
        d.rounded_rectangle([s * 9.0, y, s * 11.3, y + s * 1.1], radius=s * 0.3, fill=(222, 224, 230), outline=(160, 166, 178), width=2)
        d.ellipse([s * 9.25, y + s * 0.3, s * 9.75, y + s * 0.8], fill=cols[i] if (i == 0 and ringing) or i > 0 else (120, 200, 140))
    d.ellipse([s * 11.4, s * 0.35, s * 11.65, s * 0.6], fill=(80, 220, 120) if ringing else (60, 70, 80))
    return finish(img)


# ---------------------------------------------------------------- 인터폰 6 x 11 (수화기형)
def interphone(ringing):
    img, d, s = canvas(6, 11)
    W, H = img.size
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=s * 0.6, fill=(238, 236, 230), outline=(170, 168, 160), width=3)
    # 수화기 (왼쪽 세로)
    d.rounded_rectangle([s * 0.5, s * 0.6, s * 2.6, s * 10.4], radius=s * 0.9, fill=(250, 248, 242), outline=(186, 182, 172), width=3)
    for yy in (1.4, 9.0):
        for k in range(3):
            d.ellipse([s * (1.1 + k * 0.4), s * yy, s * (1.3 + k * 0.4), s * (yy + 0.2)], fill=(150, 146, 138))
    # 스피커 구멍
    for r in range(4):
        for c in range(3):
            d.ellipse([s * (3.4 + c * 0.55), s * (1.2 + r * 0.5), s * (3.65 + c * 0.55), s * (1.45 + r * 0.5)], fill=(120, 118, 112))
    # 버튼: 통화 / 문열림 / 경비
    labels = [(80, 170, 100), (70, 130, 220), (220, 150, 60)]
    for i, col in enumerate(labels):
        y = s * (4.4 + i * 1.7)
        d.rounded_rectangle([s * 3.2, y, s * 5.4, y + s * 1.2], radius=s * 0.3, fill=(226, 224, 218), outline=(160, 156, 148), width=2)
        d.ellipse([s * 3.45, y + s * 0.35, s * 3.95, y + s * 0.85], fill=col)
    d.ellipse([s * 4.95, s * 9.9, s * 5.3, s * 10.25], fill=(230, 60, 60) if ringing else (90, 80, 80))
    return finish(img)


# ---------------------------------------------------------------- 도어폰 5 x 8 (버튼형)
def door_phone():
    img, d, s = canvas(5, 8)
    W, H = img.size
    for y in range(H):
        t = y / H
        v = round(206 - 40 * t)
        d.line([0, y, W, y], fill=(v, v + 2, v + 6))
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=s * 0.4, outline=(120, 124, 132), width=3)
    for r in range(3):
        for c in range(5):
            d.ellipse([s * (1.0 + c * 0.7), s * (1.0 + r * 0.6), s * (1.25 + c * 0.7), s * (1.25 + r * 0.6)], fill=(70, 74, 82))
    d.rounded_rectangle([s * 0.9, s * 3.1, s * 4.1, s * 4.1], radius=s * 0.1, fill=(250, 250, 244), outline=(140, 140, 140), width=2)
    d.ellipse([s * 1.4, s * 4.7, s * 3.6, s * 6.9], fill=(150, 154, 162))
    d.ellipse([s * 1.6, s * 4.9, s * 3.4, s * 6.7], fill=(226, 228, 232))
    d.ellipse([s * 2.1, s * 5.4, s * 2.9, s * 6.2], fill=(90, 160, 230))
    return finish(img)


# ---------------------------------------------------------------- 도어카메라 6 x 9
def door_camera():
    img, d, s = canvas(6, 9)
    W, H = img.size
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=s * 0.6, fill=(40, 44, 52), outline=(150, 154, 162), width=4)
    d.ellipse([s * 1.6, s * 0.9, s * 4.4, s * 3.7], fill=(18, 20, 26), outline=(110, 116, 128), width=4)
    d.ellipse([s * 2.4, s * 1.7, s * 3.6, s * 2.9], fill=(6, 8, 12))
    d.ellipse([s * 2.6, s * 1.85, s * 2.95, s * 2.2], fill=(90, 120, 190))
    for k in range(6):
        import math
        a = k / 6 * 2 * math.pi
        cx, cy = s * 3 + math.cos(a) * s * 1.7, s * 2.3 + math.sin(a) * s * 1.7
        d.ellipse([cx - s * 0.12, cy - s * 0.12, cx + s * 0.12, cy + s * 0.12], fill=(210, 220, 255))
    for r in range(3):
        for c in range(5):
            d.ellipse([s * (1.4 + c * 0.8), s * (4.4 + r * 0.45), s * (1.6 + c * 0.8), s * (4.6 + r * 0.45)], fill=(90, 94, 104))
    d.rounded_rectangle([s * 1.5, s * 6.2, s * 4.5, s * 8.2], radius=s * 0.5, fill=(210, 214, 222), outline=(110, 114, 124), width=3)
    d.ellipse([s * 2.6, s * 6.8, s * 3.4, s * 7.6], fill=(80, 170, 240))
    return finish(img)


# ---------------------------------------------------------------- 경비실기
def guard_screen(ringing):
    img, d, s = canvas(12, 8)
    W, H = img.size
    d.rectangle([0, 0, W - 1, H - 1], fill=(48, 52, 60))
    d.rectangle([s * 0.6, s * 0.6, W - s * 0.6, H - s * 0.6], fill=(14, 16, 22))
    if ringing:
        person(d, (s * 0.9, s * 0.9, s * 7.0, H - s * 0.9))
        d.rectangle([s * 7.4, s * 1.0, W - s * 1.0, s * 3.0], fill=(200, 60, 60))
    else:
        for i in range(4):
            d.rectangle([s * 1.0, s * (1.0 + i * 1.5), W - s * 1.0, s * (2.0 + i * 1.5)], fill=(30, 52, 90))
    return finish(img)


def guard_top():
    img, d, s = canvas(14, 12)
    W, H = img.size
    d.rectangle([0, 0, W - 1, H - 1], fill=(70, 74, 82))
    # 수화기
    d.rounded_rectangle([s * 0.8, s * 1.0, s * 3.2, s * 11.0], radius=s * 1.0, fill=(30, 32, 38))
    # 키패드 4x3
    for r in range(4):
        for c in range(3):
            x, y = s * (4.2 + c * 1.6), s * (4.6 + r * 1.6)
            d.rounded_rectangle([x, y, x + s * 1.3, y + s * 1.2], radius=s * 0.2, fill=(196, 200, 210))
    # 기능 버튼
    for i, col in enumerate([(80, 170, 100), (70, 130, 220), (220, 70, 70)]):
        y = s * (4.6 + i * 2.1)
        d.rounded_rectangle([s * 9.6, y, s * 13.2, y + s * 1.6], radius=s * 0.3, fill=col)
    return finish(img)


def flat(color, size=16):
    return Image.new("RGBA", (size, size), color + (255,))


# ---------------------------------------------------------------- 아이템
def door_linker():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([5, 4, 10, 14], fill=(40, 70, 52))
    d.rectangle([6, 5, 9, 8], fill=(12, 20, 16))
    d.rectangle([6, 5, 9, 5], fill=(90, 220, 130))
    d.point([(6, 10), (9, 10), (6, 12), (9, 12)], fill=(200, 230, 210))
    d.point([(7, 11), (8, 11)], fill=(90, 220, 130))
    for y in range(1, 4):
        d.point((9, y), fill=(150, 156, 166))
    d.point((9, 0), fill=(90, 220, 130))
    # 작은 문 모양
    d.rectangle([1, 9, 3, 14], fill=(150, 100, 60))
    d.point((3, 12), fill=(240, 210, 90))
    return img


def dashboard():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 2, 14, 13], radius=1, fill=(40, 44, 52))
    d.rectangle([2, 3, 13, 12], fill=(86, 140, 70))
    d.rectangle([3, 4, 6, 6], fill=(170, 160, 120))
    d.rectangle([8, 7, 12, 11], fill=(60, 110, 200))
    d.rectangle([2, 8, 6, 12], outline=(230, 80, 80))
    d.rectangle([8, 3, 12, 6], outline=(250, 220, 80))
    d.point((10, 9), fill=(255, 255, 255))
    return img


# ---------------------------------------------------------------- 모델
def panel_model(name, tex_front, tex_side, w, h, depth, y_center=8.0):
    x1, x2 = 8 - w / 2, 8 + w / 2
    y1, y2 = y_center - h / 2, y_center + h / 2
    z1 = 16 - depth
    return {
        "parent": "block/block",
        "textures": {"particle": f"{M}:block/{tex_side}", "front": f"{M}:block/{tex_front}", "side": f"{M}:block/{tex_side}"},
        "elements": [{"from": [x1, y1, z1], "to": [x2, y2, 16], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#side"}}}],
        "display": {
            "gui": {"rotation": [0, 180, 0], "scale": [1.3, 1.3, 1.3]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1.3, 1.3, 1.3]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [0.6, 0.6, 0.6]}}}


def guard_model(screen_tex):
    return {
        "parent": "block/block",
        "textures": {"particle": f"{M}:block/guard_console_side", "top": f"{M}:block/guard_console_top",
                     "screen": f"{M}:block/{screen_tex}", "side": f"{M}:block/guard_console_side"},
        "elements": [
            {"from": [1, 0, 2], "to": [15, 3, 14], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "north": {"uv": [1, 13, 15, 16], "texture": "#side"}, "south": {"uv": [1, 13, 15, 16], "texture": "#side"},
                "east": {"uv": [2, 13, 14, 16], "texture": "#side"}, "west": {"uv": [2, 13, 14, 16], "texture": "#side"},
                "down": {"uv": [1, 2, 15, 14], "texture": "#side"}}},
            {"from": [2, 3, 10], "to": [14, 11, 11.5],
             "rotation": {"origin": [8, 3, 10.75], "axis": "x", "angle": 22.5},
             "faces": {
                 "north": {"uv": [0, 0, 16, 16], "texture": "#screen"},
                 "south": {"uv": [2, 5, 14, 13], "texture": "#side"},
                 "east": {"uv": [10, 5, 11.5, 13], "texture": "#side"}, "west": {"uv": [10, 5, 11.5, 13], "texture": "#side"},
                 "up": {"uv": [2, 10, 14, 11.5], "texture": "#side"}, "down": {"uv": [2, 10, 14, 11.5], "texture": "#side"}}}],
        "display": {
            "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
            "fixed": {"scale": [0.5, 0.5, 0.5]},
            "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}}


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_states(prop, fn):
    v = {}
    for f, r in ROT.items():
        for b in ("false", "true"):
            m = {"model": f"{M}:block/{fn(b)}"}
            if r:
                m["y"] = r
            v[f"facing={f},{prop}={b}"] = m
    return {"variants": v}


def main():
    save(video_phone(False), "block/video_phone_front.png")
    save(video_phone(True), "block/video_phone_front_ringing.png")
    save(interphone(False), "block/interphone_front.png")
    save(interphone(True), "block/interphone_front_ringing.png")
    save(door_phone(), "block/door_phone_front.png")
    save(door_camera(), "block/door_station_front.png")
    save(guard_screen(False), "block/guard_console_screen.png")
    save(guard_screen(True), "block/guard_console_screen_ringing.png")
    save(guard_top(), "block/guard_console_top.png")
    save(flat((236, 236, 240)), "block/device_white.png")
    save(flat((40, 44, 52)), "block/device_dark.png")
    save(flat((176, 180, 188)), "block/device_silver.png")
    save(flat((84, 88, 96)), "block/guard_console_side.png")
    save(door_linker(), "item/door_linker.png")
    save(dashboard(), "item/dashboard.png")

    wj("models/block/video_phone.json", panel_model("video_phone", "video_phone_front", "device_white", 12, 8, 1))
    wj("models/block/video_phone_ringing.json", panel_model("video_phone", "video_phone_front_ringing", "device_white", 12, 8, 1))
    wj("models/block/interphone.json", panel_model("interphone", "interphone_front", "device_white", 6, 11, 2, 8.5))
    wj("models/block/interphone_ringing.json", panel_model("interphone", "interphone_front_ringing", "device_white", 6, 11, 2, 8.5))
    wj("models/block/door_phone.json", panel_model("door_phone", "door_phone_front", "device_silver", 5, 8, 1))
    wj("models/block/door_station.json", panel_model("door_station", "door_station_front", "device_dark", 6, 9, 1, 8.5))
    wj("models/block/guard_console.json", guard_model("guard_console_screen"))
    wj("models/block/guard_console_ringing.json", guard_model("guard_console_screen_ringing"))
    for n in ("video_phone", "interphone", "door_phone", "guard_console"):
        wj(f"models/item/{n}.json", {"parent": f"{M}:block/{n}"})
    wj("models/item/door_linker.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/door_linker"}})
    wj("models/item/dashboard.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/dashboard"}})

    for n in ("video_phone", "interphone", "guard_console"):
        wj(f"blockstates/{n}.json", facing_states("ringing", lambda b, n=n: n + ("_ringing" if b == "true" else "")))
    wj("blockstates/door_phone.json", facing_states("powered", lambda b: "door_phone"))
    print("ok")


if __name__ == "__main__":
    main()
