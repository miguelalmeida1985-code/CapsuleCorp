<script module>
  export const meta = {
    type: 'cameras', name: 'Cameras', icon: 'mdi:cctv', category: 'Media',
    size: { w: 520, h: 440 }, tap: 'none',
    defaults: { entities: [], mode: 'live', bell: '', big: true, bell_popup: true, bell_secs: 60 },
    fields: [
      { key: 'entities', label: 'Cameras (the first one is the big one)', type: 'entities', domain: ['camera', 'image'] },
      { key: 'big', label: 'First camera big', type: 'bool' },
      { key: 'mode', label: 'Mode', type: 'select', options: [{ value: 'live', label: 'Live' }, { value: 'snapshot', label: 'Snapshot' }, { value: 'video', label: 'Video stream' }] },
      { key: 'bell', label: 'Doorbell pressed (binary sensor; its camera glows)', type: 'entity', domain: 'binary_sensor' },
      { key: 'bell_camera', label: 'Doorbell camera', type: 'entity', domain: 'camera' },
      { key: 'bell_popup', label: 'Ring opens the doorbell camera full screen on every screen', type: 'bool' },
      { key: 'bell_secs', label: 'Close it after (seconds)', type: 'number' },
    ],
  };
</script>

<script>
  // The cameras, live, side by side: the first one big and the rest two to a
  // row, each with its name, its battery or "live", and a full-screen button.
  // Every screen showing a camera shares one stream from Home Assistant (server camStream).
  import Icon from '../components/Icon.svelte';
  import Camera from './Camera.svelte';
  import { ent } from '../lib/tpl.js';
  import { name, isActive } from '../lib/entity.js';
  import { states } from '../lib/ha.svelte.js';
  import { say } from '../lib/i18n.svelte.js';
  import { app } from '../lib/config.svelte.js';
  let { props, w = 0, h = 0 } = $props();

  const GAP = 10;
  const ids = $derived((props.entities || []).filter(Boolean));
  const big = $derived(props.big !== false && ids.length > 1);
  const rows = $derived((big ? 1 : 0) + Math.ceil((ids.length - (big ? 1 : 0)) / 2));
  const cols = $derived(ids.length === 1 ? 1 : 2);
  // Tile sizes: the big row twice as tall as the others, all filling the card.
  const unit = $derived((h - GAP * Math.max(0, rows - 1)) / Math.max(1, rows + (big ? 1 : 0)));
  const tile = (i) => {
    const wide = (big && i === 0) || cols === 1;
    return { w: wide ? w : (w - GAP) / 2, h: big && i === 0 ? unit * 2 : unit };
  };
  // Battery from the camera's own sensor (Tapo, Blink, Reolink… name it <camera>_battery).
  const battery = (id) => {
    const o = id.split('.')[1];
    for (const k of [`sensor.${o}_battery`, `sensor.${o}_battery_level`, `sensor.${o}_battery_percentage`]) if (states.has(k)) return states.get(k).state;
    return null;
  };
  const ringing = $derived(!!props.bell && isActive(ent(props.bell)));
  let now = $state(new Date());
  $effect(() => { const i = setInterval(() => (now = new Date()), 1000); return () => clearInterval(i); });
  const stamp = $derived(now.toLocaleString([], { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).replace(',', ''));
</script>

<div class="cams" style="grid-template-columns:repeat({cols},minmax(0,1fr));grid-template-rows:{big ? '2fr ' : ''}repeat({rows - (big ? 1 : 0)},1fr)">
  {#each ids as id, i (id)}
    {@const e = ent(id)}
    {@const b = battery(id)}
    {@const sz = tile(i)}
    <div class="cam" class:big={big && i === 0} class:bell={ringing && (props.bell_camera ? props.bell_camera === id : i === 0)}
      role="button" tabindex="0" data-stop onclick={() => (app.popup = { camera: id, name: name(e) })} onkeydown={(ev) => ev.key === 'Enter' && (app.popup = { camera: id, name: name(e) })}>
      <Camera props={{ entity: id, mode: props.mode || 'live', fit: 'crop', show_name: false }} w={sz.w} h={sz.h} />
      <div class="shade"></div>
      <div class="tag">
        <span class="ic"><Icon icon="mdi:video" size="14px" /></span>
        <span class="tx">{name(e, '') || id}
          {#if b != null && b !== 'unavailable'}<small><span class="dot" class:warn={Number(b) < 30}></span>{say('Battery', 'Bateria')} {Math.round(Number(b))}%</small>
          {:else}<small><span class="live"></span>{say('Live', 'Ao vivo')}</small>{/if}
        </span>
      </div>
      <span class="fs"><Icon icon="mdi:fullscreen" size="15px" /></span>
      <span class="ts">{stamp}</span>
    </div>
  {/each}
</div>

<style>
  .cams { position: absolute; inset: 0; display: grid; gap: 10px; }
  .cam { position: relative; border-radius: 14px; overflow: hidden; background: #0b1220; cursor: pointer; min-height: 0;
    box-shadow: inset 0 0 0 1px var(--edge), 0 12px 24px -12px rgba(0,0,0,.8); isolation: isolate; }
  .cam.big { grid-column: 1 / -1; }
  .cam :global(.cam) { border-radius: 0; }
  .cam :global(img) { filter: saturate(.85) contrast(1.08); }
  /* Darker top and bottom for the labels, and faint scan lines. */
  .shade { position: absolute; inset: 0; z-index: 1; pointer-events: none;
    background: linear-gradient(180deg, rgba(0,0,0,.45), transparent 35%, transparent 70%, rgba(0,0,0,.45)), repeating-linear-gradient(0deg, rgba(255,255,255,.025) 0 1px, transparent 1px 3px); }
  .tag { position: absolute; left: 10px; top: 9px; z-index: 2; font-size: 13px; line-height: 1.25; font-weight: 500; display: flex; gap: 8px; align-items: center; text-shadow: 0 1px 4px rgba(0,0,0,.8); }
  .tx { display: flex; flex-direction: column; }
  .ic, .fs { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; background: rgba(8,14,26,.6); border: 1px solid var(--edge); }
  .fs { position: absolute; right: 9px; top: 9px; z-index: 2; }
  small { display: flex; gap: 5px; align-items: center; color: #c9d4e4; font-size: 10.5px; font-weight: 400; }
  .live { width: 7px; height: 7px; border-radius: 50%; background: var(--hot); box-shadow: 0 0 8px var(--hot); animation: blink 1.6s infinite; }
  .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 8px var(--ok); }
  .dot.warn { background: var(--sun); box-shadow: 0 0 8px var(--sun); }
  @keyframes blink { 50% { opacity: .35; } }
  .ts { position: absolute; right: 10px; bottom: 7px; z-index: 2; font: 10px ui-monospace, Menlo, monospace; color: rgba(255,255,255,.7); text-shadow: 0 1px 3px #000; }
  .bell { box-shadow: inset 0 0 0 2px rgba(255,184,77,.7), 0 0 28px -6px var(--sun); }
  @media (prefers-reduced-motion: reduce) { .live { animation: none; } }
</style>
