---
name: coloros-clock-font-converter
description: Convert standard TTF/OTF fonts or SVGs into ColorOS 17 compliant Fixed or Flex digital clock fonts with UPM 1000, precise vertical metrics, and variable weight/height axes without topology drift.
compatibility: opencode, antigravity
---

# ColorOS 17 Clock Font Converter Toolkit

This skill converts standard static fonts (TTF/OTF) into ColorOS 17 variable clock fonts conforming to host-process reverse-engineered specifications (`com.oplus.wallpapers`, `com.android.systemui`, `com.oplus.keyguard.personality.clocks`).

---

## 1. Specifications & Invariants

### 1.1 Fixed Fonts (LayoutType = 1, e.g. OPPODigit01.ttf)
- **unitsPerEm**: Strict `1000`.
- **Metrics**:
  - `hhea`: `ascent = 928`, `descent = -244`, `lineGap = 0`.
  - `OS/2`: `sTypoAscender = 850`, `sTypoDescender = -150`, `sTypoLineGap = 200`.
  - `OS/2`: `usWinAscent = 928`, `usWinDescent = 244`, `usWeightClass = 700`.
- **Glyph Box**:
  - Numerals visual height $\approx 738\text{ units}$ ($y \in [-36, 702]$).
  - Center digits vertically in $y \in [-25, 715]$.
- **Variable Axes**:
  - `fvar`: `wght` axis (`min = 100.0, default = 700.0, max = 700.0`).
  - `gvar`: Contains `wght` tuple variations. If no hand-crafted thin master exists, zero deltas keep 100% original glyph vector quality without distortion.

### 1.2 Flex / Tunable Fonts (LayoutType = 2, e.g. OPPOSans4.0.ttf)
- **unitsPerEm**: Strict `1000`.
- **Metrics**:
  - `hhea`: `ascent = 700`, `descent = -3543`, `lineGap = 0`.
  - `OS/2`: `sTypoAscender = 700`, `sTypoDescender = -3543`, `sTypoLineGap = 0`.
  - `OS/2`: `usWinAscent = 700`, `usWinDescent = 3543`, `usWeightClass = 1000`.
- **Top Anchor & Downward Stretch**:
  - Cap top anchored at $y = 673$.
  - At `HGHT = 1`: visual height = $748\text{ units}$ ($y \in [-75, 673]$).
  - At `HGHT = 1000`: visual height = $4201\text{ units}$ ($y \in [-3528, 673]$).
  - Formula in `TextFontHeightUtil.java`:
    $$\text{textHeight} = \text{textSize} \times (\text{HGHT} \times 0.003462 + 0.748)$$
- **Variable Axes**:
  - `fvar`: `wght` (`100..1000`, default `1000.0`) and `HGHT` (`1..1000`, default `1000.0`).
  - `gvar`:
    - `HGHT`: Piecewise zonal stretch anchored at $y = 673$, elongating vertical stems into negative Y space while keeping top/bottom caps constant.
    - `wght`: Delivers bold-to-thin interpolation if multi-master coordinates are supplied.

---

## 2. Weight Diff & Multi-Master Design Guidelines

When designing or interpolating variable fonts for ColorOS 17, artificial geometric erosion (Minkowski sum or normal offsetting) degrades aesthetic quality by introducing self-intersections, sharp spurs, and distorted corner radii.

### Principles for Designers & AI Agents

1. **Topology & Point Count Invariant**:
   - In OpenType `gvar`, every master glyph must share the exact same number of contours and on/off-curve points in the exact same sequence.
   - Point index $i$ in master $A$ maps directly to point index $i$ in master $B$:
     $$\Delta P_i = P_{i,\text{thin}} - P_{i,\text{bold}}$$
2. **Optical Scaling vs Mathematical Thinning**:
   - **Horizontal Bars**: Thinning should primarily reduce vertical bar height symmetrically around their horizontal axes, preserving clear counter spaces (e.g. inside `0`, `6`, `8`, `9`).
   - **Vertical Stems**: Move the inner contours outwards or outer contours inwards while maintaining straightness.
   - **Corner Radii**: Keep external fillet radii constant or proportionally adjusted to prevent collapsing into jagged edges.
3. **Pristine Single-Master Strategy**:
   - If only a single master TTF exists (e.g. bold or regular), prefer keeping `gvar` `wght` deltas at zero (identity). This guarantees that at any slider value, the user experiences 100% of the original font designer's pristine curves and spacing, rather than an algorithmic distortion.

---

## 3. "Weight Not Adjustable" Architecture in ColorOS 17

ColorOS 17 handles non-adjustable font weights through several distinct layers:

1. **Host Whitelist (`R.array.keyguard_style_fonts_for_disable_font_weight`)**:
   - Stock APK (`KeyguardPersonalityClocks.apk`) defines an array of font filenames for which weight adjustment is disabled:
     - `OPPOShenqiRegular.ttf`
     - `OPPOWendaoRegular.ttf`
     - `OPPOGaladaRegularExport.ttf`
     - `OPPOModakHeavyExport.ttf`
     - `OPPOShenqiRegularExport.ttf`
   - In `EditPanelBuilder.createfontSettingsPanel`:
     When the user selects any font in this array, the panel invokes:
     `oplusKeyguardStyleFontSettingPanel.setSeekBarEnable(!ArraysKt.contains(stringArray, selectedFont));`
     This calls `mFontSeekBar.setEnabled(false)` on `OplusKeyguardStyleFontWeightBar` (COUISeekBar), greying out the slider and locking weight at default.
2. **Panel UI Visibility (`showFontWeightBar`)**:
   - `OplusKeyguardStyleFontSettingPanel.showFontWeightBar(boolean show)` toggles the container layout `R.id.rl_weight_bar` between `VISIBLE` and `GONE`.
   - In `setupFont(...)`, the parameter `boolean z6` (`showFontWeightBar`) hides the entire weight bar container when `false`.
3. **TextView Attribute (`disable_font_weight_adjustment`)**:
   - In `MyCustomizedTextView.java`: `disable_font_weight_adjustment` prevents `setTypeface()` from updating font variation settings at runtime.
4. **RealOS Custom Font Integration**:
   - For custom fonts in `registry.json`, adding `"weightAdjustable": false` allows `ClockFontPluginHook` to intercept `setSeekBarEnable` or augment `keyguard_style_fonts_for_disable_font_weight`, locking the slider for non-variable or single-weight designs.

---

## 4. Converter CLI Usage

Located at `scripts/font_converter/coloros_clock_converter.py`.

```bash
# Convert to Fixed Mode (LayoutType 1)
python scripts/font_converter/coloros_clock_converter.py input.ttf -o output_fix.ttf --mode fixed --name "FontName (Fixed)"

# Convert to Flex Mode (LayoutType 2)
python scripts/font_converter/coloros_clock_converter.py input.ttf -o output_flx.ttf --mode flex --name "FontName (Flex)"

# Optional: Enable synthetic normal-thinning (disabled by default to preserve stock quality)
python scripts/font_converter/coloros_clock_converter.py input.ttf -o output_fix.ttf --mode fixed --synthetic-weight
```
