<script module>
  export const meta = {
    type: 'clock', name: 'Clock & date', icon: 'mdi:clock-outline', category: 'Info',
    size: { w: 260, h: 120 }, tap: 'none',
    defaults: { seconds: false, date: true, hour12: false },
    fields: [
      { key: 'seconds', label: 'Show seconds', type: 'bool' },
      { key: 'hour12', label: '12-hour', type: 'bool' },
      { key: 'date', label: 'Show date', type: 'bool' },
      { key: 'subtitle', label: 'Subtitle (template)', type: 'text' },
    ],
  };
</script>

<script>
  import { t } from '../lib/tpl.js';
  let { props } = $props();
  let now = $state(new Date());
  $effect(() => {
    const ms = props.seconds ? 1000 : 60000;
    let timer;
    const tickTo = () => { now = new Date(); timer = setTimeout(tickTo, ms - (Date.now() % ms) + 5); };
    tickTo();
    return () => clearTimeout(timer);
  });
  const time = $derived(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: props.seconds ? '2-digit' : undefined, hour12: !!props.hour12 }));
  const date = $derived(now.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' }).replace(/^./, (c) => c.toUpperCase()));
</script>

<div class="clock">
  <div class="time">{time}</div>
  {#if props.date}<div class="date">{date}</div>{/if}
  {#if props.subtitle}<div class="date">{t(props.subtitle)}</div>{/if}
</div>

<style>
  .clock { height: 100%; display: flex; flex-direction: column; justify-content: center; }
  /* Thin, with a cool glow: the gradient is painted into the text once, the glow is a static drop shadow. */
  .time { font-family: var(--display); font-size: 3.6em; font-weight: 200; letter-spacing: -.035em; line-height: 1; font-variant-numeric: tabular-nums;
    background: linear-gradient(180deg, #fff, #a9c3e8); -webkit-background-clip: text; background-clip: text; color: transparent; filter: drop-shadow(0 0 22px rgba(120,170,255,.35)); }
  .date { color: var(--muted); margin-top: 6px; font-size: 1em; }
</style>
