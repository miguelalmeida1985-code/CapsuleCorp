<script module>
  export const meta = {
    type: 'devices', name: 'Device list', icon: 'mdi:power-plug-outline', category: 'Controls',
    size: { w: 360, h: 300 }, tap: 'none',
    defaults: { title: 'Equipamentos', icon: 'mdi:power-plug-outline', entities: [], read_only: [], lines: '' },
    fields: [
      { key: 'title', label: 'Title', type: 'text' },
      { key: 'icon', label: 'Title icon', type: 'icon' },
      { key: 'entities', label: 'Devices', type: 'entities' },
      { key: 'names', label: 'Names (one per device: entity_id: name, templates ok)', type: 'textarea', hint: 'e.g. switch.boiler_eco_mode: Modo eco. Without one, the name from Home Assistant shows.' },
      { key: 'read_only', label: 'Show only, no switch (e.g. the well pump)', type: 'entities' },
      { key: 'lines', label: 'Second lines (one per device: entity_id: text, templates ok)', type: 'textarea', hint: 'e.g. switch.well_pump: Rega às {{ states("input_datetime.rega") }}. Without one, the device\'s state shows.' },
    ],
  };
</script>

<script>
  // A list of devices in glass rows: icon, name, a status line with a coloured
  // dot, and on the right a switch, a value or "só leitura".
  import Icon from '../components/Icon.svelte';
  import CardTitle from '../components/CardTitle.svelte';
  import { t, ent } from '../lib/tpl.js';
  import { name, stateText, entityIcon, isActive, unavailable, canToggle, toggle, domain } from '../lib/entity.js';
  import { say } from '../lib/i18n.svelte.js';
  import { app } from '../lib/config.svelte.js';
  let { props } = $props();

  const COLOR = { light: 'var(--sun)', switch: 'var(--sun)', climate: 'var(--cool)', water_heater: 'var(--hot)', sensor: 'var(--sun)', binary_sensor: 'var(--ok)', update: 'var(--cool)' };
  // "entity_id: text" lines, one per device.
  const perEntity = (s) => Object.fromEntries(String(s || '').split('\n').map((l) => l.match(/^\s*([a-z_]+\.[a-z0-9_]+)\s*:\s*(.+)$/)).filter(Boolean).map((m) => [m[1], m[2]]));
  const lines = $derived(perEntity(props.lines));
  const names = $derived(perEntity(props.names));
  const rows = $derived((props.entities || []).map((id) => {
    const e = ent(id);
    const d = domain(id);
    const ro = (props.read_only || []).includes(id);
    const sw = !ro && canToggle(id) && ['light', 'switch', 'fan', 'input_boolean', 'automation', 'siren'].includes(d);
    const on = isActive(e);
    const numeric = e && e.state !== '' && !Number.isNaN(Number(e.state));
    const sub = lines[id] ? t(lines[id]) : unavailable(e) ? say('Unavailable', 'Indisponível') : numeric ? '' : stateText(e);
    return { id, e, d, ro, sw, on, sub, n: names[id] ? t(names[id]) : name(e, '') || id, value: numeric ? stateText(e, Number.isInteger(Number(e.state)) ? { digits: 0 } : {}) : '', dot: unavailable(e) ? 'bad' : on || numeric || d === 'binary_sensor' ? 'ok' : 'off', color: on || numeric ? COLOR[d] || 'var(--cool)' : '' };
  }));
</script>

<div class="devs">
  {#if props.title}<CardTitle icon={props.icon} title={t(props.title)} />{/if}
  <div class="list">
    {#each rows as r (r.id)}
      <div class="dev" role="button" tabindex="0" data-stop onclick={() => (app.popup = { entity: r.id })} onkeydown={(ev) => ev.key === 'Enter' && (app.popup = { entity: r.id })}>
        <span class="ic" style={r.color ? `--c:${r.color}` : ''}><Icon icon={entityIcon(r.e)} size="17px" /></span>
        <span class="txt">
          <span class="n">{r.n}</span>
          {#if r.sub}<span class="s"><span class="dot {r.dot}"></span>{r.sub}</span>{/if}
        </span>
        {#if r.sw}
          <button class="toggle" aria-pressed={r.on} aria-label={r.n} onclick={(ev) => { ev.stopPropagation(); r.e && toggle(r.e); }}></button>
        {:else if r.value}
          <span class="r num" style={r.color ? `color:${r.color}` : ''}>{r.value}</span>
        {:else if r.ro}
          <span class="lock">{say('read only', 'só leitura')}</span>
        {/if}
      </div>
    {/each}
  </div>
</div>

<style>
  .devs { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .list { display: grid; gap: 8px; align-content: start; overflow: hidden; }
  .dev { display: grid; grid-template-columns: 34px 1fr auto; gap: 10px; align-items: center; background: var(--well); border: 1px solid rgba(255,255,255,.05); border-radius: 13px; padding: 7px 10px; min-width: 0; box-shadow: inset 0 1px 0 var(--edge-hi); cursor: pointer; }
  .ic { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; color: var(--c, var(--muted)); background: color-mix(in srgb, var(--c, #93a4bd) 12%, transparent); box-shadow: 0 0 14px -6px var(--c, transparent); }
  .ic :global(svg) { filter: drop-shadow(0 0 4px color-mix(in srgb, var(--c, transparent) 70%, transparent)); }
  .txt { display: flex; flex-direction: column; min-width: 0; }
  .n { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .s { font-size: 11px; color: var(--muted); display: flex; gap: 5px; align-items: center; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .dot { width: 7px; height: 7px; flex: none; border-radius: 50%; background: var(--ok); box-shadow: 0 0 8px var(--ok); }
  .dot.off { background: #5b6b82; box-shadow: none; }
  .dot.bad { background: var(--hot); box-shadow: 0 0 8px var(--hot); }
  .r { font-size: 13px; text-align: right; white-space: nowrap; }
  .lock { font-size: 10px; color: var(--muted); border: 1px solid var(--edge); border-radius: 6px; padding: 2px 6px; white-space: nowrap; }
  .toggle { width: 44px; height: 24px; border-radius: 12px; border: 1px solid var(--edge); background: #1d2939; position: relative; padding: 0; box-shadow: inset 0 2px 4px rgba(0,0,0,.5); transition: background .25s, box-shadow .25s; }
  .toggle::after { content: ''; position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: linear-gradient(180deg, #fff, #c8d3e2); box-shadow: 0 2px 4px rgba(0,0,0,.5); transition: left .22s cubic-bezier(.3,.7,.3,1.3); }
  .toggle[aria-pressed='true'] { background: linear-gradient(180deg, #ffc56a, #f59e2c); box-shadow: 0 0 16px -2px var(--sun); }
  .toggle[aria-pressed='true']::after { left: 22px; }
</style>
