# :design:uikit — VPNis Design System components

Thin, brand-pinned wrappers over Material 3, in the style of Now in Android's
`core/designsystem`. This document is the **decision-record** (issue #25) that
governs every component added under epic #24. It is a gate: settle a convention
here once, rather than re-deciding it per component.

> ⚠️ **Public API is UNSTABLE.** Until a dedicated future "screens" epic puts the
> first real consumer screen in front of these wrappers, their signatures may
> change — additively or breaking — without deprecation. Do **not** treat this
> module's API as frozen. The first real screen validates wrapper ergonomics;
> only then do we stabilise. No effort is spent on API freezing before that.

---

## 1. When to add a `VPNis*` wrapper (vs re-export / raw M3)

Add a wrapper only when **at least one** holds:

1. **Pinning off-brand M3 defaults** to brand tokens (e.g. `VPNisOutlinedButton`
   pins label = `primary`, stroke = `outline`; `VPNisTonalButton` pins
   `primaryContainer` — M3 defaults are off-brand here).
2. **Encoding a repeated compositional decision** (e.g. "control + label +
   clickable row" in `VPNisLabeledSwitch`).
3. **Protecting an invariant** the app must not break (wiring `isError` /
   `supportingText`, a11y semantics, `contentDescription` for icons).

If none holds and the component is a pure passthrough, **do not wrap** — let the
theme apply brand tokens globally and use M3 directly (Approach C). Prefer the
narrowest thing that satisfies the design.

**Flat parameters (`text: String`, `checked/onCheckedChange`) are allowed ONLY
for binary / text-only controls** where content never varies (buttons, the bare
Switch/Checkbox/RadioButton). Everything slot-shaped (TextField, ListItem, Card,
Banner, navigation) keeps M3 slots — a flat `String` under `explicitApi()` breaks
at the first icon/supporting case and forces a breaking API change.

### Buttons are a deliberate flat exception

`VPNisButton` / `VPNisOutlinedButton` / `VPNisTonalButton` use a flat
`text: String` API. This is a **conscious exception for binary/text controls**,
**not** the reference for the slot pattern. Do not cite the buttons as the model
when building slot-shaped families.

---

## 2. Package structure

Subpackages by family (the epic jumps from 3 to ~15 files at once, past the
~8–10 flat-structure threshold; cheaper to introduce before the API stabilises):

```
org.yarokovisty.vpnis.design.uikit
├── button/       VPNisButton, VPNisOutlinedButton, VPNisTonalButton   (existing)
├── selection/    Switch / Checkbox / TriStateCheckbox / RadioButton   (#28)
├── card/         Card / ElevatedCard / OutlinedCard                   (#29)
├── banner/       VPNisBanner (custom component)                       (#30)
├── input/        TextField / OutlinedTextField                        (#31)
├── list/         ListItem                                             (#32)
└── navigation/   NavigationBar / Rail, Primary/SecondaryTabRow        (#33)
```

Buttons were moved into `button/` in #25 while `:app` is the only (internal)
consumer and changing the import is cheap.

---

## 3. Dependency visibility matrix

| Edge | Scope | Rule |
|---|---|---|
| uikit → `material3` | **`api` — by rule** | Switch to `api` **in the family where a public signature first exposes an M3 type** (e.g. `KeyboardOptions`/`VisualTransformation` in #31, `CardColors` in #29). Not switched pre-emptively. Each family's DoD includes the check "does an M3 type leak into public API?" |
| `compose-ui` | **`api` — at convention-plugin level** | Set in `vpnis.android.library.compose`. Deliberate: design modules already expose compose-ui types publicly (`Modifier` on every component, brand `Color`/`Shapes` from `:design:theme`). |
| uikit → `:design:theme` | **`implementation`** | Stays `implementation` until a public uikit signature exposes a type owned by the theme. |

**M3-leak check (per family DoD):** if a public `VPNis*` signature returns or
accepts an M3 type (`*Colors`, `*Elevation`, `KeyboardOptions`,
`VisualTransformation`, `WindowInsets`, …), `material3` must be `api` in that
module — otherwise `:app` won't get the type transitively and the build breaks
under `explicitApi()`.

---

## 4. `VPNisSemanticColors` (banner warning role) — DECISION ONLY

Material 3 has no `warning` color role. The banner's `warning` variant needs one.
Two options were on the table: reuse `tertiary` (already a brand accent — may
visually clash) vs. a dedicated `VPNisSemanticColors`.

**Decision:** if `VPNisSemanticColors` is chosen, its type +
`LocalVPNisSemanticColors` `CompositionLocal` + `CompositionLocalProvider` **live
in `:design:theme`** (provided inside `VPNisTheme`). `:design:uikit` only **reads**
`LocalVPNisSemanticColors.current` in a composable body. This breaks the
`theme ↔ uikit` cycle (if the type lived in uikit, theme would have to depend on
uikit to provide it) and keeps uikit → theme at `implementation`.

The concrete `tertiary`-vs-`VPNisSemanticColors` choice and any code are made in
the **banner issue (#30)** with the banner as the first real consumer — **not**
speculatively here. This record only fixes *where* the type lives if introduced.

---

## 5. Acceptance checklist (I3) — mandatory per family

Visual verification, **not** automated screenshot tests (out of scope). Each
component ships with the applicable `@Preview` set and passes `manual-tester`
review against the "VPNis Material 3" reference tokens (already materialised in
`:design:theme` — see `design/theme/COLOR_AUDIT.md`).

**Mandatory `@Preview` matrix:**

- light **and** dark — always.
- enabled **and** disabled — always.
- error state — where the component has one (TextField, …).
- **RTL** — for RTL-sensitive components (leading/trailing icons: TextField,
  ListItem, NavigationBar).
- **`fontScale = 1.5f`** — for text-carrying components (TextField, ListItem,
  Banner, tab/nav labels).

**A11y minimum:**

- 48 dp touch target (`minimumInteractiveComponentSize()` on interactive rows).
- `contentDescription` on meaningful icons and on dismiss buttons.
- Correct roles: `toggleable(role = …)` for Switch/Checkbox rows (no
  `selectableGroup`); `selectable(role = RadioButton)` **+** `selectableGroup()`
  for mutually-exclusive radio groups; `clearAndSetSemantics {}` on the inner M3
  control so TalkBack doesn't announce twice.

**States a `@Preview` cannot show** (e.g. TextField focus ring) → documented as a
manual on-device checklist item in the family issue.

**Fallback when the reference doesn't cover a family:** the "matches design"
verdict cannot be signed off alone. Minimum = **unit-assert on brand-token
pinning** (shape / elevation / colors == theme values) **+** passing the
mandatory `@Preview` checklist.

---

## 6. Experimental M3 API rule (I4)

If an overload is annotated `@ExperimentalMaterial3Api` (e.g. the
`TextFieldState`-based overloads), the public uikit API uses **only** the stable
`value` / `onValueChange` form — **no `@OptIn` leaks into the public API** of
this module. Experimental types stay internal to implementation, never in a
public signature.
