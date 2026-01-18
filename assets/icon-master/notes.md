***REMOVED*** Scanium Android Icon Assets - Integration Guide

***REMOVED******REMOVED*** Overview

This document describes the generated Android icon assets for Scanium, derived from the camera lens logo design. The assets follow Android Material Design guidelines and support both legacy and adaptive icon formats.

***REMOVED******REMOVED*** Asset Composition

***REMOVED******REMOVED******REMOVED*** Master Files (Icon Sources)

Located in `assets/icon-master/`:

- **scanium_mark_master.svg** — Full-color vector mark with gradients, chrome effects, and lens highlights. Source for all icon variants.
- **scanium_mark_monochrome.svg** — Simplified single-color (black) vector for Android 13+ themed icons and notification icons.
- **scanium_foreground.svg** — Centered foreground layer with safe-zone padding (66% canvas). Used for adaptive icon generation.
- **scanium_background.svg** — Subtle blue gradient background for adaptive icons (matches brand primary color ***REMOVED***0052cc).

***REMOVED******REMOVED******REMOVED*** Design Decisions

1. **Lens Elements**: Two stacked circular camera lenses (upper and lower) with concentric rings, metallic chrome highlights, and colored glass effect (green-to-purple gradient).
2. **Curved S**: Blue flowing S-shape connecting the two lenses, representing the "Scanium" brand identity.
3. **Simplification**: For small sizes (24-48dp), lens rings and gradients are simplified to maintain clarity without visual noise.
4. **Color Palette**:
   - Primary Blue: `***REMOVED***0052cc` (background)
   - Lens Core: `***REMOVED***6400FF` (purple-blue)
   - Curved S: `***REMOVED***0066FF` to `***REMOVED***00ccff` (blue gradient)
   - Chrome Rings: `***REMOVED***e8e8e8` to `***REMOVED***ffffff` (metallic white)
   - Monochrome: `***REMOVED***000000` (pure black for themed icons)

***REMOVED******REMOVED*** Generated Assets

***REMOVED******REMOVED******REMOVED*** 1. Adaptive Launcher Icons (Android 8.0+)

**Directory**: `androidApp/src/main/res/mipmap-anydpi-v26/`

- **ic_launcher.xml** — References foreground, background, and monochrome drawable layers.
- **ic_launcher_round.xml** — Same as above; Android will apply masking based on device shape.

**Drawable Layers**:

- `drawable/ic_launcher_background.xml` — Solid blue background (***REMOVED***0052cc).
- `drawable/ic_launcher_foreground.xml` — Full-color lens + S mark (VectorDrawable).
- `drawable/ic_launcher_monochrome.xml** — Single-color variant for Android 13+ themed icon support.

***REMOVED******REMOVED******REMOVED*** 2. Legacy Launcher Icons (Pre-Oreo)

**Raster PNG files** at density-specific directories:

| Density | Size | Path |
|---------|------|------|
| mdpi | 48×48 | `mipmap-mdpi/ic_launcher.png` |
| hdpi | 72×72 | `mipmap-hdpi/ic_launcher.png` |
| xhdpi | 96×96 | `mipmap-xhdpi/ic_launcher.png` |
| xxhdpi | 144×144 | `mipmap-xxhdpi/ic_launcher.png` |
| xxxhdpi | 192×192 | `mipmap-xxxhdpi/ic_launcher.png` |

**Round Icons** (same sizes):

| Density | Path |
|---------|------|
| mdpi | `mipmap-mdpi/ic_launcher_round.png` |
| hdpi | `mipmap-hdpi/ic_launcher_round.png` |
| xhdpi | `mipmap-xhdpi/ic_launcher_round.png` |
| xxhdpi | `mipmap-xxhdpi/ic_launcher_round.png` |
| xxxhdpi | `mipmap-xxxhdpi/ic_launcher_round.png` |

***REMOVED******REMOVED******REMOVED*** 3. Notification Small Icons

**Monochrome white icons** for status bar and notifications:

| Density | Size | Path |
|---------|------|------|
| mdpi | 24×24 | `drawable-mdpi/ic_notification.png` |
| hdpi | 36×36 | `drawable-hdpi/ic_notification.png` |
| xhdpi | 48×48 | `drawable-xhdpi/ic_notification.png` |
| xxhdpi | 72×72 | `drawable-xxhdpi/ic_notification.png` |
| xxxhdpi | 96×96 | `drawable-xxxhdpi/ic_notification.png` |

**Usage**: `android:icon="@drawable/ic_notification"` in `AndroidManifest.xml` for the notification service.

***REMOVED******REMOVED******REMOVED*** 4. Play Store Icon

**File**: `assets/android/ic_launcher_playstore.png`

- **Size**: 512×512 px
- **Format**: PNG (no transparency)
- **Design**: Full-color with safe padding (no corner radius; Google Play applies masking).

***REMOVED******REMOVED*** Integration Instructions

***REMOVED******REMOVED******REMOVED*** Step 1: Copy Files to Android Project

```bash
***REMOVED*** From repo root:
cp -r assets/android/res/* androidApp/src/main/res/
```

Directory mapping:
- `assets/android/res/mipmap-anydpi-v26/*` → `androidApp/src/main/res/mipmap-anydpi-v26/`
- `assets/android/res/mipmap-*/*` → `androidApp/src/main/res/mipmap-*/`
- `assets/android/res/drawable/*` → `androidApp/src/main/res/drawable/`

***REMOVED******REMOVED******REMOVED*** Step 2: Update AndroidManifest.xml

In `androidApp/src/main/AndroidManifest.xml`, ensure the `<application>` tag includes:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    ...>
```

***REMOVED******REMOVED******REMOVED*** Step 3: Update Notification Icon (if applicable)

In your notification code (e.g., `NotificationCompat.Builder`):

```kotlin
notificationBuilder
    .setSmallIcon(R.drawable.ic_notification)
    .setColor(0xFF0052CC)  // Brand color
    ...
```

***REMOVED******REMOVED******REMOVED*** Step 4: Verify Play Store Icon

Upload `ic_launcher_playstore.png` (512×512) to Google Play Console:
- Go to **Release** → **Edit release** → **App releases** → **Browse files** → **App icon**
- Verify preview shows correct aspect ratio (no cropping of lens marks).

***REMOVED******REMOVED*** Design Rationale & Simplifications

***REMOVED******REMOVED******REMOVED*** Why These Choices?

1. **Two Stacked Lenses**: Core brand identity from the source logo.
2. **Curved Blue S**: Connects the lenses and clearly indicates "Scanium."
3. **Chrome Rings**: Suggest camera lens optics while maintaining legibility at small sizes.
4. **Gradient Lens**: Green-to-purple gradient provides visual depth without complexity.

***REMOVED******REMOVED******REMOVED*** Small Size Optimization (24–48dp)

At very small sizes (mdpi notification icon = 24×24 px):
- Ring strokes remain visible (1–2 px minimum).
- Gradient simplified to solid fill to prevent banding.
- Highlight points removed (not visible at <32px).

***REMOVED******REMOVED******REMOVED*** Color & Contrast

- **Dark theme compatibility**: Icon background (***REMOVED***0052cc) works well on light status bars; notification icon (white) works on dark bars.
- **Light theme compatibility**: Same design visible on dark app drawer backgrounds.

***REMOVED******REMOVED******REMOVED*** Adaptive Icon Safe Zone

The adaptive icon foreground is padded to the 66% safe zone:
- **1024×1024 canvas**: ~341 px padding on each edge.
- Critical lens and S elements stay well within safe zone.
- Icon remains recognizable under squircle and rounded square masks.

***REMOVED******REMOVED*** Verification Checklist

- [ ] All PNG files are crisp and properly sized (use `identify` to verify dimensions).
- [ ] `ic_launcher.xml` references correct drawable resources.
- [ ] `ic_launcher_monochrome.xml` renders as single-color (no gradients visible in Android 13+ theme engine).
- [ ] Notification icon (`ic_notification.png`) is white on transparent background.
- [ ] Play Store icon (512×512) has no corner radius baked in.
- [ ] AndroidManifest.xml correctly references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- [ ] Build succeeds and app launcher icon displays correctly on emulator/device.
- [ ] Icon visible at 1.5x scale on hdpi device (72×72 px ≈ 48dp).
- [ ] Notification icon appears white in status bar (test with `adb shell`).
- [ ] Google Play Console preview shows full icon (no clipping).

***REMOVED******REMOVED*** File Manifest

```
assets/
├── icon-master/
│   ├── notes.md (this file)
│   ├── scanium_mark_master.svg (full-color master)
│   ├── scanium_mark_monochrome.svg (single-color variant)
│   ├── scanium_foreground.svg (adaptive foreground)
│   └── scanium_background.svg (adaptive background)
├── android/
│   ├── ic_launcher_playstore.png (512×512)
│   └── res/
│       ├── mipmap-anydpi-v26/
│       │   ├── ic_launcher.xml
│       │   └── ic_launcher_round.xml
│       ├── mipmap-mdpi/
│       │   ├── ic_launcher.png (48×48)
│       │   └── ic_launcher_round.png (48×48)
│       ├── mipmap-hdpi/
│       │   ├── ic_launcher.png (72×72)
│       │   └── ic_launcher_round.png (72×72)
│       ├── mipmap-xhdpi/
│       │   ├── ic_launcher.png (96×96)
│       │   └── ic_launcher_round.png (96×96)
│       ├── mipmap-xxhdpi/
│       │   ├── ic_launcher.png (144×144)
│       │   └── ic_launcher_round.png (144×144)
│       ├── mipmap-xxxhdpi/
│       │   ├── ic_launcher.png (192×192)
│       │   └── ic_launcher_round.png (192×192)
│       ├── drawable-mdpi/
│       │   └── ic_notification.png (24×24)
│       ├── drawable-hdpi/
│       │   └── ic_notification.png (36×36)
│       ├── drawable-xhdpi/
│       │   └── ic_notification.png (48×48)
│       ├── drawable-xxhdpi/
│       │   └── ic_notification.png (72×72)
│       ├── drawable-xxxhdpi/
│       │   └── ic_notification.png (96×96)
│       └── drawable/ (density-independent)
│           ├── ic_launcher_background.xml
│           ├── ic_launcher_foreground.xml
│           └── ic_launcher_monochrome.xml
```

***REMOVED******REMOVED*** Next Steps

1. Copy all files to `androidApp/src/main/res/`.
2. Update `AndroidManifest.xml` with icon and roundIcon references.
3. Build and test on device (test on both hdpi and xxxhdpi if possible).
4. Upload Play Store icon to Google Play Console.
5. Commit assets to repository with clear commit message.

***REMOVED******REMOVED*** Support

For issues or questions:
- Verify file paths match the directory structure above.
- Check AndroidManifest.xml permissions and icon attributes.
- Test on Android 8.0 (Oreo) minimum for adaptive icon support.
- For notification icon visibility, test on both light and dark system themes.

---

**Generated**: January 2026
**Source Logo**: Camera Lens + Curved S (Scanium brand mark)
**Target**: Android 8.0+ (API level 26+) with legacy support for pre-Oreo devices
