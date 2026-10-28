<script module>
  export const meta = {
    type: 'house', name: 'House', icon: 'mdi:home-lightbulb-outline', category: 'Info',
    size: { w: 900, h: 560 }, tap: 'none',
    defaults: { image: '', image_off: '', lights: [], pins: [], pins_pos: {}, pin_lines: '', solar: '', solar_max: 5, solar_area: '', solar_pin: true, sky: true, place_pins: false },
    fields: [
      { key: 'image', label: 'Picture of the house (URL, e.g. /local/house.webp)', type: 'text', hint: 'Put the file in Home Assistant\'s www folder; /local/ serves it. A cut-out on a transparent background looks best.' },
      { key: 'image_off', label: 'Same picture with the outdoor lights off (optional)', type: 'text', hint: 'Same framing. Without it the picture is just dimmed when the lights are off.' },
      { key: 'lights', label: 'Outdoor lights (the picture lights up when any is on)', type: 'entities' },
      { key: 'lit', label: 'Lit when (template, overrides the lights)', type: 'text' },
      { key: 'pins', label: 'Labels on the house', type: 'entities', hint: 'Each entity gets a glass label pinned to the house. Tick "Move the labels" below to place them.' },
      { key: 'pin_lines', label: 'Labels\' second lines (one per line: entity_id: text, templates ok)', type: 'textarea', hint: 'e.g. binary_sensor.doorbell: Último toque {{ as_timestamp(states.binary_sensor.doorbell.last_changed) | timestamp_custom("%H:%M") }}. Without one, CapsuleOS picks: mode and target for air conditioners, "A aquecer" for the water heater, battery or "Online" for cameras.' },
      { key: 'place_pins', label: 'Move the labels and the solar panel corners (while editing)', type: 'bool' },
      { key: 'solar', label: 'Solar power sensor (W or kW)', type: 'entity', domain: 'sensor' },
      { key: 'solar_max', label: 'Solar peak (kW)', type: 'number', placeholder: '5' },
      { key: 'solar_pin', label: 'Solar label over the panels', type: 'bool' },
      { key: 'sky', label: 'Night sky behind the house', type: 'bool' },
    ],
  };
</script>

<script>
  // A picture of the house with live glass labels pinned to it: the picture
  // follows the outdoor lights (lit / dark) and the solar panels shimmer while
  // they produce. Positions are stored in % of the picture, so they stay put at
  // any card size.
  import Icon from '../components/Icon.svelte';
  import { t, ent, truthy } from '../lib/tpl.js';
  import { name, stateText, entityIcon, isActive, toggle, domain, formatNumber } from '../lib/entity.js';
  import { states } from '../lib/ha.svelte.js';
  import { stateWord, say } from '../lib/i18n.svelte.js';
  import { app, changed } from '../lib/config.svelte.js';
  import { haImage } from '../lib/ha.svelte.js';
  let { props, w, h, editing } = $props();

  const src = $derived(haImage(t(props.image)));
  const srcOff = $derived(haImage(t(props.image_off)));
  let ratio = $state(1.68);
  const box = $derived.by(() => {
    // With the night sky the picture fills the card (cropping a little); without it, it sits whole at the bottom.
    const bw = props.sky !== false ? Math.max(w, h * ratio) : Math.min(w, h * ratio);
    return { w: bw, h: bw / ratio, x: (w - bw) / 2, y: props.sky !== false ? (h - bw / ratio) * 0.6 : h - bw / ratio };
  });

  const lit = $derived(props.lit ? truthy(t(props.lit)) : !(props.lights || []).length || (props.lights || []).some((id) => isActive(ent(id))));

  // Solar: kW now, 0–1 of the peak, and the panel outline.
  const sun = $derived(props.solar ? ent(props.solar) : null);
  const kw = $derived.by(() => {
    const v = Number(sun?.state);
    if (!Number.isFinite(v)) return 0;
    return /^w$/i.test(sun.attributes.unit_of_measurement || '') ? v / 1000 : v;
  });
  const pv = $derived(Math.max(0, Math.min(1, kw / (Number(props.solar_max) || 5))));
  const placing = $derived(editing && !!props.place_pins);
  const area = $derived.by(() => {
    const pts = String(props.solar_area || '').trim().split(/\s+/).map((p) => p.split(',').map(Number)).filter((p) => p.length === 2 && p.every(Number.isFinite));
    return pts.length >= 3 ? pts : placing && props.solar ? [[40, 20], [60, 18], [60, 28], [40, 30]] : [];
  });
  const bbox = $derived(area.length ? { x0: Math.min(...area.map((p) => p[0])), x1: Math.max(...area.map((p) => p[0])), y0: Math.min(...area.map((p) => p[1])), y1: Math.max(...area.map((p) => p[1])) } : null);

  const COLOR = { light: '#ffb84d', switch: '#ffb84d', climate: '#5ccbff', water_heater: '#ff5a4a', camera: '#cfe0f5', sensor: '#ffb84d', binary_sensor: '#cfe0f5' };
  const lines = $derived(Object.fromEntries(String(props.pin_lines || '').split('\n').map((l) => l.match(/^\s*([a-z_]+\.[a-z0-9_]+)\s*:\s*(.+)$/)).filter(Boolean).map((m) => [m[1], m[2]])));
  const MODE = { cool: ['Cool', 'Frio'], heat: ['Heat', 'Calor'], dry: ['Dry', 'Desumidificar'], fan_only: ['Fan', 'Ventilar'], auto: ['Auto', 'Auto'], heat_cool: ['Auto', 'Auto'] };
  const battery = (id) => {
    const o = id.split('.')[1];
    for (const k of [`sensor.${o}_battery`, `sensor.${o}_battery_level`, `sensor.${o}_battery_percentage`]) if (states.has(k)) return states.get(k).state;
    return null;
  };
  // The label's second line: what's worth knowing at a glance, with a status dot.
  function sub(id, e, d) {
    if (lines[id]) return { text: t(lines[id]), dot: 'ok' };
    if (!e || e.state === 'unavailable') return { text: say('Unavailable', 'Indisponível'), dot: 'bad' };
    const a = e.attributes;
    if (d === 'climate') return e.state === 'off' ? { text: say('Off', 'Desligado'), dot: '' } : { text: `${stateWord('climate', null, e.state) || (MODE[e.state] ? say(...MODE[e.state]) : e.state)}${a.temperature != null ? ` · ${formatNumber(a.temperature, a.temperature % 1 ? 1 : 0)}°` : ''}`, dot: '' };
    if (d === 'water_heater') return e.state === 'off' ? { text: say('Off', 'Desligado'), dot: '' } : a.current_temperature != null && a.temperature != null && a.current_temperature < a.temperature - 0.5 ? { text: say('Heating', 'A aquecer'), dot: '' } : { text: say('Ready', 'Pronto'), dot: '' };
    if (d === 'camera') { const b = battery(id); return b != null && b !== 'unavailable' ? { text: `${say('Battery', 'Bateria')} ${Math.round(Number(b))}%`, dot: 'warn' } : { text: 'Online', dot: 'ok' }; }
    if (a.device_class === 'power') return { text: say('Now', 'Agora'), dot: '' };
    if (d === 'binary_sensor') return { text: stateText(e), dot: isActive(e) ? 'warn' : 'ok' };
    return null;
  }
  const value = (e, d) => d === 'binary_sensor' ? '' : d === 'water_heater' ? (e?.attributes.current_temperature != null ? `${formatNumber(e.attributes.current_temperature, 0)}°C` : stateText(e)) : d === 'climate' ? (e?.attributes.current_temperature != null ? `${formatNumber(e.attributes.current_temperature, 0)}°C` : stateText(e)) : d === 'camera' ? '' : stateText(e);
  const pins = $derived.by(() => {
    const out = (props.pins || []).map((id, i) => {
      const e = ent(id);
      const d = domain(id);
      const pos = props.pins_pos?.[id] || [15 + (i % 4) * 23, 20 + Math.floor(i / 4) * 22];
      const off = !e || ['light', 'switch', 'climate', 'water_heater', 'fan', 'input_boolean'].includes(d) ? !isActive(e) && !(d === 'water_heater' && e?.state !== 'off') : e.state === 'unavailable';
      return { id, e, d, x: pos[0], y: pos[1], side: pos[1] < 18 ? 't' : pos[0] > 50 ? 'r' : 'l', value: value(e, d), sub: sub(id, e, d), off, color: COLOR[d] || '#cfe0f5' };
    });
    // Solar: a label above the panels while they produce.
    if (props.solar && props.solar_pin !== false && bbox && !(props.pins || []).includes(props.solar)) {
      const pos = props.pins_pos?.[props.solar] || [(bbox.x0 + bbox.x1) / 2, bbox.y0 + 2];
      out.push({ id: props.solar, e: sun, d: 'sensor', x: pos[0], y: pos[1], side: 't', value: `${formatNumber(kw, 2)} kW`, sub: { text: say('Producing now', 'Produção agora'), dot: '' }, off: kw <= 0.05, color: '#ffb84d', icon: 'mdi:white-balance-sunny', label: 'Solar' });
    }
    return out;
  });

  function tap(p) {
    if (editing) return;
    if (p.d === 'camera') app.popup = { camera: p.id, name: name(p.e) };
    else if (['light', 'switch', 'fan', 'input_boolean'].includes(p.d) && p.e) toggle(p.e);
    else app.popup = { entity: p.id };
  }

  // Dragging labels and panel corners while editing ("Move the labels" ticked).
  let stage = $state();
  let drag = null;
  const pct = (ev) => {
    const r = stage.getBoundingClientRect();
    return [Math.round(Math.max(1, Math.min(99, ((ev.clientX - r.left) / r.width) * 100)) * 10) / 10, Math.round(Math.max(1, Math.min(99, ((ev.clientY - r.top) / r.height) * 100)) * 10) / 10];
  };
  function grab(ev, what) {
    if (!placing) return;
    ev.stopPropagation(); ev.preventDefault();
    ev.currentTarget.setPointerCapture(ev.pointerId);
    drag = what;
  }
  function move(ev) {
    if (!drag) return;
    const [x, y] = pct(ev);
    if (drag.pin) props.pins_pos = { ...(props.pins_pos || {}), [drag.pin]: [x, y] };
    else props.solar_area = area.map((p, i) => (i === drag.corner ? [x, y] : p)).map((p) => p.join(',')).join(' ');
  }
  function drop() {
    if (drag) changed();
    drag = null;
  }
</script>

<div class="house" class:dark={!lit} class:dim={!lit && !srcOff} class:placing class:sky={props.sky !== false}>
  <div class="stage" bind:this={stage} style="left:{box.x}px;top:{box.y}px;width:{box.w}px;height:{box.h}px" onpointermove={move} onpointerup={drop} onpointercancel={drop} role="presentation">
    {#if src}
      <img class="pic" {src} alt="" onload={(e) => e.currentTarget.naturalWidth && (ratio = e.currentTarget.naturalWidth / e.currentTarget.naturalHeight)} />
      {#if srcOff}<img class="pic off" src={srcOff} alt="" />{/if}
    {:else}
      <div class="empty"><Icon icon="mdi:home-outline" size="2em" /><span>Add a picture of the house in the card's settings</span></div>
    {/if}
    <div class="glow"></div>
    <div class="bloom" aria-hidden="true"></div>
    <!-- Stars over the picture's dark sky (screen blend: they only show on black). -->
    {#if props.sky !== false}<div class="stars" aria-hidden="true"></div><div class="stars twinkle" aria-hidden="true"></div>{/if}

    {#if area.length && (kw > 0.05 || placing)}
      <!-- Plain boxes animated by opacity and transform only, so the GPU runs the
           shimmer without repainting the picture every frame. -->
      {@const clip = `clip-path:polygon(${area.map((p) => `${p[0]}% ${p[1]}%`).join(',')})`}
      <div class="pv" style="--pv:{pv};--dur:{(4.2 - pv * 2.6).toFixed(1)}s" aria-hidden="true">
        <div class="pv-glow"><div class="pv-soft"><div class="pv-fill" style={clip}></div></div></div>
        <div class="pv-shine" style={clip}>
          <div class="pv-band" style="left:{bbox.x0 - (bbox.x1 - bbox.x0) * 0.35}%;top:{bbox.y0 - 2}%;width:{(bbox.x1 - bbox.x0) * 0.3}%;height:{bbox.y1 - bbox.y0 + 4}%"></div>
        </div>
      </div>
      {#if placing}
        {#each area as p, i}
          <div class="corner" style="left:{p[0]}%;top:{p[1]}%" role="button" tabindex="-1" onpointerdown={(ev) => grab(ev, { corner: i })}></div>
        {/each}
      {/if}
    {/if}

    {#each pins as p (p.id)}
      <div class="pin {p.side}" class:off={p.off} style="left:{p.x}%;top:{p.y}%;--c:{p.color}">
        <span class="dot"></span><span class="stem"></span>
        <button class="label" data-stop onclick={() => tap(p)} onpointerdown={(ev) => grab(ev, { pin: p.id })}>
          <span class="ic"><Icon icon={p.icon || entityIcon(p.e)} size="18px" /></span>
          <span class="txt"><span class="n">{p.label || name(p.e) || p.id}</span>{#if p.value}<span class="v">{p.value}</span>{/if}{#if p.sub}<span class="s">{#if p.sub.dot}<i class="sd {p.sub.dot}"></i>{/if}{p.sub.text}</span>{/if}</span>
        </button>
      </div>
    {/each}
  </div>
</div>

<style>
  .house { position: absolute; inset: 0; overflow: hidden; border-radius: inherit; isolation: isolate; }
  /* Night sky: drawn once; a second layer of stars only fades in and out. */
  .sky { background: radial-gradient(60% 45% at 55% 92%, rgba(255,170,70,.28), transparent 70%), radial-gradient(70% 60% at 50% 0%, #1b2f55, transparent 70%), linear-gradient(180deg, #0c1a33 0%, #0a1322 60%, #120f12 100%); }
  .sky::after { content: ''; position: absolute; inset: 0; pointer-events: none; z-index: 4; border-radius: inherit; background: radial-gradient(120% 90% at 50% 45%, transparent 60%, rgba(4,8,16,.55) 100%); }
  .stars { position: absolute; inset: 0 0 55% 0; pointer-events: none; opacity: .8; mix-blend-mode: screen;
    background-image: radial-gradient(1px 1px at 7% 12%, #fff, transparent), radial-gradient(1px 1px at 18% 30%, #cfe0ff, transparent), radial-gradient(1.5px 1.5px at 27% 8%, #fff, transparent), radial-gradient(1px 1px at 38% 22%, #fff, transparent), radial-gradient(1px 1px at 47% 5%, #dfe8ff, transparent), radial-gradient(1.5px 1.5px at 61% 16%, #fff, transparent), radial-gradient(1px 1px at 72% 7%, #cfe0ff, transparent), radial-gradient(1px 1px at 83% 26%, #fff, transparent), radial-gradient(1.5px 1.5px at 92% 11%, #fff, transparent), radial-gradient(1px 1px at 12% 44%, #fff, transparent), radial-gradient(1px 1px at 88% 42%, #dfe8ff, transparent), radial-gradient(1px 1px at 55% 34%, #fff, transparent); }
  .stars.twinkle { opacity: 0; animation: twinkle 5s ease-in-out infinite; transform: translate(3%, 4%); will-change: opacity; }
  @keyframes twinkle { 50% { opacity: .9; } }
  .dark .bloom, .bloom { position: absolute; inset: 0; pointer-events: none; }
  /* Lights on: a warm bloom over the lit picture that breathes slowly (opacity only). */
  .bloom { background: radial-gradient(45% 30% at 50% 78%, rgba(255,190,100,.22), transparent 70%), radial-gradient(30% 22% at 30% 55%, rgba(255,200,120,.12), transparent 70%), radial-gradient(30% 22% at 70% 55%, rgba(255,200,120,.12), transparent 70%); mix-blend-mode: screen; animation: breathe 4.5s ease-in-out infinite; will-change: opacity; transition: visibility 1.2s; }
  .dark .bloom { visibility: hidden; animation: none; }
  @keyframes breathe { 50% { opacity: .55; } }
  .stage { position: absolute; }
  .pic { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: fill; transition: opacity 1.2s ease, filter 1.2s ease; filter: drop-shadow(0 24px 30px rgba(0,0,0,.55)); }
  .pic.off { opacity: 0; }
  .dark .pic.off { opacity: 1; }
  .dim .pic { filter: brightness(.42) saturate(.6) drop-shadow(0 24px 30px rgba(0,0,0,.55)); }
  .glow { position: absolute; left: 10%; right: 10%; bottom: -6%; height: 26%; background: radial-gradient(50% 50% at 50% 50%, rgba(255,175,80,.32), transparent 70%); filter: blur(10px); pointer-events: none; transition: opacity 1.2s; }
  .dark .glow { opacity: 0; }
  .empty { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; gap: 8px; color: var(--muted); text-align: center; padding: 20px; }

  .pv, .pv-glow, .pv-soft, .pv-fill, .pv-shine { position: absolute; inset: 0; pointer-events: none; }
  .pv { mix-blend-mode: screen; }
  /* The glow is drawn (and blurred) once; only its opacity pulses. */
  .pv-glow { animation: pvpulse 2.6s ease-in-out infinite; }
  .pv-soft { filter: blur(6px); }
  .pv-fill { background: rgb(255 184 77 / calc(.12 + var(--pv) * .3)); }
  .pv-band { position: absolute; background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,246,216,.9), rgba(255,255,255,0)); animation: pvsweep var(--dur) linear infinite; will-change: transform; }
  @keyframes pvpulse { 50% { opacity: .5; } }
  /* The band travels 1.7× the panels' width, which is 1.7 / 0.3 of its own. */
  @keyframes pvsweep { from { transform: translateX(0); } to { transform: translateX(566.7%); } }
  .corner { position: absolute; width: 16px; height: 16px; margin: -8px 0 0 -8px; border-radius: 50%; background: #ffb84d; border: 2px solid #fff; cursor: grab; touch-action: none; z-index: 5; }

  .pin { position: absolute; width: 0; height: 0; z-index: 5; }
  .dot { position: absolute; left: -5px; top: -5px; width: 10px; height: 10px; border-radius: 50%; background: var(--c); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c) 30%, transparent), 0 0 14px var(--c); }
  .dot::after { content: ''; position: absolute; inset: -6px; border-radius: 50%; border: 1px solid var(--c); animation: ping 2.4s ease-out infinite; }
  @keyframes ping { from { transform: scale(.5); opacity: .9; } to { transform: scale(2.2); opacity: 0; } }
  .stem { position: absolute; }
  .l .stem, .r .stem { top: -1px; height: 2px; width: 34px; }
  .l .stem { right: 6px; background: linear-gradient(270deg, color-mix(in srgb, var(--c) 80%, transparent), transparent); }
  .r .stem { left: 6px; background: linear-gradient(90deg, color-mix(in srgb, var(--c) 80%, transparent), transparent); }
  .t .stem { left: -1px; width: 2px; height: 34px; bottom: 6px; background: linear-gradient(0deg, color-mix(in srgb, var(--c) 80%, transparent), transparent); }
  .label { position: absolute; display: flex; gap: 10px; align-items: center; padding: 8px 13px 8px 9px; border-radius: 14px; white-space: nowrap; text-align: left; font: inherit; color: var(--text);
    border: 1px solid var(--edge); background: linear-gradient(180deg, rgba(30,44,70,.78), rgba(10,16,28,.82));
    box-shadow: inset 0 1px 0 rgba(255,255,255,.12), 0 14px 30px -10px rgba(0,0,0,.85), 0 0 24px -8px color-mix(in srgb, var(--c) 60%, transparent);
    backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); cursor: pointer; touch-action: manipulation; }
  .l .label { right: 40px; top: 0; transform: translateY(-50%); }
  .r .label { left: 40px; top: 0; transform: translateY(-50%); }
  .t .label { left: 0; bottom: 40px; transform: translateX(-50%); }
  .ic { width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; flex: none; color: var(--c);
    background: radial-gradient(circle at 50% 30%, color-mix(in srgb, var(--c) 32%, transparent), color-mix(in srgb, var(--c) 8%, transparent));
    box-shadow: inset 0 1px 0 rgba(255,255,255,.18), 0 0 16px -4px var(--c); }
  .ic :global(svg) { filter: drop-shadow(0 0 4px var(--c)); }
  .txt { display: flex; flex-direction: column; line-height: 1.2; }
  .n { font-size: 11px; color: var(--muted); }
  .v { font-family: var(--display); font-size: 18px; font-weight: 400; font-variant-numeric: tabular-nums; }
  .s { font-size: 11px; color: var(--muted); display: flex; gap: 5px; align-items: center; }
  .sd { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 8px var(--ok); }
  .sd.warn { background: var(--sun); box-shadow: 0 0 8px var(--sun); }
  .sd.bad { background: var(--hot); box-shadow: 0 0 8px var(--hot); }
  .off { --c: #6b7a90 !important; }
  .off .dot::after { animation: none; opacity: 0; }
  .placing .label { cursor: grab; outline: 2px dashed #ffb84d; outline-offset: 2px; touch-action: none; }

  @media (prefers-reduced-motion: reduce) { .dot::after, .pv-glow, .pv-band, .stars.twinkle, .bloom { animation: none; } }
</style>
