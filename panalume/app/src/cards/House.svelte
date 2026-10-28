<script module>
  export const meta = {
    type: 'house', name: 'House', icon: 'mdi:home-lightbulb-outline', category: 'Info',
    size: { w: 900, h: 560 }, tap: 'none',
    defaults: { image: '', image_off: '', lights: [], pins: [], pins_pos: {}, solar: '', solar_max: 5, solar_area: '', place_pins: false },
    fields: [
      { key: 'image', label: 'Picture of the house (URL, e.g. /local/house.webp)', type: 'text', hint: 'Put the file in Home Assistant\'s www folder; /local/ serves it. A cut-out on a transparent background looks best.' },
      { key: 'image_off', label: 'Same picture with the outdoor lights off (optional)', type: 'text', hint: 'Same framing. Without it the picture is just dimmed when the lights are off.' },
      { key: 'lights', label: 'Outdoor lights (the picture lights up when any is on)', type: 'entities' },
      { key: 'lit', label: 'Lit when (template, overrides the lights)', type: 'text' },
      { key: 'pins', label: 'Labels on the house', type: 'entities', hint: 'Each entity gets a glass label pinned to the house. Tick "Move the labels" below to place them.' },
      { key: 'place_pins', label: 'Move the labels and the solar panel corners (while editing)', type: 'bool' },
      { key: 'solar', label: 'Solar power sensor (W or kW)', type: 'entity', domain: 'sensor' },
      { key: 'solar_max', label: 'Solar peak (kW)', type: 'number', placeholder: '5' },
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
  import { name, stateText, entityIcon, isActive, toggle, domain } from '../lib/entity.js';
  import { app, changed } from '../lib/config.svelte.js';
  import { haImage } from '../lib/ha.svelte.js';
  let { props, w, h, editing } = $props();

  const src = $derived(haImage(t(props.image)));
  const srcOff = $derived(haImage(t(props.image_off)));
  let ratio = $state(1.68);
  const box = $derived.by(() => {
    const bw = Math.min(w, h * ratio);
    return { w: bw, h: bw / ratio, x: (w - bw) / 2, y: h - bw / ratio };
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
  const uid = $props.id();
  const area = $derived.by(() => {
    const pts = String(props.solar_area || '').trim().split(/\s+/).map((p) => p.split(',').map(Number)).filter((p) => p.length === 2 && p.every(Number.isFinite));
    return pts.length >= 3 ? pts : placing && props.solar ? [[40, 20], [60, 18], [60, 28], [40, 30]] : [];
  });
  const bbox = $derived(area.length ? { x0: Math.min(...area.map((p) => p[0])), x1: Math.max(...area.map((p) => p[0])), y0: Math.min(...area.map((p) => p[1])), y1: Math.max(...area.map((p) => p[1])) } : null);

  const COLOR = { light: '#ffb84d', switch: '#ffb84d', climate: '#5ccbff', water_heater: '#ff5a4a', camera: '#cfe0f5', sensor: '#ffb84d', binary_sensor: '#cfe0f5' };
  const pins = $derived((props.pins || []).map((id, i) => {
    const e = ent(id);
    const d = domain(id);
    const pos = props.pins_pos?.[id] || [15 + (i % 4) * 23, 20 + Math.floor(i / 4) * 22];
    const value = d === 'water_heater' ? (e?.attributes.current_temperature != null ? `${e.attributes.current_temperature}°` : stateText(e)) : stateText(e);
    const off = !e || ['light', 'switch', 'climate', 'water_heater', 'fan', 'input_boolean'].includes(d) ? !isActive(e) : e.state === 'unavailable';
    return { id, e, d, x: pos[0], y: pos[1], side: pos[0] > 50 ? 'r' : 'l', value, off, color: COLOR[d] || '#cfe0f5' };
  }));

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

<div class="house" class:dark={!lit} class:dim={!lit && !srcOff} class:placing>
  <div class="stage" bind:this={stage} style="left:{box.x}px;top:{box.y}px;width:{box.w}px;height:{box.h}px" onpointermove={move} onpointerup={drop} onpointercancel={drop} role="presentation">
    {#if src}
      <img class="pic" {src} alt="" onload={(e) => e.currentTarget.naturalWidth && (ratio = e.currentTarget.naturalWidth / e.currentTarget.naturalHeight)} />
      {#if srcOff}<img class="pic off" src={srcOff} alt="" />{/if}
    {:else}
      <div class="empty"><Icon icon="mdi:home-outline" size="2em" /><span>Add a picture of the house in the card's settings</span></div>
    {/if}
    <div class="glow"></div>

    {#if area.length && (kw > 0.05 || placing)}
      <svg class="pv" viewBox="0 0 100 100" preserveAspectRatio="none" style="--pv:{pv};--dur:{(4.2 - pv * 2.6).toFixed(1)}s" aria-hidden="true">
        <defs>
          <clipPath id="pv-{uid}"><polygon points={area.map((p) => p.join(',')).join(' ')} /></clipPath>
          <linearGradient id="pvb-{uid}" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity="0" /><stop offset=".5" stop-color="#fff6d8" stop-opacity=".9" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></linearGradient>
        </defs>
        <polygon class="pv-glow" points={area.map((p) => p.join(',')).join(' ')} />
        <g clip-path="url(#pv-{uid})">
          <rect class="pv-band" x={bbox.x0 - (bbox.x1 - bbox.x0) * 0.35} y={bbox.y0 - 2} width={(bbox.x1 - bbox.x0) * 0.3} height={bbox.y1 - bbox.y0 + 4} fill="url(#pvb-{uid})" style="--run:{(bbox.x1 - bbox.x0) * 1.7}px" />
        </g>
      </svg>
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
          <span class="ic"><Icon icon={entityIcon(p.e)} size="1.1em" /></span>
          <span class="txt"><span class="n">{name(p.e) || p.id}</span><span class="v">{p.value}</span></span>
        </button>
      </div>
    {/each}
  </div>
</div>

<style>
  .house { position: absolute; inset: 0; overflow: hidden; border-radius: inherit; }
  .stage { position: absolute; }
  .pic { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: fill; transition: opacity 1.2s ease, filter 1.2s ease; filter: drop-shadow(0 24px 30px rgba(0,0,0,.55)); }
  .pic.off { opacity: 0; }
  .dark .pic.off { opacity: 1; }
  .dim .pic { filter: brightness(.42) saturate(.6) drop-shadow(0 24px 30px rgba(0,0,0,.55)); }
  .glow { position: absolute; left: 10%; right: 10%; bottom: -6%; height: 26%; background: radial-gradient(50% 50% at 50% 50%, rgba(255,175,80,.32), transparent 70%); filter: blur(10px); pointer-events: none; transition: opacity 1.2s; }
  .dark .glow { opacity: 0; }
  .empty { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; gap: 8px; color: var(--muted); text-align: center; padding: 20px; }

  .pv { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; mix-blend-mode: screen; }
  .pv-glow { fill: #ffb84d; opacity: calc(.12 + var(--pv) * .3); filter: blur(1.2px); animation: pvpulse 2.6s ease-in-out infinite; }
  .pv-band { animation: pvsweep var(--dur) linear infinite; }
  @keyframes pvpulse { 50% { opacity: calc(.06 + var(--pv) * .16); } }
  @keyframes pvsweep { from { transform: translateX(0); } to { transform: translateX(var(--run)); } }
  .corner { position: absolute; width: 16px; height: 16px; margin: -8px 0 0 -8px; border-radius: 50%; background: #ffb84d; border: 2px solid #fff; cursor: grab; touch-action: none; z-index: 5; }

  .pin { position: absolute; width: 0; height: 0; z-index: 3; }
  .dot { position: absolute; left: -5px; top: -5px; width: 10px; height: 10px; border-radius: 50%; background: var(--c); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c) 30%, transparent), 0 0 14px var(--c); }
  .dot::after { content: ''; position: absolute; inset: -6px; border-radius: 50%; border: 1px solid var(--c); animation: ping 2.4s ease-out infinite; }
  @keyframes ping { from { transform: scale(.5); opacity: .9; } to { transform: scale(2.2); opacity: 0; } }
  .stem { position: absolute; top: -1px; height: 2px; width: 28px; }
  .l .stem { right: 6px; background: linear-gradient(270deg, color-mix(in srgb, var(--c) 80%, transparent), transparent); }
  .r .stem { left: 6px; background: linear-gradient(90deg, color-mix(in srgb, var(--c) 80%, transparent), transparent); }
  .label { position: absolute; top: 0; transform: translateY(-50%); display: flex; gap: 9px; align-items: center; padding: 7px 12px 7px 8px; border-radius: 13px; white-space: nowrap; text-align: left; font: inherit; color: var(--text);
    border: 1px solid rgba(150,180,230,.16); background: linear-gradient(180deg, rgba(30,44,70,.78), rgba(10,16,28,.82));
    box-shadow: inset 0 1px 0 rgba(255,255,255,.12), 0 14px 30px -10px rgba(0,0,0,.85), 0 0 24px -8px color-mix(in srgb, var(--c) 60%, transparent);
    backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); cursor: pointer; touch-action: manipulation; }
  .l .label { right: 34px; }
  .r .label { left: 34px; }
  .ic { width: 1.9em; height: 1.9em; border-radius: 9px; display: grid; place-items: center; color: var(--c); background: color-mix(in srgb, var(--c) 18%, transparent); box-shadow: 0 0 14px -4px var(--c); }
  .txt { display: flex; flex-direction: column; line-height: 1.2; }
  .n { font-size: .72em; color: var(--muted); }
  .v { font-size: 1.05em; font-weight: 500; font-variant-numeric: tabular-nums; }
  .off { --c: #6b7a90 !important; }
  .off .dot::after { animation: none; opacity: 0; }
  .placing .label { cursor: grab; outline: 2px dashed #ffb84d; outline-offset: 2px; touch-action: none; }

  @media (prefers-reduced-motion: reduce) { .dot::after, .pv-glow, .pv-band { animation: none; } }
</style>
