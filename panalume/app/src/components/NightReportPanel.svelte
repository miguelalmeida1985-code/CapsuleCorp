<script>
  // The night report, over the dashboard on frosted glass: each camera and each light or
  // plug on a timeline of last night, and today's weather in detail. Closes with the X,
  // Escape / Back, or by itself after a while (a touch on it keeps it open).
  import Icon from './Icon.svelte';
  import { app } from '../lib/config.svelte.js';
  import { states, subscribe, haImage, conn } from '../lib/ha.svelte.js';
  import { say, stateWord } from '../lib/i18n.svelte.js';
  import { loadNight, clock, duration, REPORT_DEFAULTS } from '../lib/nightreport.js';
  import { WX } from '../cards/Weather.svelte';

  let { props = {} } = $props();
  const close = () => (app.popup = null);

  let night = $state(null), failed = $state(false);
  $effect(() => {
    if (!conn.connected) return;
    loadNight($state.snapshot(props)).then((n) => (night = n)).catch(() => (failed = true));
  });

  // Closing by itself: a bar along the bottom shows the time left; touching the report stops it.
  const secs = Math.max(10, Number(props.secs) || REPORT_DEFAULTS.secs);
  let held = $state(false);
  $effect(() => {
    if (held) return;
    const t = setTimeout(close, secs * 1000);
    return () => clearTimeout(t);
  });

  const now = new Date();
  const hello = now.getHours() < 12 ? say('Good morning', 'Bom dia') : now.getHours() < 20 ? say('Good afternoon', 'Boa tarde') : say('Good evening', 'Boa noite');
  const today = now.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' });

  // ---- last night ----
  const seen = $derived(night ? night.people.filter((x) => x.spans.length) : []);
  const pos = (ms) => night ? Math.min(100, Math.max(0, ((ms - night.start) / (night.end - night.start)) * 100)) : 0;
  // Hour marks along the timeline, every two hours.
  const ticks = $derived.by(() => {
    if (!night) return [];
    const out = [];
    const d = new Date(night.start);
    d.setMinutes(0, 0, 0);
    d.setHours(d.getHours() + (d.getHours() % 2 ? 1 : 2));
    for (; d.getTime() < night.end - 30 * 60e3; d.setHours(d.getHours() + 2)) out.push({ at: pos(d.getTime()), label: String(d.getHours()).padStart(2, '0') + 'h' });
    return out;
  });
  const total = (spans) => spans.reduce((t, s) => t + s[1] - s[0], 0);
  const peopleText = (x) => {
    const n = x.spans.length;
    if (!n) return say('Nobody', 'Ninguém');
    const times = x.spans.slice(0, 3).map((s) => clock(s[0])).join(', ') + (n > 3 ? '…' : '');
    return `${n} ${n > 1 ? say('detections', 'deteções') : say('detection', 'deteção')} · ${times}`;
  };
  const watchText = (x) => {
    if (!x.spans.length) return say("Didn't turn on", 'Não ligou');
    const first = x.spans[0], last = x.spans[x.spans.length - 1];
    const still = last[1] >= night.end - 1000;
    return `${say('On at', 'Ligou às')} ${clock(first[0])} · ${still ? say('still on', 'ainda ligada') : `${say('off at', 'desligou às')} ${clock(last[1])}`} · ${duration(total(x.spans))}`;
  };

  // ---- today's weather ----
  const wxId = $derived(props.weather || Object.values(app.config?.cards || {}).find((c) => c.type === 'weather' && c.props?.entity)?.props.entity || [...states.keys()].find((k) => k.startsWith('weather.')) || '');
  const wx = $derived(states.get(wxId));
  const a = $derived(wx?.attributes || {});
  let hourly = $state([]), daily = $state([]);
  $effect(() => { if (wxId) return subscribe({ type: 'weather/subscribe_forecast', entity_id: wxId, forecast_type: 'hourly' }, (ev) => (hourly = ev.forecast || [])); });
  $effect(() => { if (wxId) return subscribe({ type: 'weather/subscribe_forecast', entity_id: wxId, forecast_type: 'daily' }, (ev) => (daily = ev.forecast || [])); });
  const day = $derived(daily.find((f) => new Date(f.datetime).toDateString() === now.toDateString()) || daily[0] || null);
  // The rest of today, hour by hour (at most nine columns; every other hour when the day is long).
  const hours = $derived.by(() => {
    const end = new Date(now); end.setHours(23, 59, 0, 0);
    let list = hourly.filter((f) => { const t = Date.parse(f.datetime); return t > now.getTime() - 3600e3 && t <= end.getTime(); });
    if (list.length < 4) list = hourly.slice(0, 9);
    const step = Math.ceil(list.length / 9) || 1;
    return list.filter((_, i) => i % step === 0).slice(0, 9);
  });
  const cond = (s) => stateWord('weather', null, s) || (s || '').replace(/-/g, ' ');
  const rain = $derived(day?.precipitation_probability ?? (hours.length ? Math.max(...hours.map((f) => Number(f.precipitation_probability) || 0)) : null));
  const sun = $derived(states.get('sun.sun')?.attributes || {});
  const facts = $derived.by(() => {
    const out = [];
    if (a.apparent_temperature != null) out.push(['mdi:thermometer', say('Feels like', 'Sensação'), `${Math.round(a.apparent_temperature)}°`]);
    if (rain != null) out.push(['mdi:umbrella-outline', say('Rain', 'Chuva'), `${Math.round(rain)}%${day?.precipitation ? ` · ${day.precipitation} ${a.precipitation_unit || 'mm'}` : ''}`]);
    if (a.wind_speed != null) out.push(['mdi:weather-windy', say('Wind', 'Vento'), `${Math.round(day?.wind_speed ?? a.wind_speed)} ${a.wind_speed_unit || ''}`.trim()]);
    if (a.humidity != null) out.push(['mdi:water-percent', say('Humidity', 'Humidade'), `${Math.round(a.humidity)}%`]);
    const uv = day?.uv_index ?? a.uv_index;
    if (uv != null) out.push(['mdi:white-balance-sunny', 'UV', String(Math.round(uv))]);
    if (sun.next_rising && sun.next_setting) out.push(['mdi:weather-sunset', say('Sun', 'Sol'), `${clock(Date.parse(sun.next_rising) - (Date.parse(sun.next_rising) > Date.parse(sun.next_setting) ? 864e5 : 0))} – ${clock(Date.parse(sun.next_setting))}`]);
    return out;
  });
</script>

<div class="back" role="presentation" onclick={(e) => e.target === e.currentTarget && close()}>
  <div class="glass" role="dialog" aria-modal="true" aria-label={say('Night report', 'Relatório da noite')} onpointerdown={() => (held = true)}>
    <div class="glow g1"></div><div class="glow g2"></div>
    <button class="x" onclick={close} aria-label={say('Close', 'Fechar')}><Icon icon="mdi:close" size="1.4em" /></button>

    <header>
      <span class="hello">{hello}</span>
      <span class="date">{today}</span>
    </header>

    <div class="cols">
      <section class="night">
        <h3><Icon icon="mdi:weather-night" size="1.1em" /> {say('Last night', 'Esta noite')}
          {#if night}<span class="range">{clock(night.start)} – {clock(night.end)}</span>{/if}</h3>
        {#if !night}
          <p class="wait">{failed ? say("Couldn't read the night from Home Assistant.", 'Não consegui ler a noite do Home Assistant.') : say('Reading the night…', 'A ler a noite…')}</p>
        {:else}
          <div class="verdict" class:alert={seen.length}>
            <Icon icon={seen.length ? 'mdi:account-alert-outline' : 'mdi:shield-check-outline'} size="1.5em" />
            <span>{seen.length
              ? say(`People seen on ${seen.length} camera${seen.length > 1 ? 's' : ''}`, `Pessoas vistas em ${seen.length} câmara${seen.length > 1 ? 's' : ''}`)
              : say('A quiet night: nobody on the cameras', 'Noite tranquila: ninguém nas câmaras')}</span>
          </div>
          <div class="rows">
            {#each night.people as x (x.id)}
              <div class="row" class:hit={x.spans.length}>
                <div class="lbl">
                  <span class="ic"><Icon icon={x.spans.length ? 'mdi:walk' : 'mdi:cctv'} size="1.05em" /></span>
                  <span class="nm"><b>{x.name}</b><small>{peopleText(x)}</small></span>
                  {#if x.pic}<img class="pic" src={haImage(x.pic)} alt="" />{/if}
                </div>
                <div class="track">{#each x.spans as s}<i class="dot" style="left:{pos(s[0])}%"></i>{/each}</div>
              </div>
            {/each}
            {#each night.watch as x (x.id)}
              <div class="row lamp" class:hit={x.spans.length}>
                <div class="lbl">
                  <span class="ic"><Icon icon={x.spans.length ? 'mdi:string-lights' : 'mdi:string-lights-off'} size="1.05em" /></span>
                  <span class="nm"><b>{x.name}</b><small>{watchText(x)}</small></span>
                </div>
                <div class="track">{#each x.spans as s}<i class="bar" style="left:{pos(s[0])}%;width:max(4px,{pos(s[1]) - pos(s[0])}%)"></i>{/each}</div>
              </div>
            {/each}
            {#if !night.people.length && !night.watch.length}<p class="wait">{say('No cameras or lights chosen yet (Night report card settings).', 'Ainda sem câmaras nem luzes escolhidas (definições do cartão Relatório da noite).')}</p>{/if}
          </div>
          {#if ticks.length}<div class="hours">{#each ticks as t}<span style="left:{t.at}%">{t.label}</span>{/each}</div>{/if}
        {/if}
      </section>

      <section class="day">
        <h3><Icon icon="mdi:weather-partly-cloudy" size="1.1em" /> {say('Today', 'Hoje')}</h3>
        {#if wx}
          <div class="now">
            <span class="wi {wx.state}"><Icon icon={WX[day?.condition || wx.state] || 'mdi:weather-cloudy'} size="4.2em" /></span>
            <div>
              <div class="temp">{a.temperature != null ? Math.round(a.temperature) + '°' : '—'}</div>
              <div class="cond">{cond(day?.condition || wx.state)}</div>
              {#if day}<div class="hl"><span>↑ {Math.round(day.temperature)}°</span>{#if day.templow != null}<span class="lo">↓ {Math.round(day.templow)}°</span>{/if}</div>{/if}
            </div>
          </div>
          {#if hours.length}
            <div class="hourly" style="--n:{hours.length}">
              {#each hours as f}
                {@const p = Number(f.precipitation_probability) || 0}
                <div class="h">
                  <span class="t">{Math.round(f.temperature)}°</span>
                  <span class="wi {f.condition}"><Icon icon={WX[f.condition] || 'mdi:weather-cloudy'} size="1.5em" /></span>
                  <span class="rain" class:some={p >= 20}><i style="height:{Math.max(2, p)}%"></i></span>
                  <span class="pp">{p ? p + '%' : ''}</span>
                  <span class="hh">{clock(Date.parse(f.datetime))}</span>
                </div>
              {/each}
            </div>
          {/if}
          {#if facts.length}
            <div class="facts">
              {#each facts as [ic, k, v]}<div class="fact"><Icon icon={ic} size="1.1em" /><span><small>{k}</small>{v}</span></div>{/each}
            </div>
          {/if}
        {:else}
          <p class="wait">{say('No weather in Home Assistant.', 'Sem meteorologia no Home Assistant.')}</p>
        {/if}
      </section>
    </div>

    {#if !held}<div class="timer" style="animation-duration:{secs}s"></div>{/if}
  </div>
</div>

<style>
  .back { position: fixed; inset: 0; z-index: 900; display: grid; place-items: center; padding: 20px; background: rgba(4,8,16,.4); backdrop-filter: blur(18px) saturate(1.3); animation: fade .35s ease; }
  .glass { position: relative; isolation: isolate; width: min(1080px, 100%); max-height: calc(100dvh - 40px); overflow-y: auto; padding: 30px 34px 26px; border-radius: 32px;
    background: linear-gradient(160deg, rgba(40,56,92,.55), rgba(12,18,32,.62)); border: 1px solid rgba(255,255,255,.14);
    box-shadow: 0 40px 100px -20px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.18);
    animation: rise .5s cubic-bezier(.2,.9,.3,1.1); }
  :global(html.lite) .back { background: rgba(4,8,16,.6); }
  :global(html.lite) .glass { background: linear-gradient(160deg, rgba(30,42,70,.97), rgba(12,18,32,.97)); }
  /* Two soft lights behind the glass: dawn on one side, night on the other. */
  .glow { position: absolute; border-radius: 50%; pointer-events: none; filter: blur(10px); z-index: -1; }
  .g1 { width: 520px; height: 520px; right: -160px; top: -220px; background: radial-gradient(circle, rgba(255,184,77,.32), transparent 65%); }
  .g2 { width: 560px; height: 560px; left: -220px; bottom: -280px; background: radial-gradient(circle, rgba(110,140,255,.3), transparent 65%); }
  .x { position: absolute; top: 18px; right: 18px; width: 46px; height: 46px; border-radius: 50%; border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.08); color: inherit; display: grid; place-items: center; z-index: 2; }
  header { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; margin: 0 60px 22px 0; }
  .hello { font-family: var(--display); font-size: 2.3em; font-weight: 300; letter-spacing: -.01em; }
  .date { color: var(--muted); font-size: 1.05em; text-transform: capitalize; }
  .cols { display: grid; grid-template-columns: 1.15fr 1fr; gap: 26px; }
  @media (max-width: 820px) { .cols { grid-template-columns: 1fr; } }
  section { min-width: 0; padding: 20px 22px; border-radius: 22px; background: rgba(255,255,255,.045); border: 1px solid rgba(255,255,255,.07); }
  h3 { margin: 0 0 14px; font-size: .82em; font-weight: 600; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); display: flex; align-items: center; gap: 8px; }
  .range { margin-left: auto; letter-spacing: .02em; font-variant-numeric: tabular-nums; }
  .wait { color: var(--muted); margin: 8px 0; }
  .verdict { display: flex; align-items: center; gap: 10px; padding: 12px 14px; margin-bottom: 14px; border-radius: 16px; font-weight: 600; color: var(--ok);
    background: color-mix(in srgb, var(--ok) 12%, transparent); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ok) 30%, transparent); }
  .verdict.alert { color: var(--sun); background: color-mix(in srgb, var(--sun) 13%, transparent); box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--sun) 35%, transparent), 0 0 30px -12px var(--sun); }
  .rows { display: flex; flex-direction: column; gap: 12px; }
  .row { display: flex; flex-direction: column; gap: 6px; }
  .lbl { display: flex; align-items: center; gap: 10px; }
  .ic { flex: none; width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; background: rgba(255,255,255,.06); color: var(--muted); }
  .row.hit .ic { color: #1d1204; background: radial-gradient(circle at 50% 30%, #ffd58a, var(--sun)); box-shadow: 0 0 16px -4px var(--sun); }
  .row.lamp.hit .ic { background: radial-gradient(circle at 50% 30%, #fff1c4, #ffcf6b); box-shadow: 0 0 20px -2px rgba(255,207,107,.9); }
  .nm { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .nm b { font-weight: 600; }
  .nm small { color: var(--muted); font-size: .85em; font-variant-numeric: tabular-nums; }
  .pic { flex: none; width: 72px; height: 42px; object-fit: cover; border-radius: 8px; box-shadow: 0 0 0 1px rgba(255,255,255,.15), 0 6px 14px -6px rgba(0,0,0,.8); }
  .track { position: relative; height: 8px; margin-left: 42px; border-radius: 4px; background: rgba(255,255,255,.06); }
  .dot { position: absolute; top: 50%; width: 10px; height: 10px; margin: -5px 0 0 -5px; border-radius: 50%; background: var(--sun); box-shadow: 0 0 10px var(--sun); }
  .bar { position: absolute; top: 0; bottom: 0; border-radius: 4px; background: linear-gradient(90deg, #ffcf6b, #ffe7a8); box-shadow: 0 0 12px rgba(255,207,107,.8); }
  .hours { position: relative; height: 16px; margin: 6px 0 0 42px; font-size: .72em; color: var(--muted); }
  .hours span { position: absolute; transform: translateX(-50%); }
  .now { display: flex; align-items: center; gap: 18px; margin-bottom: 16px; }
  .temp { font-family: var(--display); font-size: 3.4em; font-weight: 200; line-height: 1; }
  .cond { color: var(--muted); text-transform: capitalize; }
  .hl { display: flex; gap: 10px; font-weight: 600; margin-top: 4px; }
  .lo { color: var(--muted); }
  .wi { display: inline-flex; color: #dfe8f5; filter: drop-shadow(0 4px 8px rgba(0,0,0,.4)); }
  .wi.sunny, .wi.partlycloudy { color: var(--sun); filter: drop-shadow(0 0 14px rgba(255,184,77,.7)); }
  .wi.rainy, .wi.pouring, .wi.lightning-rainy { color: var(--cool); filter: drop-shadow(0 0 10px rgba(92,203,255,.55)); }
  .hourly { position: relative; display: grid; grid-template-columns: repeat(var(--n), minmax(0, 1fr)); padding-top: 4px; margin-bottom: 16px; }
  .h { display: flex; flex-direction: column; align-items: center; gap: 4px; font-variant-numeric: tabular-nums; }
  .t { font-weight: 600; font-size: .95em; }
  .rain { position: relative; width: 6px; height: 26px; border-radius: 3px; background: rgba(255,255,255,.06); overflow: hidden; }
  .rain i { position: absolute; left: 0; right: 0; bottom: 0; border-radius: 3px; background: rgba(92,203,255,.35); }
  .rain.some i { background: var(--cool); box-shadow: 0 0 8px var(--cool); }
  .pp { font-size: .72em; color: var(--cool); min-height: 1.1em; }
  .hh { font-size: .75em; color: var(--muted); }
  .facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
  .fact { display: flex; align-items: center; gap: 8px; padding: 9px 10px; border-radius: 14px; background: rgba(255,255,255,.05); color: var(--muted); }
  .fact span { display: flex; flex-direction: column; color: var(--text); font-weight: 600; font-size: .92em; white-space: nowrap; }
  .fact small { color: var(--muted); font-weight: 400; font-size: .78em; }
  .timer { position: absolute; left: 0; bottom: 0; height: 3px; width: 100%; transform-origin: left; background: linear-gradient(90deg, rgba(110,140,255,.8), rgba(255,184,77,.9)); animation: shrink linear forwards; }
  @keyframes shrink { to { transform: scaleX(0); } }
  @keyframes fade { from { opacity: 0; } }
  @keyframes rise { from { transform: translateY(24px) scale(.97); opacity: 0; } }
  @media (prefers-reduced-motion: reduce) { .glass, .back { animation: none; } }
</style>
