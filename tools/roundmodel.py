"""
둥근 모서리 / 원형 부품을 블록 모델 요소로 만드는 도우미.

마인크래프트 모델은 상자(요소)만 쓸 수 있으므로
 - 둥근 사각형: 위아래로 계단처럼 잘게 나눈 상자들 + 정면 텍스처의 투명 모서리(cutout)
 - 원형 부품(렌즈, 버튼): 0° / 45° 로 돌린 상자 두 개(팔각 기둥) + 원 모양 텍스처
로 곡선을 표현한다. 정면은 모두 north(-z) 쪽, 벽은 z=16 쪽.
"""
import math


def r4(v):
    return round(v, 4)


def rounded_slabs(x1, y1, x2, y2, r, steps):
    """둥근 사각형을 가로 띠 상자들로 나눔 → [(sx1, sy1, sx2, sy2)]"""
    r = min(r, (x2 - x1) / 2, (y2 - y1) / 2)
    if r <= 0 or steps <= 0:
        return [(x1, y1, x2, y2)]
    h = r / steps
    out = []
    for i in range(steps):
        dy = r - (i + 0.5) * h
        inset = r - math.sqrt(max(0.0, r * r - dy * dy))
        out.append((x1 + inset, y2 - (i + 1) * h, x2 - inset, y2 - i * h))
        out.append((x1 + inset, y1 + i * h, x2 - inset, y1 + (i + 1) * h))
    out.append((x1, y1 + r, x2, y2 - r))
    return out


def front_uv(sx1, sy1, sx2, sy2, bbox):
    """정면(north) UV: 텍스처 한 장이 bbox 전체를 덮는다 (north 면은 왼쪽이 +x)"""
    bx1, by1, bx2, by2 = bbox
    w, h = bx2 - bx1, by2 - by1
    return [r4((bx2 - sx2) / w * 16), r4((by2 - sy2) / h * 16), r4((bx2 - sx1) / w * 16), r4((by2 - sy1) / h * 16)]


def rounded_box(rect, z1, z2, r, bbox, front="#front", side="#edge", steps=3):
    """둥근 사각 판. rect=(x1,y1,x2,y2), z1<z2 (z1 이 정면)"""
    els = []
    for sx1, sy1, sx2, sy2 in rounded_slabs(*rect, r, steps):
        if sx2 - sx1 <= 0.001 or sy2 - sy1 <= 0.001:
            continue
        full = {"uv": [0, 0, 16, 16], "texture": side}
        els.append({
            "from": [r4(sx1), r4(sy1), r4(z1)], "to": [r4(sx2), r4(sy2), r4(z2)],
            "faces": {"north": {"uv": front_uv(sx1, sy1, sx2, sy2, bbox), "texture": front},
                      "south": dict(full), "east": dict(full), "west": dict(full), "up": dict(full), "down": dict(full)}})
    return els


def round_part(cx, cy, d, z1, z2, front, side):
    """원형 부품 (렌즈 테두리, 버튼): 팔각 기둥 + 원 텍스처(투명 바깥)"""
    h = d / 2
    els = []
    for k, angle in enumerate((0, 45)):
        zf = z1 + (0.01 if angle else 0)
        el = {
            "from": [r4(cx - h), r4(cy - h), r4(zf)], "to": [r4(cx + h), r4(cy + h), r4(z2)],
            "faces": {"north": {"uv": [0, 0, 16, 16], "texture": front},
                      "east": {"uv": [0, 0, 16, 16], "texture": side}, "west": {"uv": [0, 0, 16, 16], "texture": side},
                      "up": {"uv": [0, 0, 16, 16], "texture": side}, "down": {"uv": [0, 0, 16, 16], "texture": side}}}
        if angle:
            el["rotation"] = {"origin": [r4(cx), r4(cy), r4(z2)], "axis": "z", "angle": angle}
        els.append(el)
    return els


DISPLAY_PANEL = {
    "gui": {"rotation": [0, 180, 0], "scale": [1.5, 1.5, 1.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1.5, 1.5, 1.5]},
    "ground": {"translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [0.8, 0.8, 0.8]},
}


def model(textures, elements, display=None):
    return {"parent": "block/block", "render_type": "minecraft:cutout", "textures": textures,
            "elements": elements, "display": display or DISPLAY_PANEL}
