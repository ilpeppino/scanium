***REMOVED***!/usr/bin/env python3
"""Generate all Scanium branding PNG assets from SVG sources."""

import os
import cairosvg

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOGO_DIR = os.path.join(BASE_DIR, "logo")
ICON_DIR = os.path.join(BASE_DIR, "icon")
RASTER_DIR = os.path.join(BASE_DIR, "raster")

***REMOVED*** General raster sizes
RASTER_SIZES = [1024, 512, 256, 128, 64, 32]

***REMOVED*** Android mipmap densities: density -> size in pixels
ANDROID_DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

***REMOVED*** iOS sizes
IOS_SIZES = [1024, 180, 167, 152, 120]

***REMOVED*** Logo variants to export
LOGO_VARIANTS = [
    "scanium-logo-primary",
    "scanium-logo-negative",
    "scanium-logo-mono-dark",
    "scanium-logo-mono-light",
    "scanium-icon-mark",
]


def ensure_dir(path):
    """Create directory if it doesn't exist."""
    os.makedirs(path, exist_ok=True)


def convert_svg_to_png(svg_path, png_path, size):
    """Convert SVG to PNG at specified size."""
    cairosvg.svg2png(
        url=svg_path,
        write_to=png_path,
        output_width=size,
        output_height=size,
    )
    print(f"  Generated: {png_path} ({size}x{size})")


def generate_raster_exports():
    """Generate general raster PNG exports."""
    print("\n=== Generating Raster Exports ===")

    for variant in LOGO_VARIANTS:
        svg_path = os.path.join(LOGO_DIR, f"{variant}.svg")
        if not os.path.exists(svg_path):
            print(f"  Warning: {svg_path} not found, skipping...")
            continue

        variant_dir = os.path.join(RASTER_DIR, variant)
        ensure_dir(variant_dir)

        for size in RASTER_SIZES:
            png_path = os.path.join(variant_dir, f"{variant}-{size}.png")
            convert_svg_to_png(svg_path, png_path, size)


def generate_android_assets():
    """Generate Android adaptive icon assets."""
    print("\n=== Generating Android Assets ===")

    android_dir = os.path.join(ICON_DIR, "android")
    ensure_dir(android_dir)

    ***REMOVED*** Use the icon mark as the source for app icon
    icon_svg = os.path.join(LOGO_DIR, "scanium-icon-mark.svg")

    ***REMOVED*** Generate Play Store icon (512x512)
    play_store_path = os.path.join(android_dir, "play-store-512.png")
    convert_svg_to_png(icon_svg, play_store_path, 512)

    ***REMOVED*** Generate mipmap densities
    for density, size in ANDROID_DENSITIES.items():
        density_dir = os.path.join(android_dir, f"mipmap-{density}")
        ensure_dir(density_dir)

        ***REMOVED*** Full icon (for legacy launcher icons)
        icon_path = os.path.join(density_dir, "ic_launcher.png")
        convert_svg_to_png(icon_svg, icon_path, size)

        ***REMOVED*** Round icon variant
        round_path = os.path.join(density_dir, "ic_launcher_round.png")
        convert_svg_to_png(icon_svg, round_path, size)


def generate_ios_assets():
    """Generate iOS app icon assets."""
    print("\n=== Generating iOS Assets ===")

    ios_dir = os.path.join(ICON_DIR, "ios", "AppIcon.appiconset")
    ensure_dir(ios_dir)

    ***REMOVED*** Use the icon mark as the source
    icon_svg = os.path.join(LOGO_DIR, "scanium-icon-mark.svg")

    ***REMOVED*** Generate all iOS sizes
    for size in IOS_SIZES:
        icon_path = os.path.join(ios_dir, f"icon-{size}.png")
        convert_svg_to_png(icon_svg, icon_path, size)

    ***REMOVED*** Generate Contents.json for Xcode
    contents_json = """{
  "images" : [
    {
      "filename" : "icon-120.png",
      "idiom" : "iphone",
      "scale" : "2x",
      "size" : "60x60"
    },
    {
      "filename" : "icon-180.png",
      "idiom" : "iphone",
      "scale" : "3x",
      "size" : "60x60"
    },
    {
      "filename" : "icon-152.png",
      "idiom" : "ipad",
      "scale" : "2x",
      "size" : "76x76"
    },
    {
      "filename" : "icon-167.png",
      "idiom" : "ipad",
      "scale" : "2x",
      "size" : "83.5x83.5"
    },
    {
      "filename" : "icon-1024.png",
      "idiom" : "ios-marketing",
      "scale" : "1x",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
"""
    contents_path = os.path.join(ios_dir, "Contents.json")
    with open(contents_path, "w") as f:
        f.write(contents_json)
    print(f"  Generated: {contents_path}")


def main():
    """Generate all branding assets."""
    print("Scanium Branding Asset Generator")
    print("=" * 40)

    ***REMOVED*** Ensure output directories exist
    ensure_dir(RASTER_DIR)
    ensure_dir(os.path.join(ICON_DIR, "android"))
    ensure_dir(os.path.join(ICON_DIR, "ios"))

    ***REMOVED*** Generate all assets
    generate_raster_exports()
    generate_android_assets()
    generate_ios_assets()

    print("\n" + "=" * 40)
    print("Asset generation complete!")


if __name__ == "__main__":
    main()
