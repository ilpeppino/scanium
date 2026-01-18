***REMOVED***!/usr/bin/env python3
"""
Generate Android icon assets for Scanium.
Creates launcher, notification, and play store icons.
"""

import os
from pathlib import Path
from PIL import Image, ImageDraw
import math

def create_launcher_icon(width, height):
    """Create a launcher icon with lenses and curved S."""
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    ***REMOVED*** Background gradient (blue)
    bg = Image.new('RGB', (width, height), '***REMOVED***0052cc')
    img.paste(bg, (0, 0))

    ***REMOVED*** Calculate scaling
    center_x = width // 2
    center_y = height // 2
    lens_radius = width // 4.5
    y_offset = height // 6

    ***REMOVED*** Draw upper lens
    upper_y = center_y - lens_radius - y_offset
    _draw_lens(draw, center_x, upper_y, lens_radius, width, height)

    ***REMOVED*** Draw lower lens
    lower_y = center_y + lens_radius + y_offset
    _draw_lens(draw, center_x, lower_y, lens_radius, width, height)

    ***REMOVED*** Draw curved S between them (simplified)
    _draw_s_curve(draw, center_x, width, height)

    return img

def create_notification_icon(width, height):
    """Create a white monochrome notification icon."""
    img = Image.new('RGBA', (width, height), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    center_x = int(width // 2)
    center_y = int(height // 2)
    lens_radius = int(width // 4)

    ***REMOVED*** Upper lens
    upper_y = int(center_y - lens_radius - height // 8)
    lens_box_upper = [
        int(center_x - lens_radius),
        int(upper_y - lens_radius),
        int(center_x + lens_radius),
        int(upper_y + lens_radius)
    ]
    draw.ellipse(lens_box_upper, outline=(255, 255, 255, 255), width=max(1, width // 12))

    ***REMOVED*** Lower lens
    lower_y = int(center_y + lens_radius + height // 8)
    lens_box_lower = [
        int(center_x - lens_radius),
        int(lower_y - lens_radius),
        int(center_x + lens_radius),
        int(lower_y + lens_radius)
    ]
    draw.ellipse(lens_box_lower, outline=(255, 255, 255, 255), width=max(1, width // 12))

    ***REMOVED*** Curved S (simplified bezier)
    line_width = max(1, width // 10)
    points = [
        (int(center_x + lens_radius + width // 6), int(upper_y - width // 12)),
        (int(center_x + lens_radius + width // 4), int(upper_y + width // 8)),
        (int(center_x + lens_radius + width // 5), int(center_y)),
        (int(center_x + lens_radius + width // 4), int(lower_y - width // 8)),
        (int(center_x + lens_radius + width // 6), int(lower_y + width // 12)),
    ]
    if len(points) > 1:
        draw.line(points, fill=(255, 255, 255, 255), width=line_width)

    return img

def create_playstore_icon(width, height):
    """Create a 512x512 Play Store icon with full branding."""
    img = Image.new('RGB', (width, height), '***REMOVED***0052cc')
    draw = ImageDraw.Draw(img)

    center_x = width // 2
    center_y = height // 2
    lens_radius = width // 6
    y_offset = width // 5

    ***REMOVED*** Upper lens - solid with gradient effect
    upper_y = center_y - lens_radius - y_offset
    lens_box = [
        center_x - lens_radius,
        upper_y - lens_radius,
        center_x + lens_radius,
        upper_y + lens_radius
    ]
    ***REMOVED*** Draw filled lens with gradients (simplified)
    _draw_filled_lens(draw, center_x, upper_y, lens_radius, (78, 0, 255))

    ***REMOVED*** Lower lens
    lower_y = center_y + lens_radius + y_offset
    _draw_filled_lens(draw, center_x, lower_y, lens_radius, (120, 0, 255))

    ***REMOVED*** Curved S - thick and visible
    _draw_s_curve_thick(draw, center_x, center_y, width, lens_radius)

    return img

def _draw_lens(draw, x, y, radius, width, height):
    """Helper: Draw a single lens with rings."""
    x, y, radius = int(x), int(y), int(radius)
    ***REMOVED*** Outer chrome ring
    draw.ellipse([x - radius, y - radius, x + radius, y + radius],
                outline=(200, 200, 200), width=max(1, radius // 10))
    ***REMOVED*** Middle ring
    inner_r = int(radius * 0.85)
    draw.ellipse([x - inner_r, y - inner_r, x + inner_r, y + inner_r],
                outline=(100, 100, 100), width=max(1, radius // 15))
    ***REMOVED*** Inner colored circle
    inner_r2 = int(radius * 0.7)
    draw.ellipse([x - inner_r2, y - inner_r2, x + inner_r2, y + inner_r2],
                fill=(100, 0, 200), outline=(200, 0, 255), width=1)

def _draw_filled_lens(draw, x, y, radius, color):
    """Helper: Draw a filled lens for Play Store icon."""
    x, y, radius = int(x), int(y), int(radius)
    ***REMOVED*** Outer ring
    draw.ellipse([x - radius, y - radius, x + radius, y + radius],
                fill=color, outline=(80, 80, 80), width=2)
    ***REMOVED*** Inner highlight
    inner_r = int(radius * 0.5)
    draw.ellipse([int(x - inner_r * 0.6), int(y - inner_r * 0.6),
                 int(x + inner_r * 0.4), int(y + inner_r * 0.4)],
                fill=(200, 150, 255), outline=None)

def _draw_s_curve(draw, center_x, width, height):
    """Helper: Draw the curved S (simplified)."""
    center_y = height // 2
    curve_width = max(1, width // 20)
    offset = width // 6

    ***REMOVED*** Upper part of S
    upper_points = [
        (int(center_x + offset), int(height // 4)),
        (int(center_x + offset * 1.5), int(height // 3)),
        (int(center_x + offset), int(height / 2.5)),
    ]
    if len(upper_points) > 1:
        draw.line(upper_points, fill=(0, 100, 255), width=curve_width)

    ***REMOVED*** Lower part of S
    lower_points = [
        (int(center_x + offset), int(height / 2.3)),
        (int(center_x + offset * 1.5), int(height * 2 // 3)),
        (int(center_x + offset), int(height * 3 // 4)),
    ]
    if len(lower_points) > 1:
        draw.line(lower_points, fill=(0, 150, 255), width=curve_width)

def _draw_s_curve_thick(draw, center_x, center_y, width, lens_radius):
    """Helper: Draw thick S curve for Play Store icon."""
    curve_width = width // 16
    offset = int(lens_radius * 1.2)

    ***REMOVED*** Upper S
    upper_pts = [
        (int(center_x + offset), int(center_y - lens_radius * 1.8)),
        (int(center_x + offset * 1.3), int(center_y - lens_radius)),
        (int(center_x + offset), int(center_y - lens_radius * 0.2)),
    ]
    draw.line(upper_pts, fill=(0, 150, 255), width=curve_width)

    ***REMOVED*** Lower S
    lower_pts = [
        (int(center_x + offset), int(center_y + lens_radius * 0.2)),
        (int(center_x + offset * 1.3), int(center_y + lens_radius)),
        (int(center_x + offset), int(center_y + lens_radius * 1.8)),
    ]
    draw.line(lower_pts, fill=(0, 100, 255), width=curve_width)

def main():
    base_dir = Path("/home/user/scanium")
    android_dir = base_dir / "assets/android/res"

    ***REMOVED*** Launcher icons
    launcher_sizes = {
        "mdpi": (48, 48),
        "hdpi": (72, 72),
        "xhdpi": (96, 96),
        "xxhdpi": (144, 144),
        "xxxhdpi": (192, 192),
    }

    ***REMOVED*** Notification icons
    notification_sizes = {
        "mdpi": (24, 24),
        "hdpi": (36, 36),
        "xhdpi": (48, 48),
        "xxhdpi": (72, 72),
        "xxxhdpi": (96, 96),
    }

    print("Generating launcher icons...")
    for density, (w, h) in launcher_sizes.items():
        img = create_launcher_icon(w, h)
        for name in ["ic_launcher", "ic_launcher_round"]:
            path = android_dir / f"mipmap-{density}" / f"{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            img.save(path)
            print(f"  ✓ {path.relative_to(base_dir)}")

    print("Generating notification icons...")
    for density, (w, h) in notification_sizes.items():
        img = create_notification_icon(w, h)
        path = android_dir / f"drawable-{density}" / "ic_notification.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print(f"  ✓ {path.relative_to(base_dir)}")

    print("Generating Play Store icon...")
    img = create_playstore_icon(512, 512)
    path = base_dir / "assets/android/ic_launcher_playstore.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"  ✓ {path.relative_to(base_dir)}")

    print("\n✅ Icon generation complete!")

if __name__ == "__main__":
    main()
