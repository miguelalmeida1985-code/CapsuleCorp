# Panalume

Open **Panalume** in the HA sidebar. Press the faint pencil (top-right) or **E** to edit.

- **Add** cards from the panel; drag to move (between sidebar and main area too), drag any edge/corner to resize. Shift = 1px precision, arrows nudge, Delete, Ctrl+D duplicate, Ctrl+Z undo.
- **Card → Content / Style / Actions / Size**: any text field can be an HA template (`{{ states('sensor.x') }}`), including colours and styling.
- **Actions**: tap/hold → toggle, more-info pop-up, pop-up of other cards, navigate, service call, URL.
- **Layout**: one main layout shows on every device — desktop scales it, phone gets an automatic single-column version. Give phone or desktop its own custom layout if you want (Layout tab, or just start dragging on that device). New cards are added to every layout. Pin a screen with `?device=phone` / `desktop`.
- **Groups**: rooms move as one. Double-tap a card to move it alone; Shift/Ctrl-click or drag a box to select several, then Group (Ctrl+G), align or size them together.
- **Show / hide parts** of bigger cards (EV, Octopus, heat pump…) with the chips at the top of Content or the eye next to each field.
- **Theme**: colours, fonts (Google Fonts URL), card glass/blur (set blur 0 on slow tablets), global CSS.

**TV** — add `?tv` to the address (e.g. in a kiosk app on an Android TV). The remote's arrows move a glowing highlight to the nearest card or button, OK presses it, Back closes pop-ups and the views menu. Blur is off on TVs to keep them smooth (`?effects=full` turns it back on); `?effects=lite` turns blur off on any slow screen. A TV can't be edited.

**Version** — every screen shows the version it's running in the bottom-left corner (`?noversion` hides it; a kiosk app can add its own with `?app_version=1.2.0`). After an update in Home Assistant every open screen reloads itself within seconds; one that's mid-edit shows `v0.1.0 → v0.2.0` in amber and reloads when the edit is done.

Config is stored in the app's `/data/dashboard.json` (Layout → Export for a backup).

## Energy cards

Added from **Add → Energy**; entities are auto-detected for the Octopus Energy and myenergi integrations (editable after).

- **Octopus rates** — current/next rate, cheapest 2h, half-hourly bars (today / next 24h / two days) with Intelligent dispatches, Octoplus sessions and export rate overlaid.
- **Octoplus sessions** — live session banner with baseline vs this half-hour's usage, upcoming Saving / Power Down / Power Up / Free Electricity sessions with **Join**, points and 12-month stats.
- **Intelligent Octopus** — dispatch status, planned/recent slots, smart/bump charge, ready-by time, charge target.
- **Energy cost today** — cost, kWh, average p/kWh, export, solar, yesterday, peak/off-peak split, half-hourly usage coloured by rate.
- **Heat pump** — live COP/SCOP, kW in/out, flow & outdoor temp, hot water + Boost.
- **Zappi / Eddi** — status, mode buttons, boosts.
- **Electric vehicle** — battery ring vs target, range, charging, plug/lock, climate.
- **Power flow** — solar / grid / battery / home / EV.

**Fox ESS** inverters (H1, H3, KH…) are detected too, from the local `foxess_modbus` integration or the FoxESS cloud one: Solar, Energy flow, Power flow and Home battery fill in PV power, house load, grid in/out and the battery with the right signs.

**Water heater** (Add → Controls) — a glass tank filled to the water temperature that glows and bubbles while heating, with the target − / +, operation modes and on / off. For Ariston (Velis, Lydos…) it also finds the showers available, eco mode and the heating and anti-legionella sensors.

## Import from ha-fusion

Edit → **Layout → Import from ha-fusion** rebuilds the dashboard from your ha-fusion app: views, rooms, buttons (their state / name / icon / colour / service templates keep working, including `entity_id`), cameras and sidebar items, with tablet, phone and desktop layouts. An **Energy** view is added from auto-detected Octopus / myenergi / solar / EV entities. Icons from other sets (`tabler:`, `mingcute:`, `solar:` …) load from Iconify.

## House card (CapsuleOS)

A picture of your house with live glass labels pinned to it. Add → **Info → House**.

- **Picture**: put it in Home Assistant's `www` folder and use `/local/house.webp`. A cut-out on a transparent background looks best. Add a second picture with the outdoor lights off (same framing) and pick the **outdoor lights**: the card fades between the two as they switch. Without the second picture it just dims.
- **Labels**: pick the entities to pin. Tap a label to toggle a light or switch, open a camera full screen, or open the details of anything else.
- **Placing**: while editing, tick **Move the labels** and drag each label's dot onto its device (positions are kept in % of the picture). Move the card itself by the blue corner.
- **Solar**: pick the solar power sensor and the peak in kW; with Move the labels ticked, drag the four corners onto the panels. The panels shimmer faster the more they produce and stop at night.
