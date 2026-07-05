# VPNis Material 3 — Colour Scheme Coverage Audit

Generated for issue #17 (DesignSync token import). The design source is the
"VPNis Material 3" canvas exported via the claude_design MCP.

Legend:
- **BRANDED** — hex value taken directly from the design canvas.
- **DERIVED** — value not in the canvas; a reasonable M3 baseline for this indigo hue was used and is documented here.
- **M3 DEFAULT** — role not set in `lightColorScheme()` / `darkColorScheme()`; the M3 library default applies.

---

## ColorScheme roles (30 standard + 6 surface-container extensions)

| Role | Light | Dark |
|---|---|---|
| primary | BRANDED `#4C5EAF` | BRANDED `#8EA0FF` |
| onPrimary | BRANDED `#FFFFFF` | DERIVED `#172978` |
| primaryContainer | BRANDED `#DEE0FF` | BRANDED `#333F90` |
| onPrimaryContainer | BRANDED `#00105C` | BRANDED `#DEE0FF` |
| inversePrimary | M3 DEFAULT | M3 DEFAULT |
| secondary | DERIVED `#5A5D72` | DERIVED `#C0C6DC` |
| onSecondary | DERIVED `#FFFFFF` | DERIVED `#2A3042` |
| secondaryContainer | BRANDED `#DFE1F9` | BRANDED `#424559` |
| onSecondaryContainer | BRANDED `#171B2C` | BRANDED `#DFE1F9` |
| tertiary | BRANDED `#2F6A5F` | BRANDED `#9AD0C3` |
| onTertiary | BRANDED `#FFFFFF` | BRANDED `#003730` |
| tertiaryContainer | BRANDED `#B6ECDF` | BRANDED `#164F45` |
| onTertiaryContainer | BRANDED `#00201A` | DERIVED `#B6ECDF` |
| error | BRANDED `#BA1A1A` | DERIVED `#FFB4AB` |
| onError | DERIVED `#FFFFFF` | DERIVED `#690005` |
| errorContainer | BRANDED `#FFDAD6` | DERIVED `#93000A` |
| onErrorContainer | BRANDED `#410002` | DERIVED `#FFDAD6` |
| background | BRANDED `#FBF8FF` | BRANDED `#121318` |
| onBackground | BRANDED `#1B1B21` | BRANDED `#E4E1E9` |
| surface | BRANDED `#FBF8FF` | BRANDED `#121318` |
| onSurface | BRANDED `#1B1B21` | BRANDED `#E4E1E9` |
| surfaceVariant | BRANDED `#E3E1EC` | DERIVED `#46464F` |
| onSurfaceVariant | BRANDED `#46464F` | BRANDED `#C7C5D0` |
| surfaceTint | M3 DEFAULT (= primary) | M3 DEFAULT (= primary) |
| inverseSurface | M3 DEFAULT | M3 DEFAULT |
| inverseOnSurface | M3 DEFAULT | M3 DEFAULT |
| outline | BRANDED `#767680` | DERIVED `#90909A` |
| outlineVariant | BRANDED `#C7C5D0` | DERIVED `#45464F` |
| scrim | M3 DEFAULT | M3 DEFAULT |
| surfaceContainer | BRANDED `#EFEDF4` | BRANDED `#1F1F25` |
| surfaceContainerLow | BRANDED `#F5F2FA` | BRANDED `#1B1B21` |
| surfaceContainerHigh | M3 DEFAULT | M3 DEFAULT |
| surfaceContainerHighest | M3 DEFAULT | M3 DEFAULT |
| surfaceBright | M3 DEFAULT | M3 DEFAULT |
| surfaceDim | M3 DEFAULT | M3 DEFAULT |

**Summary (light):** 22 BRANDED · 4 DERIVED · 10 M3 DEFAULT  
**Summary (dark):** 16 BRANDED · 10 DERIVED · 10 M3 DEFAULT

---

## Typography styles

| Style | Status | Values |
|---|---|---|
| displayLarge | BRANDED | 57sp / Normal / lh 64sp / ls −0.25sp |
| displayMedium | M3 DEFAULT | — |
| displaySmall | M3 DEFAULT | — |
| headlineLarge | M3 DEFAULT | — |
| headlineMedium | BRANDED | 32sp / Normal / lh 40sp |
| headlineSmall | M3 DEFAULT | — |
| titleLarge | BRANDED | 22sp / Medium / lh 28sp |
| titleMedium | M3 DEFAULT | — |
| titleSmall | M3 DEFAULT | — |
| bodyLarge | BRANDED | 16sp / Normal / lh 24sp / ls 0.5sp |
| bodyMedium | M3 DEFAULT | — |
| bodySmall | M3 DEFAULT | — |
| labelLarge | M3 DEFAULT | — |
| labelMedium | BRANDED | 12sp / Medium / lh 16sp / ls 0.5sp |
| labelSmall | M3 DEFAULT | — |

**Summary:** 5 BRANDED · 10 M3 DEFAULT  
Font family: `FontFamily.Default` (Roboto on Android) — no custom font resource added.

---

## Shapes

All five corner families set explicitly from the design spec.

| Token | Value |
|---|---|
| extraSmall | RoundedCornerShape(4.dp) |
| small | RoundedCornerShape(12.dp) |
| medium | RoundedCornerShape(16.dp) |
| large | RoundedCornerShape(20.dp) |
| extraLarge | RoundedCornerShape(28.dp) |

Button shape: M3 `Button` / `FilledTonalButton` / `OutlinedButton` use the
theme's `shapes.full` (fully-rounded pill) by default — no per-button override needed.
