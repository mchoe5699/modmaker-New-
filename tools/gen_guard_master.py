"""
KOCOM ASTRO KGP-70K 경비실기 텍스처 / 모델 / 블록상태.

정면(설명서 5쪽, 사진 4~7):
 왼쪽 송수화기 + 받침 홈, 스피커 구멍 / 오른쪽 검은 앞판: 7인치 화면, 로고,
 기능 LED 버튼 4개(전화·부재·관리자·비상|전원), 카메라(센서),
 10키(1~9, *, 0, #) + 세대·경비·로비·문열림, 조그 버튼, 마이크.
블록: 검은 받침대 위에 본체가 22.5° 뒤로 기울어져 있고, 수화기·꼬인 코드가 붙어 있다.
GUI 본체 좌표는 GuardMasterScreen 과 같다 (580 x 510 단위, 텍스처는 2배).
"""
import json
import math
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

BW, BH = 580, 510
S = 2
SX, SY, SW, SH = 187, 36, 353, 199
HX, HY, HW, HH = 8, 6, 118, 470
PANEL = (160, 10, 575, 460)
KEY_X = [213, 266, 319, 378]
KEY_Y = [300, 336, 372, 408]
KEYS = [["1", "2", "3", "세 대"], ["4", "5", "6", "경 비"], ["7", "8", "9", "로 비"], ["*", "0", "#", "문열림"]]
JOG = (478, 358, 58, 20)
FN_X = [240, 290, 342, 404]
FN_Y = 262
FN_LABELS = ["전 화", "부 재", "관리자", "비상 | 전원"]


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


def grad(img, box, top, bottom, mask=None, horizontal=False):
    x1, y1, x2, y2 = [int(v) for v in box]
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    n = (x2 - x1) if horizontal else (y2 - y1)
    for i in range(max(1, n)):
        t = i / max(1, n - 1)
        c = tuple(round(top[k] + (bottom[k] - top[k]) * t) for k in range(3)) + (255,)
        if horizontal:
            d.line([x1 + i, y1, x1 + i, y2 - 1], fill=c)
        else:
            d.line([x1, y1 + i, x2 - 1, y1 + i], fill=c)
    if mask is not None:
        layer.putalpha(Image.composite(layer.split()[3], Image.new("L", img.size, 0), mask))
    img.alpha_composite(layer)


def rmask(size, box, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=r, fill=255)
    return m


def P(v):
    return v * S


# ---------------------------------------------------------------- 화면 그림 (블록용)
def screen_image(mode):
    W, H = 400, 225
    bg = Image.open(os.path.join(TEX, "gui/wallpad_bg.png")).convert("RGBA").resize((W, H))
    icons = Image.open(os.path.join(TEX, "gui/wallpad_icons.png")).convert("RGBA")
    cams = Image.open(os.path.join(TEX, "gui/wallpad_cams.png")).convert("RGBA")
    img = bg.copy()
    d = ImageDraw.Draw(img, "RGBA")

    def ic(i, x, y, s):
        img.alpha_composite(icons.crop(((i % 8) * 128, (i // 8) * 128, (i % 8) * 128 + 128, (i // 8) * 128 + 128)).resize((s, s)), (x, y))

    if mode == "idle":
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rectangle([0, 0, 132, H], fill=(16, 42, 85, 80))
        img.alpha_composite(layer)
        ic(6, 8, 8, 40)
        d.text((56, 6), "24℃", font=font(22), fill=(255, 255, 255))
        d.text((10, 54), "월요일 05.09 11:21", font=font(10), fill=(255, 255, 255))
        for r in range(5):
            for c in range(7):
                d.text((10 + c * 17, 90 + r * 18), str(r * 7 + c + 1), font=font(8), fill=(255, 255, 255))
        d.text((320, 8), "HOMENET", font=font(12, True), fill=(255, 255, 255))
        for i, (x, y, k) in enumerate([(170, 56, 0), (240, 56, 2), (310, 56, 3), (170, 128, 4)]):
            ic(k, x, y, 44)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rectangle([140, 192, 394, 206], fill=(0, 0, 0, 160))
        img.alpha_composite(layer)
    else:
        idx = 2 if mode == "ringing" else 3
        cam = cams.crop(((idx % 2) * 512, (idx // 2) * 288, (idx % 2) * 512 + 480, (idx // 2) * 288 + 270)).resize((W, H))
        img = cam
        d = ImageDraw.Draw(img, "RGBA")
        d.rectangle([0, 0, W, 26], fill=(0, 0, 0, 170))
        txt = "세대에서 호출이 왔습니다" if mode == "ringing" else "통화 중  00:42"
        d.text((W / 2, 13), txt, font=font(16, True), fill=(255, 214, 90) if mode == "ringing" else (156, 240, 176), anchor="mm")
    return img


# ---------------------------------------------------------------- 본체
def body(screen=None):
    W, H = P(BW), P(BH)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    m = rmask((W, H), [0, 0, W - 1, H - 1], P(16))
    grad(img, (0, 0, W, H), (252, 252, 253), (224, 226, 230), m)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=P(16), outline=(176, 179, 186), width=S)
    # 오른쪽 옆면 음영, 아래 받침 띠
    d.rectangle([W - P(4), P(12), W - P(1), H - P(12)], fill=(206, 209, 214))
    d.line([P(4), P(470), W - P(4), P(470)], fill=(200, 203, 209), width=S)
    grad(img, (P(4), P(471), W - P(4), H - P(4)), (236, 238, 241), (214, 216, 221))
    # 송수화기 받침 홈
    d.rounded_rectangle([P(4), P(2), P(132), P(482)], radius=P(16), fill=(232, 234, 238), outline=(196, 199, 205), width=S)
    d.rounded_rectangle([P(10), P(10), P(126), P(474)], radius=P(14), fill=(214, 217, 222))
    # 수화기 걸이 (가운데 홈)
    d.rounded_rectangle([P(56), P(214), P(80), P(262)], radius=P(6), fill=(190, 193, 199))
    # 스피커 (받침 옆 세로 구멍들)
    for i in range(12):
        y = P(296 + i * 10)
        d.rounded_rectangle([P(140), y, P(153), y + P(4)], radius=P(2), fill=(150, 153, 160))
    # 검은 앞판
    x1, y1, x2, y2 = [P(v) for v in PANEL]
    pm = rmask((W, H), [x1, y1, x2, y2], P(12))
    grad(img, (x1, y1, x2, y2), (40, 42, 47), (16, 17, 20), pm)
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).polygon([(x1, y1), (x2, y1), (x2, y1 + P(120)), (x1, y1 + P(260))], fill=(255, 255, 255, 14))
    gl.putalpha(Image.composite(gl.split()[3], Image.new("L", (W, H), 0), pm))
    img.alpha_composite(gl)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x1, y1, x2, y2], radius=P(12), outline=(70, 72, 78), width=S)
    # 화면
    d.rectangle([P(SX - 5), P(SY - 5), P(SX + SW + 5), P(SY + SH + 5)], fill=(8, 9, 11), outline=(60, 62, 68), width=S)
    if screen is not None:
        img.alpha_composite(screen.resize((P(SW), P(SH)), Image.LANCZOS), (P(SX), P(SY)))
    else:
        d.rectangle([P(SX), P(SY), P(SX + SW), P(SY + SH)], fill=(14, 16, 22))
    # 로고
    d.text((P(SX + SW), P(22)), "HOMENET", font=font(P(10), True), fill=(225, 228, 235), anchor="rm")
    # 기능 LED 버튼
    fl = font(P(7.5))
    for x, lab in zip(FN_X, FN_LABELS):
        d.rounded_rectangle([P(x - 11), P(FN_Y - 3), P(x + 11), P(FN_Y + 3)], radius=P(3), fill=(58, 60, 66), outline=(90, 92, 98))
        d.text((P(x), P(FN_Y + 12)), lab, font=fl, fill=(170, 174, 182), anchor="mm")
    # 카메라 / 센서
    d.ellipse([P(512), P(253), P(530), P(271)], fill=(10, 10, 12), outline=(84, 86, 92), width=S)
    d.ellipse([P(517), P(258), P(523), P(264)], fill=(40, 46, 70))
    # 10키
    for r in range(4):
        for c in range(4):
            w = 50 if c == 3 else 40
            kx, ky = KEY_X[c], KEY_Y[r]
            box = [P(kx - w / 2), P(ky - 13), P(kx + w / 2), P(ky + 13)]
            km = rmask((W, H), box, P(7))
            grad(img, box, (250, 250, 252), (196, 199, 205), km)
            d = ImageDraw.Draw(img)
            d.rounded_rectangle(box, radius=P(7), outline=(120, 123, 130), width=S)
            lab = KEYS[r][c]
            f = font(P(15), True) if c < 3 else font(P(10.5), True)
            d.text((P(kx), P(ky)), lab, font=f, fill=(34, 36, 42), anchor="mm")
    # 조그
    jx, jy, jr, ji = JOG
    for i in range(jr, 0, -1):
        t = i / jr
        if i > jr - 6:
            v = int(40 + (jr - i) * 8)
        elif i > ji + 2:
            v = int(30 + 22 * (1 - abs(t - 0.6)))
        else:
            v = int(54 + (ji - i) * 2)
        d.ellipse([P(jx - i), P(jy - i), P(jx + i), P(jy + i)], fill=(v, v, v + 3))
    d.ellipse([P(jx - jr), P(jy - jr), P(jx + jr), P(jy + jr)], outline=(96, 98, 104), width=S)
    d.ellipse([P(jx - ji - 2), P(jy - ji - 2), P(jx + ji + 2), P(jy + ji + 2)], outline=(20, 20, 22), width=2 * S)
    d.ellipse([P(jx - ji + 4), P(jy - ji + 2), P(jx + ji - 6), P(jy - 2)], fill=(80, 82, 88))
    # 마이크
    d.ellipse([P(553), P(428), P(559), P(434)], fill=(8, 8, 10))
    return img


def handset():
    W, H = P(HW), P(HH)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([P(4), P(6), W - P(1), H - P(1)], radius=P(22), fill=(0, 0, 0, 70))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(P(3))))
    m = rmask((W, H), [0, 0, W - P(4), H - P(5)], P(22))
    grad(img, (0, 0, W, H), (255, 255, 255), (206, 209, 214), m, horizontal=True)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - P(4), H - P(5)], radius=P(22), outline=(176, 179, 186), width=S)
    # 가운데 능선
    d.line([P(18), P(40), P(18), H - P(40)], fill=(255, 255, 255), width=P(2))
    d.line([P(96), P(40), P(96), H - P(40)], fill=(196, 199, 205), width=S)
    # 수화부 / 송화부 구멍
    for (cy, rows) in ((86, 5), (392, 3)):
        for r in range(rows):
            for c in range(5 - abs(r - rows // 2)):
                n = 5 - abs(r - rows // 2)
                x = P(57 - (n - 1) * 6 + c * 12)
                y = P(cy - (rows - 1) * 5 + r * 10)
                d.ellipse([x - P(2), y - P(2), x + P(2), y + P(2)], fill=(150, 153, 160))
    # 코드 연결부
    d.rounded_rectangle([P(48), H - P(16), P(68), H - P(4)], radius=P(4), fill=(70, 72, 78))
    return img


def disc(size, draw):
    big = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    draw(ImageDraw.Draw(big), size * 4)
    return big.resize((size, size), Image.LANCZOS)


def jog_tex():
    def dr(d, n):
        for i in range(n // 2, 0, -1):
            t = i / (n / 2)
            v = int(36 + 24 * (1 - abs(t - 0.62)) if t > 0.36 else 60 + (0.36 - t) * 60)
            d.ellipse([n / 2 - i, n / 2 - i, n / 2 + i, n / 2 + i], fill=(v, v, v + 3, 255))
        d.ellipse([1, 1, n - 2, n - 2], outline=(100, 102, 108), width=n // 40)
        r = n * 0.18
        d.ellipse([n / 2 - r, n / 2 - r, n / 2 + r, n / 2 + r], outline=(16, 16, 18), width=n // 40)
    return disc(64, dr)


def solid(rgb):
    return Image.new("RGBA", (16, 16), rgb + (255,))


# ---------------------------------------------------------------- 모델
ORIGIN_Y, FRONT_Z = 0.7, 4.0
BODY = (1.0, ORIGIN_Y, 15.0, ORIGIN_Y + 14.0 * BH / BW)   # x1, y1, x2, y2 (세운 상태)
ANGLE = 22.5


def bx_(u):
    return BODY[2] - u * (BODY[2] - BODY[0]) / BW


def by_(v):
    return BODY[3] - v * (BODY[3] - BODY[1]) / BH


TILT_ON = True


def tilt(els):
    if not TILT_ON:
        return els
    for e in els:
        e["rotation"] = {"origin": [8, ORIGIN_Y, FRONT_Z], "axis": "x", "angle": ANGLE}
    return els


def box(fr, to, tex_all, front=None):
    f = {k: {"uv": [0, 0, 16, 16], "texture": tex_all} for k in ("north", "south", "east", "west", "up", "down")}
    if front:
        f["north"] = front
    return {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to], "faces": f}


def model(front_tex, with_handset, part="full"):
    """part: full (아이템용, 22.5도 기울인 전체), body (세운 본체만 - 렌더러가 각도만큼 돌림), static (받침대·코드만)"""
    global TILT_ON
    TILT_ON = part == "full"
    bbox = BODY
    els = []
    # 하우징 (흰색)
    els += tilt(rm.rounded_box(BODY, FRONT_Z + 0.25, FRONT_Z + 1.6, 0.35, bbox, side="#white", steps=2))
    # 검은 앞판
    px1, py1, px2, py2 = PANEL
    els += tilt(rm.rounded_box((bx_(px2), by_(py2), bx_(px1), by_(py1)), FRONT_Z + 0.08, FRONT_Z + 0.25, 0.3, bbox,
                               side="#black", steps=2))
    # 10키 (살짝 튀어나옴)
    for r in range(4):
        for c in range(4):
            w = 50 if c == 3 else 40
            u1, u2 = KEY_X[c] - w / 2, KEY_X[c] + w / 2
            v1, v2 = KEY_Y[r] - 13, KEY_Y[r] + 13
            rect = (bx_(u2), by_(v2), bx_(u1), by_(v1))
            front = {"uv": rm.front_uv(*rect, bbox), "texture": "#front"}
            els += tilt([box((rect[0], rect[1], FRONT_Z - 0.06), (rect[2], rect[3], FRONT_Z + 0.08), "#key", front)])
    # 조그 (원형)
    jx, jy, jr, _ = JOG
    cx, cy, rr = bx_(jx), by_(jy), jr * (BODY[2] - BODY[0]) / BW
    jog = box((cx - rr, cy - rr, FRONT_Z - 0.16), (cx + rr, cy + rr, FRONT_Z + 0.08), "#black",
              {"uv": [0, 0, 16, 16], "texture": "#jog"})
    els += tilt([jog])
    # 송수화기
    if with_handset:
        hb = (bx_(HX + HW), by_(HY + HH), bx_(HX), by_(HY))
        els += tilt(rm.rounded_box(hb, FRONT_Z - 0.95, FRONT_Z + 0.25, 0.55, hb, front="#handset", side="#white", steps=3))
        inset = 0.12
        els += tilt(rm.rounded_box((hb[0] + inset, hb[1] + inset, hb[2] - inset, hb[3] - inset), FRONT_Z - 1.15, FRONT_Z - 0.95, 0.45,
                                   hb, front="#handset", side="#white", steps=3))
    if part == "static":
        els = []
    if part != "body":
        # 받침대 (검정)
        els.append(box((2.4, 0, 3.4), (13.6, 0.7, 3.8), "#black"))
        els.append(box((2.0, 0, 3.8), (14.0, 0.7, 14.2), "#black"))
        els.append(box((2.4, 0, 14.2), (13.6, 0.7, 14.6), "#black"))
        if part == "full":
            els.append(box((3.5, 0.7, 7.25), (12.5, 6.9, 8.75), "#black"))
        els.append(box((4.5, 0.7, 8.75), (11.5, 3.0, 10.4), "#black"))
        els.append(box((3.0, 0.7, 10.4), (13.0, 1.6, 13.4), "#black"))
        # 꼬인 코드 (수화기 아래 → 책상 앞으로)
        path = [(12.9, 2.1, 3.3), (13.1, 1.3, 2.9), (13.0, 0.75, 2.3), (12.5, 0.45, 1.7), (11.7, 0.35, 1.25), (10.8, 0.35, 1.0),
                (9.9, 0.35, 0.95)]
        pts = []
        for i in range(len(path) - 1):
            a, b = path[i], path[i + 1]
            dist = math.dist(a, b)
            n = max(1, int(dist / 0.28))
            for k in range(n):
                t = k / n
                pts.append(tuple(a[j] + (b[j] - a[j]) * t for j in range(3)))
        for i, (x, y, z) in enumerate(pts):
            o = 0.12 if i % 2 else -0.12
            s = 0.22
            els.append(box((x - s, max(0.0, y - s + o), z - s), (x + s, max(0.0, y - s + o) + 2 * s, z + s), "#cord"))
    tex = {"particle": f"{M}:block/guard_master_white", "front": f"{M}:block/{front_tex}",
           "white": f"{M}:block/guard_master_white", "black": f"{M}:block/guard_master_black",
           "key": f"{M}:block/guard_master_key", "jog": f"{M}:block/guard_master_jog",
           "handset": f"{M}:block/guard_master_handset", "cord": f"{M}:block/guard_master_cord"}
    display = {
        "gui": {"rotation": [25, 200, 0], "translation": [0, 0.5, 0], "scale": [0.68, 0.68, 0.68]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    }
    return rm.model(tex, els, display)


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def states():
    v = {}
    for f, r in ROT.items():
        for ring in ("false", "true"):
            for off in ("false", "true"):
                for pw in ("false", "true"):
                    name = "guard_master"
                    mdl = {"model": f"{M}:block/{name}"}
                    if r:
                        mdl["y"] = r
                    v[f"facing={f},offhook={off},powered={pw},ringing={ring}"] = mdl
    return {"variants": v}


def main():
    save(body(), "gui/guard_master_body.png")
    save(handset(), "gui/guard_master_handset.png")
    for mode, name in (("idle", "guard_master_front"), ("ringing", "guard_master_front_ringing"), ("talk", "guard_master_front_talk")):
        save(body(screen_image(mode)).resize((512, 512), Image.LANCZOS), f"block/{name}.png")
    save(handset().resize((64, 256), Image.LANCZOS), "block/guard_master_handset.png")
    save(solid((236, 238, 241)), "block/guard_master_white.png")
    save(solid((24, 25, 28)), "block/guard_master_black.png")
    save(solid((200, 203, 209)), "block/guard_master_key.png")
    save(solid((52, 54, 58)), "block/guard_master_cord.png")
    save(jog_tex(), "block/guard_master_jog.png")
    # 블록: 받침대·코드 (본체는 GuardMasterRenderer 가 각도만큼 돌려 그림)
    wj("models/block/guard_master.json", model("guard_master_front", True, "static"))
    wj("models/block/guard_master_body.json", model("guard_master_front", True, "body"))
    wj("models/block/guard_master_body_ringing.json", model("guard_master_front_ringing", True, "body"))
    wj("models/block/guard_master_body_offhook.json", model("guard_master_front_talk", False, "body"))
    wj("models/block/guard_master_item.json", model("guard_master_front", True, "full"))
    leg = rm.model({"particle": f"{M}:block/guard_master_black", "black": f"{M}:block/guard_master_black"},
                   [box((3.5, 0, 0), (12.5, 16, 1.5), "#black")])
    wj("models/block/guard_master_leg.json", leg)
    wj("models/item/guard_master.json", {"parent": f"{M}:block/guard_master_item"})
    for old in ("guard_master_ringing", "guard_master_offhook"):
        pth = os.path.join(ROOT, f"models/block/{old}.json")
        if os.path.exists(pth):
            os.remove(pth)
    wj("blockstates/guard_master.json", states())
    print("guard_master: body top y", round(BODY[3], 3))


if __name__ == "__main__":
    main()
