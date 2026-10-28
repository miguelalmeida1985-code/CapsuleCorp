<script module>
  import { foxess } from '../lib/foxess.js';
  import { find } from '../lib/octopus.js';
  export const meta = {
    type: 'energy', name: 'Energy now', icon: 'mdi:lightning-bolt', category: 'Energy',
    size: { w: 520, h: 300 }, tap: 'none',
    defaults: { title: 'Energia', subtitle: '' },
    autofill: () => {
      const f = foxess();
      if (!f.solar) return {};
      return { solar: f.solar, home: f.home || '', grid_export: f.grid_export || '', grid_import: f.grid_import || '', today: f.solar_today || '', subtitle: 'Fox ESS',
        forecast: find(/^sensor\.solcast_pv_forecast_forecast_today$/) || '' };
    },
    fields: [
      { key: 'title', label: 'Title', type: 'text' },
      { key: 'subtitle', label: 'Subtitle (right of the title, e.g. the inverter)', type: 'text' },
      { key: 'solar', label: 'Solar power', type: 'entity', domain: 'sensor' },
      { key: 'home', label: 'House power', type: 'entity', domain: 'sensor' },
      { key: 'grid_export', label: 'Power sent to the grid', type: 'entity', domain: 'sensor' },
      { key: 'grid_import', label: 'Power from the grid', type: 'entity', domain: 'sensor' },
      { key: 'today', label: 'Solar energy today', type: 'entity', domain: 'sensor' },
      { key: 'forecast', label: 'Solar forecast today (Solcast, for the curve ahead)', type: 'entity', domain: 'sensor' },
    ],
  };
</script>

<script>
  // Production now in big glowing numbers, where it's going, and today's curve:
  // solar (amber) and the house (blue) since midnight, the forecast ahead dashed.
  import Icon from '../components/Icon.svelte';
  import CardTitle from '../components/CardTitle.svelte';
  import { t, ent } from '../lib/tpl.js';
  import { formatNumber, stateText } from '../lib/entity.js';
  import { useHistory } from '../lib/history.svelte.js';
  import { say } from '../lib/i18n.svelte.js';
  let { props } = $props();
  // The chart takes whatever room the numbers above leave.
  let cw = $state(0), chh = $state(0);

  const kw = (id) => {
    const e = id ? ent(id) : null;
    const v = Number(e?.state);
    if (!e || !Number.isFinite(v)) return null;
    return /^w$/i.test(e.attributes.unit_of_measurement || '') ? v / 1000 : v;
  };
  const fmt = (v) => (v == null ? '—' : v < 1 && v > -1 && v !== 0 ? formatNumber(v, 2) : formatNumber(v, v >= 10 ? 1 : 2));
  const solar = $derived(kw(props.solar));
  const home = $derived(kw(props.home));
  const exp = $derived(kw(props.grid_export));
  const imp = $derived(kw(props.grid_import));
  const toGrid = $derived(exp != null && exp > 0.005);

  // Today since midnight, refreshed every few minutes (and the live value appended).
  const midnight = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const hist = useHistory(() => [props.solar, props.home], () => Math.max(1, (Date.now() - midnight()) / 3600e3));
  const unitOf = (id) => (/^w$/i.test(ent(id)?.attributes.unit_of_measurement || '') ? 1000 : 1);
  // Average into 10-minute buckets: smooth enough for a glance, a few hundred points at most.
  function buckets(id) {
    const pts = id ? hist.series[id] || [] : [];
    const d = unitOf(id), m0 = midnight(), out = new Map();
    for (const [ts, v] of pts) {
      if (ts < m0) continue;
      const b = Math.floor((ts - m0) / 600e3);
      const o = out.get(b) || [0, 0];
      o[0] += v / d; o[1]++;
      out.set(b, o);
    }
    return [...out].sort((a, b) => a[0] - b[0]).map(([b, [s, n]]) => [b / 144, s / n]);
  }
  const sol = $derived(buckets(props.solar));
  const hom = $derived(buckets(props.home));
  const fc = $derived((ent(props.forecast)?.attributes.detailedForecast || []).map((p) => [(Date.parse(p.period_start) - midnight()) / 864e5, Number(p.pv_estimate)]).filter((p) => p[0] >= 0 && p[0] <= 1 && Number.isFinite(p[1])));
  const nowX = $derived((Date.now() - midnight()) / 864e5);

  // Chart geometry.
  const CH = $derived(Math.max(40, chh - 20));
  const CW = $derived(Math.max(100, cw - 40));
  const top = $derived(Math.max(1, ...sol.map((p) => p[1]), ...hom.map((p) => p[1]), ...fc.map((p) => p[1])));
  const maxKw = $derived(Math.ceil(top));
  const X = (x) => x * CW;
  const Y = (v) => CH - (v / maxKw) * (CH - 6);
  // Smooth line through the points (monotone-ish Catmull-Rom).
  function path(pts) {
    if (pts.length < 2) return '';
    const p = pts.map(([x, v]) => [X(x), Y(v)]);
    let d = `M${p[0][0].toFixed(1)},${p[0][1].toFixed(1)}`;
    for (let i = 0; i < p.length - 1; i++) {
      const a = p[i - 1] || p[i], b = p[i], c = p[i + 1], e = p[i + 2] || c;
      const c1 = [b[0] + (c[0] - a[0]) / 6, b[1] + (c[1] - a[1]) / 6], c2 = [c[0] - (e[0] - b[0]) / 6, c[1] - (e[1] - b[1]) / 6];
      d += `C${c1[0].toFixed(1)},${Math.min(CH, c1[1]).toFixed(1)} ${c2[0].toFixed(1)},${Math.min(CH, c2[1]).toFixed(1)} ${c[0].toFixed(1)},${c[1].toFixed(1)}`;
    }
    return d;
  }
  const solPath = $derived(path(sol));
  const area = $derived(sol.length > 1 ? `${solPath}L${X(sol.at(-1)[0]).toFixed(1)},${CH}L${X(sol[0][0]).toFixed(1)},${CH}Z` : '');
  const fcPath = $derived(path(fc.filter((p) => p[0] >= nowX - 0.02)));
  const lastHome = $derived(hom.at(-1));
</script>

<div class="en">
  <CardTitle icon="mdi:lightning-bolt" title={t(props.title) || say('Energy', 'Energia')} color="var(--sun)">
    {#if props.subtitle}{t(props.subtitle)}{/if}
  </CardTitle>
  <div class="now">
    <div>
      <div class="lbl">{say('Producing now', 'Produção agora')}</div>
      <div class="big num glow-sun">{fmt(solar)}<small> kW</small></div>
    </div>
    <div class="flows">
      <div class="flow" style="--c:var(--sun)"><span class="fi"><Icon icon="mdi:white-balance-sunny" size="18px" /></span><span><span class="l">Solar</span><span class="v num glow-sun">{fmt(solar)} kW</span></span></div>
      <div class="flow" style="--c:var(--cool)"><span class="fi"><Icon icon="mdi:home" size="18px" /></span><span><span class="l">{say('House', 'Casa')}</span><span class="v num glow-cool">{fmt(home)} kW</span></span></div>
      {#if toGrid || imp == null}
        <div class="flow" style="--c:var(--ok)"><span class="fi"><Icon icon="mdi:transmission-tower-export" size="18px" /></span><span><span class="l">{say('To the grid', 'Injetar na rede')}</span><span class="v num glow-ok">{fmt(exp)} kW</span></span></div>
      {:else}
        <div class="flow" style="--c:#c9a0ff"><span class="fi"><Icon icon="mdi:transmission-tower-import" size="18px" /></span><span><span class="l">{say('From the grid', 'Da rede')}</span><span class="v num">{fmt(imp)} kW</span></span></div>
      {/if}
      <div class="flow" style="--c:var(--muted)"><span class="fi"><Icon icon="mdi:lightning-bolt" size="18px" /></span><span><span class="l">{say('Today', 'Hoje')}</span><span class="v num">{props.today ? stateText(ent(props.today)) : '—'}</span></span></div>
    </div>
  </div>
  <div class="cbox" bind:clientWidth={cw} bind:clientHeight={chh}>
  <svg class="chart" width={CW + 40} height={CH + 20} aria-hidden="true">
    <defs>
      <linearGradient id="en-sun" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#ffb84d" stop-opacity=".35" /><stop offset="1" stop-color="#ffb84d" stop-opacity="0" /></linearGradient>
    </defs>
    {#each Array.from({ length: maxKw }, (_, i) => i + 1) as k}
      <line x1="0" x2={CW} y1={Y(k)} y2={Y(k)} class="grid" /><text x={CW + 6} y={Y(k) + 3}>{k} kW</text>
    {/each}
    {#each [0, 0.25, 0.5, 0.75, 1] as x}<text x={X(x)} y={CH + 16} text-anchor={x === 0 ? 'start' : x === 1 ? 'end' : 'middle'}>{String(x * 24).padStart(2, '0')}:00</text>{/each}
    {#if area}<path d={area} fill="url(#en-sun)" />{/if}
    {#if fcPath}<path d={fcPath} class="fc" />{/if}
    {#if solPath}<path d={solPath} class="sol" />{/if}
    {#if hom.length > 1}<path d={path(hom)} class="home" />{/if}
    <line x1={X(nowX)} x2={X(nowX)} y1="0" y2={CH} class="nowl" />
    {#if lastHome}<circle cx={X(lastHome[0])} cy={Y(lastHome[1])} r="4" class="dotn" />{/if}
  </svg>
  </div>
</div>

<style>
  .en { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .now { display: grid; grid-template-columns: auto 1fr; gap: 6px 22px; align-items: center; }
  .lbl { font-size: 12px; color: var(--muted); }
  .big { font-size: 46px; font-weight: 200; line-height: 1; letter-spacing: -1px; white-space: nowrap; }
  .big small { font-size: 18px; color: var(--muted); font-weight: 300; letter-spacing: 0; text-shadow: none; }
  .glow-sun { color: var(--sun); text-shadow: 0 0 18px rgba(255,184,77,.55); }
  .glow-cool { color: var(--cool); text-shadow: 0 0 18px rgba(92,203,255,.5); }
  .glow-ok { color: var(--ok); text-shadow: 0 0 18px rgba(67,224,138,.45); }
  .flows { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 12px; }
  .flow { display: flex; gap: 9px; align-items: center; min-width: 0; }
  .flow > span:last-child { display: flex; flex-direction: column; min-width: 0; }
  .fi { width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; flex: none; color: var(--c); background: color-mix(in srgb, var(--c) 12%, transparent); box-shadow: 0 0 16px -6px var(--c); }
  .fi :global(svg) { filter: drop-shadow(0 0 4px var(--c)); }
  .l { font-size: 11px; color: var(--muted); white-space: nowrap; }
  .v { font-size: 15px; white-space: nowrap; }
  .cbox { flex: 1; min-height: 0; margin-top: 12px; position: relative; }
  .chart { position: absolute; left: 0; top: 0; display: block; overflow: visible; }
  .chart text { fill: var(--muted); font-size: 10px; font-family: var(--font); }
  .grid { stroke: rgba(255,255,255,.06); }
  .sol { fill: none; stroke: #ffb84d; stroke-width: 2.5; filter: drop-shadow(0 0 6px rgba(255,184,77,.8)); }
  .fc { fill: none; stroke: #ffb84d; stroke-width: 2; stroke-dasharray: 4 5; opacity: .55; }
  .home { fill: none; stroke: #5ccbff; stroke-width: 2; filter: drop-shadow(0 0 5px rgba(92,203,255,.7)); }
  .nowl { stroke: rgba(255,255,255,.25); stroke-dasharray: 2 4; }
  .dotn { fill: #5ccbff; stroke: rgba(255,255,255,.8); stroke-width: 1.5; }
</style>
