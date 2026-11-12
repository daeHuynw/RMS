#!/usr/bin/env python3
"""Vẽ lại PNG use case UML cho RMS (khớp docs/useCaseDiagram.md). Chạy: python3 render_uc.py"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
BG = (255, 255, 255)
INK = (30, 30, 30)
MUTED = (90, 90, 90)

COLORS = {
    "Guest": (110, 110, 110),
    "Customer": (30, 100, 200),
    "Staff": (200, 110, 30),
    "Kitchen": (190, 40, 40),
    "Admin": (120, 50, 170),
}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


F_TITLE = font(28, True)
F_ACTOR = font(16, True)
F_UC = font(14)
F_SYS = font(18, True)
F_NOTE = font(12)


def text_size(draw: ImageDraw.ImageDraw, text: str, fnt) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def draw_actor(draw: ImageDraw.ImageDraw, x: int, y: int, name: str, color: tuple[int, int, int]) -> tuple[int, int]:
    """Stick figure; return connection point (right of torso)."""
    head_r = 14
    draw.ellipse((x - head_r, y - head_r, x + head_r, y + head_r), outline=color, width=3)
    body_top = y + head_r
    body_bot = y + 58
    draw.line((x, body_top, x, body_bot), fill=color, width=3)
    draw.line((x - 22, y + 28, x + 22, y + 28), fill=color, width=3)
    draw.line((x, body_bot, x - 18, body_bot + 28), fill=color, width=3)
    draw.line((x, body_bot, x + 18, body_bot + 28), fill=color, width=3)
    tw, th = text_size(draw, name, F_ACTOR)
    draw.text((x - tw // 2, body_bot + 34), name, fill=color, font=F_ACTOR)
    return x + 28, y + 28


def draw_uc(
    draw: ImageDraw.ImageDraw,
    cx: int,
    cy: int,
    label: str,
    color: tuple[int, int, int],
) -> tuple[int, int, int, int]:
    tw, th = text_size(draw, label, F_UC)
    pad_x, pad_y = 22, 14
    w = max(tw + pad_x * 2, 140)
    h = max(th + pad_y * 2, 40)
    left, top = cx - w // 2, cy - h // 2
    draw.ellipse((left, top, left + w, top + h), outline=color, width=2)
    draw.text((cx - tw // 2, cy - th // 2), label, fill=INK, font=F_UC)
    return left, top, left + w, top + h


def connect(draw: ImageDraw.ImageDraw, a: tuple[int, int], box: tuple[int, int, int, int], color: tuple[int, int, int]) -> None:
    bx = box[0]
    by = (box[1] + box[3]) // 2
    draw.line((a[0], a[1], bx, by), fill=color, width=2)


def dashed(draw: ImageDraw.ImageDraw, p1: tuple[int, int], p2: tuple[int, int], color: tuple[int, int, int], label: str = "") -> None:
    x1, y1 = p1
    x2, y2 = p2
    steps = 16
    for i in range(steps):
        if i % 2:
            continue
        t0, t1 = i / steps, (i + 1) / steps
        draw.line(
            (int(x1 + (x2 - x1) * t0), int(y1 + (y2 - y1) * t0), int(x1 + (x2 - x1) * t1), int(y1 + (y2 - y1) * t1)),
            fill=color,
            width=2,
        )
    if label:
        mx, my = (x1 + x2) // 2, (y1 + y2) // 2 - 10
        draw.text((mx, my), label, fill=MUTED, font=F_NOTE)


def system_box(draw: ImageDraw.ImageDraw, left: int, top: int, right: int, bottom: int, title: str = "RMS") -> None:
    draw.rectangle((left, top, right, bottom), outline=INK, width=2)
    tw, th = text_size(draw, title, F_SYS)
    draw.rectangle((left + 12, top - th // 2 - 4, left + 24 + tw, top + th // 2 + 4), fill=BG, outline=INK, width=1)
    draw.text((left + 18, top - th // 2), title, fill=INK, font=F_SYS)


def save(img: Image.Image, name: str) -> None:
    path = OUT / name
    img.save(path, "PNG")
    print("wrote", path)


def render_overview() -> None:
    w, h = 1280, 820
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw.text((40, 24), "RMS — Use case overview (no guest QR order)", fill=INK, font=F_TITLE)

    actors = [
        ("Guest", 70, 140),
        ("Customer", 70, 280),
        ("Staff", 70, 420),
        ("Kitchen", 70, 560),
        ("Admin", 70, 700),
    ]
    pts = {}
    for name, x, y in actors:
        pts[name] = draw_actor(draw, x, y, name, COLORS[name])

    system_box(draw, 220, 90, 1240, 780)

    # columns of use cases
    guest_ucs = [("Xem thuc don", 380, 150)]
    cust_ucs = [
        ("Dang ky / Dang nhap", 380, 230),
        ("Dat ban", 560, 230),
        ("Gio hang / Dat mon sau check-in", 780, 230),
        ("Theo doi don", 1000, 230),
        ("Hoa don PDF", 560, 310),
        ("Ho so", 780, 310),
    ]
    staff_ucs = [
        ("So do ban (timer, khu vuc)", 420, 420),
        ("Mo / dong phien", 680, 420),
        ("QL dat ban + check-in", 940, 420),
        ("Tao / chinh don walk-in", 420, 500),
        ("Thanh toan", 680, 500),
        ("Xem hang doi bep", 940, 500),
    ]
    kit_ucs = [
        ("Hang doi bep realtime", 520, 600),
        ("Cap nhat trang thai mon", 820, 600),
    ]
    admin_ucs = [
        ("QL users / roles", 360, 720),
        ("QL menu / danh muc", 560, 720),
        ("QL ban / giam gia", 780, 720),
        ("Dashboard + bieu do", 980, 720),
        ("Settings / Audit", 1160, 720),
    ]

    boxes = {}
    for label, x, y in guest_ucs + cust_ucs + staff_ucs + kit_ucs + admin_ucs:
        color = (
            COLORS["Guest"]
            if (label, x, y) in guest_ucs
            else COLORS["Customer"]
            if (label, x, y) in cust_ucs
            else COLORS["Staff"]
            if (label, x, y) in staff_ucs
            else COLORS["Kitchen"]
            if (label, x, y) in kit_ucs
            else COLORS["Admin"]
        )
        boxes[label] = draw_uc(draw, x, y, label, color)

    connect(draw, pts["Guest"], boxes["Xem thuc don"], COLORS["Guest"])
    connect(draw, pts["Guest"], boxes["Dang ky / Dang nhap"], COLORS["Guest"])
    connect(draw, pts["Customer"], boxes["Dang ky / Dang nhap"], COLORS["Customer"])
    for lab in ("Dat ban", "Gio hang / Dat mon sau check-in", "Theo doi don", "Hoa don PDF", "Ho so", "Xem thuc don"):
        connect(draw, pts["Customer"], boxes[lab], COLORS["Customer"])
    for lab in (
        "So do ban (timer, khu vuc)",
        "Mo / dong phien",
        "QL dat ban + check-in",
        "Tao / chinh don walk-in",
        "Thanh toan",
        "Xem hang doi bep",
    ):
        connect(draw, pts["Staff"], boxes[lab], COLORS["Staff"])
    for lab in ("Hang doi bep realtime", "Cap nhat trang thai mon"):
        connect(draw, pts["Kitchen"], boxes[lab], COLORS["Kitchen"])
    for lab in ("QL users / roles", "QL menu / danh muc", "QL ban / giam gia", "Dashboard + bieu do", "Settings / Audit"):
        connect(draw, pts["Admin"], boxes[lab], COLORS["Admin"])

    draw.text((240, 100), "Khach goi mon sau staff check-in — khong QR guest", fill=MUTED, font=F_NOTE)
    save(img, "uc-overview.png")


def render_customer() -> None:
    w, h = 1152, 864
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw.text((36, 24), "Guest & Customer — RMS", fill=INK, font=F_TITLE)

    g = draw_actor(draw, 90, 220, "Guest", COLORS["Guest"])
    c = draw_actor(draw, 90, 520, "Customer", COLORS["Customer"])
    system_box(draw, 200, 80, 1100, 800)

    items = [
        ("Xem thuc don cong khai", 420, 160, "Guest"),
        ("Dang ky tai khoan", 700, 160, "Guest"),
        ("Dang nhap / refresh / logout", 420, 260, "Customer"),
        ("Xem / sua ho so", 700, 260, "Customer"),
        ("Dat ban", 420, 360, "Customer"),
        ("Xem / huy dat ban cua minh", 700, 360, "Customer"),
        ("Them gio hang", 420, 470, "Customer"),
        ("Dat mon tu gio (sau check-in)", 700, 470, "Customer"),
        ("Dat ngay 1 mon (sau check-in)", 420, 580, "Customer"),
        ("Theo doi don cua minh", 700, 580, "Customer"),
        ("Xem / tai hoa don PDF", 560, 690, "Customer"),
    ]
    boxes = {}
    for label, x, y, who in items:
        boxes[label] = draw_uc(draw, x, y, label, COLORS[who])

    connect(draw, g, boxes["Xem thuc don cong khai"], COLORS["Guest"])
    connect(draw, g, boxes["Dang ky tai khoan"], COLORS["Guest"])
    for lab in items:
        if lab[3] == "Customer" or lab[0] == "Xem thuc don cong khai":
            connect(draw, c, boxes[lab[0]], COLORS["Customer"])

    # include note
    dashed(
        draw,
        ((boxes["Dat mon tu gio (sau check-in)"][0] + boxes["Dat mon tu gio (sau check-in)"][2]) // 2, boxes["Dat mon tu gio (sau check-in)"][3]),
        ((boxes["Dat ban"][0] + boxes["Dat ban"][2]) // 2, boxes["Dat ban"][3]),
        COLORS["Customer"],
        "<<include>> CHECKED_IN",
    )
    draw.text((220, 100), "Khong co use case Goi mon qua QR cho khach", fill=MUTED, font=F_NOTE)
    save(img, "uc-customer.png")


def render_staff() -> None:
    w, h = 1152, 864
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw.text((36, 24), "Staff — RMS", fill=INK, font=F_TITLE)

    s = draw_actor(draw, 90, 400, "Staff", COLORS["Staff"])
    system_box(draw, 200, 80, 1100, 800)

    items = [
        ("So do ban (mau + timer + loc khu vuc)", 560, 150),
        ("Mo / dong phien ban (walk-in)", 560, 250),
        ("QL dat ban (confirm / reject / check-in)", 560, 350),
        ("Tao / chinh don walk-in", 560, 450),
        ("Ghi nhan thanh toan + PDF", 560, 550),
        ("Xem hang doi bep (tuy quyen)", 560, 650),
        ("Check-in mo table_session OPEN", 560, 760),
    ]
    boxes = {}
    for label, x, y in items:
        boxes[label] = draw_uc(draw, x, y, label, COLORS["Staff"])
        if label != "Check-in mo table_session OPEN":
            connect(draw, s, boxes[label], COLORS["Staff"])

    dashed(
        draw,
        (
            (boxes["QL dat ban (confirm / reject / check-in)"][0] + boxes["QL dat ban (confirm / reject / check-in)"][2]) // 2,
            boxes["QL dat ban (confirm / reject / check-in)"][3],
        ),
        (
            (boxes["Check-in mo table_session OPEN"][0] + boxes["Check-in mo table_session OPEN"][2]) // 2,
            boxes["Check-in mo table_session OPEN"][1],
        ),
        COLORS["Staff"],
        "<<include>>",
    )
    dashed(
        draw,
        (
            (boxes["Tao / chinh don walk-in"][0] + boxes["Tao / chinh don walk-in"][2]) // 2,
            boxes["Tao / chinh don walk-in"][3],
        ),
        (
            boxes["Check-in mo table_session OPEN"][0],
            (boxes["Check-in mo table_session OPEN"][1] + boxes["Check-in mo table_session OPEN"][3]) // 2,
        ),
        COLORS["Staff"],
        "<<include>> phien OPEN",
    )
    save(img, "uc-staff.png")


def render_kitchen() -> None:
    w, h = 1000, 420
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw.text((28, 18), "Kitchen — RMS", fill=INK, font=F_TITLE)

    k = draw_actor(draw, 80, 200, "Kitchen", COLORS["Kitchen"])
    system_box(draw, 180, 70, 960, 380)
    items = [
        ("Xem hang doi bep realtime (STOMP)", 580, 140),
        ("Xem chi tiet / ghi chu mon", 580, 230),
        ("Cap nhat trang thai mon (PENDING→READY)", 580, 320),
    ]
    for label, x, y in items:
        box = draw_uc(draw, x, y, label, COLORS["Kitchen"])
        connect(draw, k, box, COLORS["Kitchen"])
    save(img, "uc-kitchen.png")


def render_admin() -> None:
    w, h = 1152, 864
    img = Image.new("RGB", (w, h), BG)
    draw = ImageDraw.Draw(img)
    draw.text((36, 24), "Admin — RMS", fill=INK, font=F_TITLE)

    a = draw_actor(draw, 90, 420, "Admin", COLORS["Admin"])
    system_box(draw, 200, 80, 1100, 800)

    items = [
        ("QL nguoi dung + vai (STAFF/KITCHEN)", 560, 140),
        ("QL danh muc mon", 560, 230),
        ("QL mon + anh", 560, 320),
        ("QL ban (CRUD; QR schema giu, khong dung cho khach)", 560, 410),
        ("QL ma giam gia", 560, 500),
        ("Cai dat nha hang (thue, thoi luong dat ban)", 560, 590),
        ("Dashboard (overview + bieu do DT / peak)", 560, 680),
        ("Xem audit log", 560, 770),
    ]
    for label, x, y in items:
        box = draw_uc(draw, x, y, label, COLORS["Admin"])
        connect(draw, a, box, COLORS["Admin"])
    draw.text((220, 100), "Ngoai scope: CRM VIP/diem, HDDT nghi dinh, kho NL", fill=MUTED, font=F_NOTE)
    save(img, "uc-admin.png")


if __name__ == "__main__":
    render_overview()
    render_customer()
    render_staff()
    render_kitchen()
    render_admin()
