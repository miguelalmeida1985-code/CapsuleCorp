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
      { key: 'title', label: 'Title (- hides it)', type: 'text' },
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
  import CardTitle from '../components/CardTitle.svelte';
  import { stateWord, say } from '../lib/i18n.svelte.js';
  import { formatNumber, isActive, toggle } from '../lib/entity.js';
  let { props } = $props();
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
</script>

<div class="wh" class:off={!on} class:heating>
  {#if props.title !== '-'}<CardTitle icon="mdi:water" color="var(--hot)" title={t(props.title) || say('Hot water', 'Águas quentes')} />{/if}
  <div class="row">
    {#if show('tank')}
      <div class="glow"><div class="tank">
        <div class="fill" style:height="{fill}%"></div>
        {#if heating}<span class="bubble" style:left="30%"></span><span class="bubble" style:left="56%" style:animation-delay="1.1s"></span><span class="bubble" style:left="43%" style:animation-delay="2s"></span>{/if}
      </div></div>
    {/if}
    <div class="info">
      <div class="head">
        <div class="now num">{cur != null ? formatNumber(cur, 0) : '—'}°C</div>
        {#if show('power')}<button class="pw" class:on data-stop aria-label={on ? say('Turn off', 'Desligar') : say('Turn on', 'Ligar')} onclick={() => toggle(pw || e)}><Icon icon="mdi:power" size="16px" /></button>{/if}
      </div>
      {#if show('target') && target != null}
        <div class="kv" data-stop><span>{say('Target', 'Alvo')}</span>
          <span class="tg"><button class="step" aria-label="−" disabled={!on} onclick={() => nudge(-1)}>−</button><b class="num">{formatNumber(target, 0)}°</b><button class="step" aria-label="+" disabled={!on} onclick={() => nudge(1)}>+</button></span>
        </div>
      {/if}
      <div class="kv"><span>{say('State', 'Estado')}</span><span class="st">{!e ? say('Not found', 'Não encontrado') : !on ? say('Off', 'Desligado') : leg ? 'Anti-legionella' : heating ? say('Heating', 'A aquecer') : say('Ready', 'Pronto')}</span></div>
      {#if showers && show('showers')}<div class="kv"><span>{say('Showers (≈)', 'Duches (≈)')}</span><span>{formatNumber(showers.state, 0)}</span></div>{/if}
      {#if (show('modes') && modes.length) || (eco && show('eco'))}
        <div class="chips" data-stop>
          {#if eco && show('eco')}<button class="chip" class:sel={eco.state === 'on'} onclick={() => toggle(eco)}><Icon icon="mdi:leaf" size="1em" />Eco</button>{/if}
          {#if show('modes')}{#each modes as m (m)}<button class="chip" class:sel={m === mode} onclick={() => setMode(m)}>{label(m)}</button>{/each}{/if}
        </div>
      {/if}
    </div>
  </div>
</div>

<style>
  .wh { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .row { flex: 1; min-height: 0; display: flex; gap: 16px; align-items: center; }
  .glow { height: 100%; max-height: 150px; aspect-ratio: 62 / 136; flex: none; filter: drop-shadow(0 0 18px rgba(255, 140, 40, .35)); transition: filter .6s; }
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
  .info { flex: 1; min-width: 0; display: grid; gap: 8px; align-content: center; }
  .head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  .now { font-size: 34px; font-weight: 200; line-height: 1; color: var(--sun); text-shadow: 0 0 18px rgba(255,184,77,.55); white-space: nowrap; }
  .off .now { color: var(--muted); text-shadow: none; }
  .pw { width: 36px; height: 34px; flex: none; border-radius: 11px; border: 1px solid var(--edge); color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.02)); display: grid; place-items: center; padding: 0; transition: box-shadow .25s, background .25s; }
  .pw.on { background: radial-gradient(circle at 50% 30%, #ff9a8c, var(--hot)); border-color: transparent; color: #fff; box-shadow: 0 0 22px -2px var(--hot), inset 0 1px 0 rgba(255,255,255,.5); }
  .kv { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 12px; white-space: nowrap; }
  .kv > span:first-child { color: var(--muted); }
  .heating .st { color: var(--sun); }
  .tg { display: flex; align-items: center; gap: 6px; }
  .tg b { font-weight: 400; font-size: 13px; min-width: 2.4em; text-align: center; }
  .step { width: 30px; height: 30px; border-radius: 10px; border: 1px solid var(--edge); color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.08), rgba(255,255,255,.02)); font-size: 16px; line-height: 1; box-shadow: inset 0 1px 0 var(--edge-hi); padding: 0; }
  .step:disabled { opacity: .4; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; }
  .chip { border: 1px solid var(--edge); border-radius: 11px; padding: 5px 9px; font-size: 11px; display: inline-flex; align-items: center; gap: 5px; color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.02)); }
  .chip.sel { border-color: rgba(255,138,76,.5); background: rgba(255,138,76,.2); color: #ffc08f; }
  @media (prefers-reduced-motion: reduce) { .bubble { animation: none; } }
</style>
