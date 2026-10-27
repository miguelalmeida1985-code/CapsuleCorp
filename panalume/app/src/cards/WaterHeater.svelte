<script module>
  import { states } from '../lib/ha.svelte.js';
  import { find } from '../lib/octopus.js';
  // Ariston (Velis, Lydos…) names its extras "<device>_<thing>" beside water_heater.<device>.
  function autofill() {
    const ids = [...states.keys()].filter((k) => k.startsWith('water_heater.'));
    const entity = ids.find((k) => /ariston|velis|lydos/.test(k)) || ids.find((k) => !/octopus_energy_heat_pump/.test(k)) || '';
    if (!entity) return {};
    const p = entity.slice('water_heater.'.length);
    const id = (k) => (states.has(k) ? k : '');
    return {
      entity,
      showers: id(`sensor.${p}_average_showers`) || find(/^sensor\.[a-z0-9_]*(average_showers|av_shower)$/),
      heating: id(`binary_sensor.${p}_is_heating`) || id(`binary_sensor.${p}_heating`),
      eco: id(`switch.${p}_eco_mode`) || id(`switch.${p}_eco`),
      power: id(`switch.${p}_power`),
      legionella: id(`binary_sensor.${p}_anti_legionella_cycle`) || id(`switch.${p}_anti_legionella`),
    };
  }
  export const meta = {
    type: 'waterheater', name: 'Water heater', icon: 'mdi:water-boiler', category: 'Controls',
    size: { w: 400, h: 250 }, tap: 'none', hold: 'none',
    defaults: { scale_min: 25 },
    autofill,
    sections: [{ key: 'tank', label: 'Tank' }, { key: 'target', label: 'Target − / +' }, { key: 'modes', label: 'Modes' }, { key: 'showers', label: 'Showers', section: 'showers' }, { key: 'eco', label: 'Eco', section: 'eco' }, { key: 'power', label: 'On / off', section: 'power' }],
    fields: [
      { key: 'title', label: 'Title', type: 'text' },
      { key: 'entity', label: 'Water heater', type: 'entity', domain: 'water_heater' },
      { key: 'heating', label: 'Heating now (blank = below target)', type: 'entity', domain: 'binary_sensor' },
      { key: 'showers', label: 'Showers available', type: 'entity', domain: 'sensor' },
      { key: 'eco', label: 'Eco mode switch', type: 'entity', domain: 'switch' },
      { key: 'power', label: 'Power switch (blank = the water heater itself)', type: 'entity', domain: 'switch' },
      { key: 'legionella', label: 'Anti-legionella cycle', type: 'entity' },
      { key: 'scale_min', label: 'Empty tank at (°C, for the fill)', type: 'number', section: 'tank' },
    ],
  };
</script>

<script>
  // Water heater: a glass tank filled to the water temperature, glowing and
  // bubbling while it heats, with the target, modes and Ariston's extras.
  import Icon from '../components/Icon.svelte';
  import { t, ent } from '../lib/tpl.js';
  import { callService } from '../lib/ha.svelte.js';
  import { stateWord } from '../lib/i18n.svelte.js';
  import { formatNumber, isActive, toggle } from '../lib/entity.js';
  let { props, w = 400 } = $props();
  const show = (k) => !props.hide?.[k];
  const e = $derived(ent(props.entity));
  const a = $derived(e?.attributes || {});
  const cur = $derived(a.current_temperature != null ? Number(a.current_temperature) : null);
  const step = $derived(Number(a.target_temp_step) || 1);
  const lo = $derived(Number(a.min_temp) || 40), hi = $derived(Number(a.max_temp) || 75);

  // − / + are sent once you stop tapping.
  let pending = $state(null), timer;
  const target = $derived(pending ?? (a.temperature != null ? Number(a.temperature) : null));
  function nudge(d) {
    pending = Math.min(hi, Math.max(lo, Math.round((target + d * step) / step) * step));
    clearTimeout(timer);
    timer = setTimeout(() => callService('water_heater', 'set_temperature', { temperature: pending }, { entity_id: e.entity_id }).finally(() => setTimeout(() => (pending = null), 1500)), 900);
  }

  const pw = $derived(ent(props.power));
  const on = $derived(pw ? pw.state === 'on' : !!e && e.state !== 'off' && e.state !== 'unavailable');
  const heating = $derived(on && (props.heating ? isActive(ent(props.heating)) : cur != null && target != null && cur < target - 0.5));
  const empty = $derived(Number(props.scale_min) || 25);
  const fill = $derived(cur == null ? 0 : Math.max(6, Math.min(94, ((cur - empty) / (hi - empty)) * 94)));
  const modes = $derived((a.operation_list || []).filter((m) => m !== 'off'));
  const mode = $derived(a.operation_mode || e?.state);
  const label = (m) => stateWord('water_heater', null, m) || String(m).replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
  const setMode = (m) => callService('water_heater', 'set_operation_mode', { operation_mode: m }, { entity_id: e.entity_id });
  const showers = $derived(ent(props.showers));
  const eco = $derived(ent(props.eco));
  const leg = $derived(isActive(ent(props.legionella)));
  const narrow = $derived(w < 340);
</script>

<div class="wh" class:off={!on} class:heating>
  {#if show('tank')}
    <div class="glow"><div class="tank">
      <div class="fill" style:height="{fill}%"></div>
      {#if heating}<span class="bubble" style:left="30%"></span><span class="bubble" style:left="56%" style:animation-delay="1.1s"></span><span class="bubble" style:left="43%" style:animation-delay="2s"></span>{/if}
    </div></div>
  {/if}
  <div class="info">
    <div class="head">
      <div class="title">{t(props.title) || a.friendly_name || 'Water heater'}</div>
      {#if show('power')}<button class="pw" class:on aria-label={on ? 'Turn off' : 'Turn on'} onclick={() => toggle(pw || e)}><Icon icon="mdi:power" size="1.1em" /></button>{/if}
    </div>
    <div class="now"><b>{cur != null ? formatNumber(cur, 0) : '—'}</b><span>°C</span>
      <em>{!e ? 'Not found' : !on ? 'Off' : leg ? 'Anti-legionella' : heating ? 'Heating' : 'Ready'}</em>
    </div>
    {#if show('target') && target != null}
      <div class="target" data-stop>
        <button class="nb" aria-label="Cooler" disabled={!on} onclick={() => nudge(-1)}><Icon icon="mdi:minus" size="1.1em" /></button>
        <span><small>target</small> {formatNumber(target, 0)}°</span>
        <button class="nb" aria-label="Warmer" disabled={!on} onclick={() => nudge(1)}><Icon icon="mdi:plus" size="1.1em" /></button>
      </div>
    {/if}
    <div class="chips" data-stop>
      {#if showers && show('showers')}<span class="chip"><Icon icon="mdi:shower-head" size="1em" />{formatNumber(showers.state, 0)}{narrow ? '' : ' showers'}</span>{/if}
      {#if eco && show('eco')}<button class="chip" class:sel={eco.state === 'on'} onclick={() => toggle(eco)}><Icon icon="mdi:leaf" size="1em" />Eco</button>{/if}
      {#if show('modes')}{#each modes as m (m)}<button class="chip" class:sel={m === mode} onclick={() => setMode(m)}>{label(m)}</button>{/each}{/if}
    </div>
  </div>
</div>

<style>
  .wh { height: 100%; display: flex; gap: 18px; align-items: center; }
  .glow { height: 100%; max-height: 200px; aspect-ratio: 62 / 136; flex: none; filter: drop-shadow(0 0 18px rgba(255, 140, 40, .3)); transition: filter .6s; }
  .off .glow { filter: none; }
  .tank { width: 100%; height: 100%; border-radius: 50% / 10%; position: relative; overflow: hidden;
    background: linear-gradient(90deg, #1b2638 0%, #4a5d79 28%, #8aa0bd 40%, #4a5d79 55%, #1b2638 100%);
    box-shadow: 0 16px 24px -10px rgba(0,0,0,.9), inset 0 0 0 1px rgba(255,255,255,.08); }
  .fill { position: absolute; left: 0; right: 0; bottom: 0; transition: height .8s cubic-bezier(.2,.7,.2,1);
    background: linear-gradient(90deg, #8a3a10 0%, #ff8a2a 30%, #ffd27a 42%, #ff8a2a 58%, #8a3a10 100%); box-shadow: 0 0 30px rgba(255,140,40,.6); }
  .fill::before { content: ''; position: absolute; left: 0; right: 0; top: -6px; height: 12px; border-radius: 50%; background: radial-gradient(ellipse at center, #ffe3a8, #ff9a3a 70%); }
  .off .fill { filter: saturate(.25) brightness(.6); box-shadow: none; }
  .tank::after { content: ''; position: absolute; left: 14%; top: 8%; width: 12%; height: 84%; border-radius: 6px; background: linear-gradient(180deg, rgba(255,255,255,.45), rgba(255,255,255,.05)); pointer-events: none; }
  .bubble { position: absolute; bottom: 4px; width: 5px; height: 5px; border-radius: 50%; background: rgba(255,240,210,.75); animation: rise 3s linear infinite; }
  @keyframes rise { from { transform: translateY(0); opacity: 0; } 15% { opacity: 1; } to { transform: translateY(-110px); opacity: 0; } }
  .info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
  .head { display: flex; align-items: center; gap: 8px; }
  .title { flex: 1; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pw { border: 0; width: 2.2em; height: 2.2em; border-radius: 50%; display: grid; place-items: center; background: rgba(255,255,255,.07); color: var(--muted); }
  .pw.on { background: rgba(255,138,76,.22); color: #ff9a4c; }
  .now { display: flex; align-items: baseline; gap: 4px; }
  .now b { font-size: 2.6em; line-height: 1; font-weight: 600; }
  .heating .now b { color: #ffb36b; text-shadow: 0 0 18px rgba(255,140,40,.45); }
  .now span { color: var(--muted); }
  .now em { margin-left: auto; font-style: normal; font-size: .8em; color: var(--muted); }
  .heating .now em { color: #ff9a4c; }
  .target { display: flex; align-items: center; gap: 6px; }
  .target span { flex: 1; text-align: center; font-weight: 600; }
  .target small { color: var(--muted); font-weight: 400; }
  .nb { border: 0; width: 2.4em; height: 2.2em; border-radius: 12px; display: grid; place-items: center; background: rgba(255,255,255,.07); color: inherit; }
  .nb:disabled { opacity: .4; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; }
  .chip { border: 0; border-radius: 999px; padding: 5px 10px; font-size: .8em; display: inline-flex; align-items: center; gap: 5px; background: rgba(255,255,255,.07); color: inherit; }
  .chip.sel { background: rgba(255,138,76,.25); color: #ffc08f; }
</style>
