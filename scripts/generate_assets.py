import os
import cairosvg

***REMOVED*** Configuration
SIZE = 1024
COLORS = {
    "blue": "***REMOVED***1F4BFF",
    "yellow": "***REMOVED***FFD400",
    "white": "***REMOVED***FFFFFF",
    "slate": "***REMOVED***1E293B",
    "black": "***REMOVED***000000",
    "transparent": "none"
}

OUTPUT_DIR = "assets/design"
EXPORT_DIR = "assets/exports"

def get_s_path():
    ***REMOVED*** S Design: Lightning S
    return "M 720 220 L 320 220 L 600 512 L 424 512 L 704 804 L 304 804 L 304 704 L 528 704 L 352 512 L 528 512 L 320 320 L 720 320 Z"

def get_frame_element(color, stroke_width=60):
    return f'<rect x="112" y="112" width="800" height="800" rx="160" ry="160" fill="none" stroke="{color}" stroke-width="{stroke_width}" />'

def generate_svg(filename, content):
    with open(f"{OUTPUT_DIR}/{filename}", "w") as f:
        f.write(content)
    print(f"Generated {filename}")
    return f"{OUTPUT_DIR}/{filename}"

def main():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)
    if not os.path.exists(EXPORT_DIR):
        os.makedirs(EXPORT_DIR)

    s_path = get_s_path()

    ***REMOVED*** 1. Logo Primary (Blue BG, White Frame, Yellow S)
    logo_primary = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        <rect x="0" y="0" width="{SIZE}" height="{SIZE}" fill="{COLORS["blue"]}"/>
        {get_frame_element(COLORS["white"])}
        <path d="{s_path}" fill="{COLORS["yellow"]}"/>
    </svg>'''
    generate_svg("logo_primary.svg", logo_primary)

    ***REMOVED*** 2. Icon Background (Solid Blue)
    icon_bg = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        <rect width="{SIZE}" height="{SIZE}" fill="{COLORS["blue"]}"/>
    </svg>'''
    generate_svg("icon_background.svg", icon_bg)

    ***REMOVED*** 3. Icon Foreground (Scaled, Transparent BG)
    scale = 0.65
    offset = (SIZE - SIZE * scale) / 2
    icon_fg = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        <g transform="translate({offset}, {offset}) scale({scale})">
            {get_frame_element(COLORS["white"])}
            <path d="{s_path}" fill="{COLORS["yellow"]}"/>
        </g>
    </svg>'''
    generate_svg("icon_foreground.svg", icon_fg)

    ***REMOVED*** 4. Icon Monochrome (For Android 13+ Themed Icons)
    icon_mono = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        <g transform="translate({offset}, {offset}) scale({scale})">
            {get_frame_element(COLORS["white"]).replace(COLORS["white"], "***REMOVED***000000")}
            <path d="{s_path}" fill="***REMOVED***000000"/>
        </g>
    </svg>'''
    generate_svg("icon_monochrome.svg", icon_mono)

    ***REMOVED*** 5. Negative/Dark Mode Safe
    logo_negative = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        {get_frame_element(COLORS["white"])}
        <path d="{s_path}" fill="{COLORS["white"]}"/>
    </svg>'''
    generate_svg("logo_negative.svg", logo_negative)

    ***REMOVED*** Monochrome Logo
    logo_monochrome = f'''<svg width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}" xmlns="http://www.w3.org/2000/svg">
        {get_frame_element(COLORS["black"])}
        <path d="{s_path}" fill="{COLORS["black"]}"/>
    </svg>'''
    generate_svg("logo_monochrome.svg", logo_monochrome)

    ***REMOVED*** Export PNGs
    ***REMOVED*** Added 20 and 29
    sizes = [1024, 512, 192, 180, 167, 152, 120, 96, 87, 80, 76, 60, 58, 48, 40, 32, 29, 20]
    for size in sizes:
        cairosvg.svg2png(url=f"{OUTPUT_DIR}/logo_primary.svg", write_to=f"{EXPORT_DIR}/icon_{size}.png", output_width=size, output_height=size)

    print("Done.")

if __name__ == "__main__":
    main()
