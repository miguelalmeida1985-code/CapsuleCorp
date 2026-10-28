<script module>
  export const meta = {
    type: 'climates', name: 'Air conditioners', icon: 'mdi:snowflake', category: 'Controls',
    size: { w: 380, h: 300 }, tap: 'none',
    defaults: { title: 'Climatização', icon: 'mdi:snowflake', entities: [], lines: '' },
    fields: [
      { key: 'title', label: 'Title', type: 'text' },
      { key: 'icon', label: 'Title icon', type: 'icon' },
      { key: 'entities', label: 'Air conditioners / thermostats', type: 'entities', domain: 'climate' },
      { key: 'lines', label: 'Room labels (one per line: entity_id: text)', type: 'textarea', hint: 'e.g. climate.sensibo: Quarto meninos · Sensibo. Without one, the name shows.' },
    ],
  };
</script>

<script>
  // Air conditioners side by side: the room's temperature (glowing while on),
  // the target with − / +, the mode and on / off.
  import Icon from '../components/Icon.svelte';
  import CardTitle from '../components/CardTitle.svelte';
  import { t, ent } from '../lib/tpl.js';
  import { name, formatNumber } from '../lib/entity.js';
  import { callService } from '../lib/ha.svelte.js';
  import { stateWord, say } from '../lib/i18n.svelte.js';
  import { app } from '../lib/config.svelte.js';
  let { props } = $props();

  const MODE_ICON = { cool: 'mdi:snowflake', heat: 'mdi:fire', dry: 'mdi:water-percent', fan_only: 'mdi:fan', auto: 'mdi:thermostat-auto', heat_cool: 'mdi:thermostat-auto', off: 'mdi:snowflake' };
  const MODE_COLOR = { heat: 'var(--hot)', dry: 'var(--sun)' };
  const MODE_WORD = { cool: ['Cool', 'Frio'], heat: ['Heat', 'Calor'], dry: ['Dry', 'Desumidificar'], fan_only: ['Fan', 'Ventilar'], auto: ['Auto', 'Auto'], heat_cool: ['Auto', 'Auto'], off: ['Off', 'Desligado'] };
  const modeWord = (m) => stateWord('climate', null, m) || (MODE_WORD[m] ? say(...MODE_WORD[m]) : m);
  const lines = $derived(Object.fromEntries(String(props.lines || '').split('\n').map((l) => l.match(/^\s*([a-z_]+\.[a-z0-9_]+)\s*:\s*(.+)$/)).filter(Boolean).map((m) => [m[1], m[2]])));

  // Targets being changed: shown straight away, sent once the taps stop.
  let pending = $state({});
  const timers = {};
  function step(e, d) {
    const id = e.entity_id;
    const a = e.attributes;
    const s = Number(a.target_temp_step) || (a.temperature % 1 ? 0.5 : 1);
    const cur = pending[id] ?? Number(a.temperature);
    if (!Number.isFinite(cur)) return;
    const next = Math.min(Number(a.max_temp) || 32, Math.max(Number(a.min_temp) || 16, Math.round((cur + d * s) / s) * s));
    pending = { ...pending, [id]: next };
    clearTimeout(timers[id]);
    timers[id] = setTimeout(() => {
      callService('climate', 'set_temperature', { temperature: next }, { entity_id: id });
      setTimeout(() => { const { [id]: _, ...rest } = pending; pending = rest; }, 3000);
    }, 700);
  }
  const power = (e) => callService('climate', e.state === 'off' ? 'turn_on' : 'turn_off', {}, { entity_id: e.entity_id });
</script>

<div class="clm">
  {#if props.title}<CardTitle icon={props.icon} title={t(props.title)} />{/if}
  <div class="wrap" style="grid-template-columns:repeat({Math.max(1, (props.entities || []).length)},minmax(0,1fr))">
    {#each props.entities || [] as id (id)}
      {@const e = ent(id)}
      {@const on = !!e && e.state !== 'off' && e.state !== 'unavailable'}
      {@const target = pending[id] ?? e?.attributes.temperature}
      <div class="ac" class:off={!on} style="--mc:{MODE_COLOR[e?.state] || 'var(--cool)'}">
        <div class="room">{lines[id] ? t(lines[id]) : name(e, '') || id}</div>
        <div class="temp num"><Icon icon={MODE_ICON[e?.state] || 'mdi:thermostat'} size="22px" />{e?.attributes.current_temperature != null ? formatNumber(e.attributes.current_temperature, 0) + '°C' : '—'}</div>
        {#if target != null}
          <div class="set">
            <button class="step" data-stop aria-label="−" onclick={() => e && step(e, -1)}>−</button>
            <span>{say('target', 'alvo')} <b class="num">{formatNumber(target, target % 1 ? 1 : 0)}°</b></span>
            <button class="step" data-stop aria-label="+" onclick={() => e && step(e, 1)}>+</button>
          </div>
        {/if}
        <div class="ctl">
          <button class="chip" data-stop onclick={() => (app.popup = { entity: id })}>{e ? modeWord(on ? e.state : 'auto') : '—'}</button>
          <button class="pwr" class:on data-stop aria-label={say('On / off', 'Ligar / desligar')} onclick={() => e && power(e)}><Icon icon="mdi:power" size="16px" /></button>
        </div>
      </div>
    {/each}
  </div>
</div>

<style>
  .clm { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .wrap { display: grid; gap: 10px; }
  .ac { background: var(--well); border: 1px solid rgba(255,255,255,.05); border-radius: 15px; padding: 11px; display: grid; gap: 8px; box-shadow: inset 0 1px 0 var(--edge-hi); min-width: 0; }
  .room { font-size: 12px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .temp { font-size: 30px; font-weight: 200; display: flex; align-items: center; gap: 8px; white-space: nowrap; }
  .temp :global(svg) { color: var(--mc); filter: drop-shadow(0 0 6px var(--mc)); flex: none; }
  .off .temp { color: var(--muted); }
  .off .temp :global(svg) { color: #5b6b82; filter: none; }
  .set { display: flex; align-items: center; justify-content: space-between; gap: 4px; font-size: 12px; color: var(--muted); white-space: nowrap; }
  .set b { color: var(--text); font-weight: 400; }
  .step { width: 30px; height: 30px; border-radius: 10px; border: 1px solid var(--edge); color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.08), rgba(255,255,255,.02)); font-size: 16px; line-height: 1; box-shadow: inset 0 1px 0 var(--edge-hi); padding: 0; }
  .step:active { transform: scale(.94); }
  .ctl { display: flex; gap: 6px; }
  .chip { flex: 1; min-width: 0; border: 1px solid var(--edge); color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.02)); border-radius: 11px; padding: 7px 8px; font-size: 12px; box-shadow: inset 0 1px 0 var(--edge-hi); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .pwr { width: 36px; height: 34px; flex: none; border-radius: 11px; border: 1px solid var(--edge); color: var(--text); background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.02)); display: grid; place-items: center; padding: 0; transition: box-shadow .25s, background .25s; }
  .pwr.on { background: radial-gradient(circle at 50% 30%, #9be2ff, var(--cool)); border-color: transparent; color: #04213a; box-shadow: 0 0 20px -2px var(--cool), inset 0 1px 0 rgba(255,255,255,.6); }
</style>
