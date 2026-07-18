"""
Generates CRS Scheduler's launcher icon: a plain, universally-legible
calendar/schedule glyph. Renders adaptive icon foreground/background layers
plus legacy flattened + round fallbacks, at every mipmap density, and a
monochrome silhouette for Android 13+ themed icon support.
"""
from PIL import Image, ImageDraw
import math
import os

BASE = "app/src/main/res"

# name -> (legacy px, adaptive-layer px)
DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

SS = 4  # supersample factor for anti-aliasing

BG_COLOR = (0x1B, 0x11, 0x14, 255)      # deep maroon-black, fixed regardless of day/night
FG_COLOR = (0xF6, 0xF6, 0xF3, 255)      # off-white glyph
ACCENT_COLOR = (0xC4, 0x44, 0x4A, 255)  # maroon "today" dot


def draw_calendar_glyph(draw, cx, cy, scale, color, accent=None):
    """Draws a simple calendar/schedule pictograph centered at (cx, cy).
    `scale` is roughly the glyph's half-width in px."""
    w = scale * 1.65
    h = scale * 1.55
    left = cx - w / 2
    top = cy - h / 2 + scale * 0.12
    right = cx + w / 2
    bottom = cy + h / 2 + scale * 0.12

    body_stroke = max(2, int(scale * 0.16))

    # main body (rounded rect), stroked only -- reads clean at small sizes
    radius = scale * 0.34
    draw.rounded_rectangle([left, top, right, bottom], radius=radius,
                            outline=color, width=body_stroke)

    # header bar dividing the "title" row from the grid
    header_y = top + h * 0.32
    draw.line([left + body_stroke * 0.5, header_y, right - body_stroke * 0.5, header_y],
               fill=color, width=body_stroke)

    # two binder tabs on top
    tab_w = scale * 0.10
    tab_h = scale * 0.30
    tab_top = top - tab_h * 0.55
    for tab_cx in (left + w * 0.28, right - w * 0.28):
        draw.rounded_rectangle(
            [tab_cx - tab_w / 2, tab_top, tab_cx + tab_w / 2, top + tab_h * 0.45],
            radius=tab_w / 2, fill=color)

    # a light grid of "day" marks below the header
    rows, cols = 2, 3
    grid_top = header_y + (bottom - header_y) * 0.22
    grid_bottom = bottom - (bottom - header_y) * 0.18
    grid_left = left + w * 0.16
    grid_right = right - w * 0.16
    dot_r = scale * 0.075
    for r in range(rows):
        for c in range(cols):
            fx = grid_left + (grid_right - grid_left) * (c + 0.5) / cols
            fy = grid_top + (grid_bottom - grid_top) * (r + 0.5) / rows
            is_today = accent is not None and r == rows - 1 and c == cols - 1
            fill = accent if is_today else color
            rr = dot_r * (1.35 if is_today else 1.0)
            draw.ellipse([fx - rr, fy - rr, fx + rr, fy + rr], fill=fill)


def render_layer(px, mode):
    """mode: 'background' | 'foreground' | 'monochrome'"""
    hi = px * SS
    img = Image.new("RGBA", (hi, hi), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = cy = hi / 2

    if mode == "background":
        draw.rectangle([0, 0, hi, hi], fill=BG_COLOR)
    elif mode == "foreground":
        # glyph sized to sit safely within the adaptive icon's keep-out zone
        glyph_scale = hi * 0.145
        draw_calendar_glyph(draw, cx, cy, glyph_scale, FG_COLOR, accent=ACCENT_COLOR)
    elif mode == "monochrome":
        # Android 13+ themed icon layer: OS supplies its own tint at runtime,
        # only the alpha/shape matters. Solid white glyph on transparent.
        glyph_scale = hi * 0.145
        draw_calendar_glyph(draw, cx, cy, glyph_scale, (255, 255, 255, 255), accent=(255, 255, 255, 255))

    return img.resize((px, px), Image.LANCZOS)


def flatten_legacy(bg_layer, fg_layer, px, round_mask):
    """Composite background+foreground into a single legacy icon, optionally
    circle-masked for the _round variant."""
    composed = Image.alpha_composite(bg_layer.convert("RGBA"), fg_layer.convert("RGBA"))
    if round_mask:
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        out.paste(composed, (0, 0), mask)
        return out
    else:
        # legacy square icon: Android expects a filled square (no alpha
        # corners) with slightly rounded corners baked in visually
        mask = Image.new("L", (px, px), 0)
        r = px * 0.18
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, px - 1, px - 1], radius=r, fill=255)
        out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        out.paste(composed, (0, 0), mask)
        return out


for density, (legacy_px, adaptive_px) in DENSITIES.items():
    folder = os.path.join(BASE, f"mipmap-{density}")
    os.makedirs(folder, exist_ok=True)

    bg_adaptive = render_layer(adaptive_px, "background")
    fg_adaptive = render_layer(adaptive_px, "foreground")
    bg_adaptive.save(os.path.join(folder, "ic_launcher_background.png"))
    fg_adaptive.save(os.path.join(folder, "ic_launcher_foreground.png"))

    # legacy flattened icons, rendered fresh at legacy_px (not just downscaled
    # adaptive layers) so proportions stay correct at the smaller canvas
    bg_legacy = render_layer(legacy_px, "background")
    fg_legacy = render_layer(legacy_px, "foreground")
    flatten_legacy(bg_legacy, fg_legacy, legacy_px, round_mask=False).save(
        os.path.join(folder, "ic_launcher.png"))
    flatten_legacy(bg_legacy, fg_legacy, legacy_px, round_mask=True).save(
        os.path.join(folder, "ic_launcher_round.png"))

# monochrome themed-icon layer: single vector-friendly, density-independent
# master PNG referenced by anydpi-v26 XML at a generous fixed size
mono_dir = os.path.join(BASE, "mipmap-xxxhdpi")
render_layer(432, "monochrome").save(os.path.join(mono_dir, "ic_launcher_monochrome.png"))
for density, (_, adaptive_px) in DENSITIES.items():
    folder = os.path.join(BASE, f"mipmap-{density}")
    render_layer(adaptive_px, "monochrome").save(os.path.join(folder, "ic_launcher_monochrome.png"))

print("Icon generation complete.")
