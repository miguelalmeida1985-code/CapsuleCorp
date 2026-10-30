<script module>
  export const meta = {
    type: 'night-report', name: 'Night report', icon: 'mdi:weather-night', category: 'Info',
    size: { w: 320, h: 110 }, tap: 'none',
    defaults: { people: [], watch: [], weather: '', night_from: '20:00', morning_from: '05:00', morning_to: '12:00', popup: true, secs: 90 },
    fields: [
      { key: 'people', label: 'Person sensors (empty: every Frigate camera)', type: 'entities', domain: 'binary_sensor' },
      { key: 'watch', label: 'Lights and plugs to report (e.g. the garland)', type: 'entities', domain: ['switch', 'light', 'input_boolean'] },
      { key: 'weather', label: "Weather (empty: the Weather card's)", type: 'entity', domain: 'weather' },
      { key: 'night_from', label: 'Night starts at (HH:MM)', type: 'text' },
      { key: 'popup', label: 'Opens by itself the first time the tablet wakes in the morning', type: 'bool' },
      { key: 'morning_from', label: 'Morning from (HH:MM)', type: 'text' },
      { key: 'morning_to', label: 'Morning until (HH:MM)', type: 'text' },
      { key: 'secs', label: 'Close it after (seconds)', type: 'number' },
    ],
    watch: () => ['sun.sun'],
  };
</script>

<script>
  // A small tile with last night's summary; a tap opens the whole report. The same
  // report opens by itself on a wall tablet the first time it wakes up in the morning (App.svelte).
  import Icon from '../components/Icon.svelte';
  import { app } from '../lib/config.svelte.js';
  import { conn } from '../lib/ha.svelte.js';
  import { say } from '../lib/i18n.svelte.js';
  import { loadNight, duration } from '../lib/nightreport.js';
  let { props, card } = $props();

  let night = $state(null);
  $effect(() => {
    if (!conn.connected) return;
    const p = $state.snapshot(props);
    let alive = true;
    const load = () => loadNight(p).then((n) => alive && (night = n)).catch(() => {});
    load();
    const timer = setInterval(load, 10 * 60e3);
    return () => { alive = false; clearInterval(timer); };
  });
  const seen = $derived(night ? night.people.filter((x) => x.spans.length) : []);
  const lit = $derived(night ? night.watch.filter((x) => x.spans.length) : []);
  const line = $derived.by(() => {
    if (!night) return '…';
    const parts = [seen.length
      ? say(`People on ${seen.length} camera${seen.length > 1 ? 's' : ''}`, `Pessoas em ${seen.length} câmara${seen.length > 1 ? 's' : ''}`)
      : say('Nobody on the cameras', 'Ninguém nas câmaras')];
    for (const w of lit) parts.push(`${w.name} ${duration(w.spans.reduce((t, s) => t + s[1] - s[0], 0))}`);
    return parts.join(' · ');
  });
  function open() {
    if (card?.id) app.popup = { report: card.id };
  }
</script>

<button class="nr" class:alert={seen.length} data-stop onclick={open}>
  <span class="moon"><Icon icon={seen.length ? 'mdi:account-alert-outline' : 'mdi:weather-night'} size="1.6em" /></span>
  <span class="tx">
    <b>{say('Night report', 'Relatório da noite')}</b>
    <small>{line}</small>
  </span>
  <Icon icon="mdi:chevron-right" size="1.2em" />
</button>

<style>
  .nr { position: absolute; inset: 0; display: flex; align-items: center; gap: 12px; padding: 0 4px; border: 0; background: none; color: inherit; font: inherit; text-align: left; cursor: pointer; }
  .moon { flex: none; width: 2.6em; height: 2.6em; border-radius: 50%; display: grid; place-items: center; color: #cfe0ff;
    background: radial-gradient(circle at 35% 30%, rgba(160,190,255,.35), rgba(40,60,110,.25)); box-shadow: 0 0 18px -6px rgba(140,170,255,.8), inset 0 1px 0 var(--edge-hi); }
  .alert .moon { color: #1d1204; background: radial-gradient(circle at 50% 30%, #ffd58a, var(--sun)); box-shadow: 0 0 18px -4px var(--sun); }
  .tx { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  b { font-weight: 600; }
  small { color: var(--muted); font-size: .82em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
