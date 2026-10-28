<script module>
  export const meta = {
    type: 'childlock', name: 'Child lock', icon: 'mdi:lock-outline', category: 'Controls',
    size: { w: 64, h: 64 }, tap: 'none',
    defaults: {},
    fields: [],
  };
</script>

<script>
  // The padlock: a tap locks the screen (it keeps showing everything live, but
  // touches do nothing); holding it for 3 s unlocks. While locked, the page-wide
  // guard in App.svelte passes touches on this button to the hold below.
  import Icon from '../components/Icon.svelte';
  import { lock, setLocked } from '../lib/childlock.svelte.js';
  import { app } from '../lib/config.svelte.js';
  import { say } from '../lib/i18n.svelte.js';
  let { w = 64, h = 64, editing } = $props();
  const size = $derived(Math.max(36, Math.min(w - 24, h - 36)));
  const R = 46, C = 2 * Math.PI * R;
  // Show the hint after a touch elsewhere while locked, then fade it out.
  let hint = $state(false), timer;
  $effect(() => {
    if (!lock.nudge) return;
    hint = true;
    clearTimeout(timer);
    timer = setTimeout(() => (hint = false), 2500);
  });
  function tap() {
    if (editing || lock.locked) return;
    app.popup = null;
    setLocked(true);
  }
</script>

<div class="cl">
  <button class="pad" class:locked={lock.locked} data-childlock data-stop style="width:{size}px;height:{size}px" onclick={tap}
    aria-label={lock.locked ? say('Locked: hold 3 s to unlock', 'Bloqueado: segura 3 s para desbloquear') : say('Lock the screen', 'Bloquear o ecrã')}>
    <svg class="ring" viewBox="0 0 100 100" aria-hidden="true"><circle cx="50" cy="50" r={R} style="stroke-dasharray:{C};stroke-dashoffset:{C * (1 - lock.holding)}" /></svg>
    <Icon icon={lock.locked ? 'mdi:lock' : 'mdi:lock-open-variant-outline'} size="{Math.round(size * 0.42)}px" />
  </button>
  {#if lock.locked}<span class="hint" class:show={hint || lock.holding > 0}>{say('Hold 3 s', 'Segura 3 s')}</span>{/if}
</div>

<style>
  .cl { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; }
  .pad { position: relative; border-radius: 50%; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid var(--edge);
    background: linear-gradient(180deg, rgba(40,58,90,.5), rgba(10,16,30,.6)); box-shadow: inset 0 1px 0 var(--edge-hi), 0 10px 24px -12px rgba(0,0,0,.8);
    transition: color .25s, box-shadow .25s, background .25s; touch-action: none; }
  .pad.locked { color: #1d1204; border-color: transparent; background: radial-gradient(circle at 50% 30%, #ffd58a, var(--sun)); box-shadow: 0 0 16px -6px var(--sun), inset 0 1px 0 rgba(255,255,255,.6); }
  .ring { position: absolute; inset: -4px; width: calc(100% + 8px); height: calc(100% + 8px); transform: rotate(-90deg); pointer-events: none; }
  .ring circle { fill: none; stroke: #fff; stroke-width: 5; stroke-linecap: round; opacity: .9; filter: drop-shadow(0 0 4px rgba(255,255,255,.8)); }
  .hint { font-size: 11px; color: var(--sun); white-space: nowrap; opacity: 0; transition: opacity .3s; pointer-events: none; }
  .hint.show { opacity: 1; }
</style>
