import os
import re

***REMOVED*** Configuration matches generate_assets.py
SIZE = 1024
COLORS = {
    "blue": "***REMOVED***1F4BFF",
    "yellow": "***REMOVED***FFD400",
    "white": "***REMOVED***FFFFFF",
    "black": "***REMOVED***000000"
}

def get_s_path():
    return "M 720 220 L 320 220 L 600 512 L 424 512 L 704 804 L 304 804 L 304 704 L 528 704 L 352 512 L 528 512 L 320 320 L 720 320 Z"

def get_rounded_rect_path(x, y, w, h, r):
    return f"M {x+r} {y} H {x+w-r} A {r} {r} 0 0 1 {x+w} {y+r} V {y+h-r} A {r} {r} 0 0 1 {x+w-r} {y+h} H {x+r} A {r} {r} 0 0 1 {x} {y+h-r} V {y+r} A {r} {r} 0 0 1 {x+r} {y} Z"

def generate_vector_xml(filename, fill_color, stroke_color, is_monochrome=False):
    s_path = get_s_path()
    rect_path = get_rounded_rect_path(112, 112, 800, 800, 160)

    scale = 0.65
    offset = (SIZE - SIZE * scale) / 2

    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{SIZE}"
    android:viewportHeight="{SIZE}">
    <group
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{offset}"
        android:translateY="{offset}">
        <path
            android:pathData="{rect_path}"
            android:strokeColor="{stroke_color}"
            android:strokeWidth="60"
            android:fillColor="***REMOVED***00000000"/>
        <path
            android:pathData="{s_path}"
            android:fillColor="{fill_color}"/>
    </group>
</vector>'''

    with open(f"androidApp/src/main/res/drawable/{filename}", "w") as f:
        f.write(xml)
    print(f"Generated {filename}")

def update_colors_xml():
    filepath = "androidApp/src/main/res/values/colors.xml"
    with open(filepath, "r") as f:
        content = f.read()

    ***REMOVED*** Update scanium_blue
    content = re.sub(r'<color name="scanium_blue">.*?</color>', f'<color name="scanium_blue">{COLORS["blue"]}</color>', content)

    ***REMOVED*** Update ic_launcher_background
    content = re.sub(r'<color name="ic_launcher_background">.*?</color>', f'<color name="ic_launcher_background">{COLORS["blue"]}</color>', content)

    ***REMOVED*** Add scanium_yellow if missing
    if 'name="scanium_yellow"' not in content:
         content = content.replace('</resources>', f'    <color name="scanium_yellow">{COLORS["yellow"]}</color>\n</resources>')

    with open(filepath, "w") as f:
        f.write(content)
    print("Updated colors.xml")

def update_mipmap_xml(filename):
    filepath = f"androidApp/src/main/res/mipmap-anydpi-v26/{filename}"
    if not os.path.exists(filepath):
        print(f"Skipping {filepath}, file not found")
        return

    with open(filepath, "r") as f:
        content = f.read()

    if "monochrome" not in content:
        ***REMOVED*** Add monochrome before </adaptive-icon>
        content = content.replace('</adaptive-icon>', '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>')
        with open(filepath, "w") as f:
            f.write(content)
        print(f"Updated {filename} with monochrome reference")
    else:
        print(f"{filename} already has monochrome reference")

def main():
    generate_vector_xml("ic_launcher_foreground.xml", COLORS["yellow"], COLORS["white"])
    ***REMOVED*** Monochrome: use white. Android tints it.
    generate_vector_xml("ic_launcher_monochrome.xml", "***REMOVED***FFFFFFFF", "***REMOVED***FFFFFFFF", is_monochrome=True)

    update_colors_xml()
    update_mipmap_xml("ic_launcher.xml")
    update_mipmap_xml("ic_launcher_round.xml")

if __name__ == "__main__":
    main()
