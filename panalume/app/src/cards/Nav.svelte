<script module>
  export const meta = {
    type: 'nav', name: 'Navigation', icon: 'mdi:menu', category: 'Layout',
    size: { w: 260, h: 220 }, tap: 'none',
    defaults: { direction: 'vertical', show_icons: true, show_names: true },
    fields: [
      { key: 'direction', label: 'Direction', type: 'select', options: ['vertical', 'horizontal'] },
      { key: 'show_icons', label: 'Icons', type: 'bool' },
      { key: 'show_names', label: 'Names', type: 'bool' },
    ],
  };
</script>

<script>
  import Icon from '../components/Icon.svelte';
  import { app, setView } from '../lib/config.svelte.js';
  let { props, w = 0, h = 0 } = $props();
  // A wide, short nav card lays out horizontally whatever its setting (e.g. on phones).
  const dir = $derived(w > h * 3 ? 'horizontal' : props.direction);
</script>

<nav class="nav {dir}" data-stop>
  {#each app.config.views as v}
    <button class:sel={app.view === v.id} onclick={() => setView(v.id)}>
      {#if props.show_icons !== false}<Icon icon={v.icon} size="1.3em" />{/if}
      {#if props.show_names !== false}<span>{v.name}</span>{/if}
    </button>
  {/each}
</nav>

<style>
  .nav { height: 100%; display: flex; flex-direction: column; justify-content: center; gap: 4px; overflow: hidden; }
  /* Rows share the height: they shrink together when the card is short instead of overflowing. */
  .nav:not(.horizontal) button { flex: 0 1 46px; min-height: 0; padding-block: 0; }
  .horizontal { flex-direction: row; align-items: center; }
  button { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 14px; border: 0; background: none; color: var(--muted); font: inherit; font-weight: 500; text-align: left; white-space: nowrap; transition: all .2s; }
  .horizontal { gap: 2px; }
  .horizontal button { flex: 1 1 0; min-width: 0; justify-content: center; gap: 8px; padding: 10px 8px; border-radius: 13px; font-size: .9em; }
  .horizontal button span { overflow: hidden; text-overflow: ellipsis; }
  button.sel { color: #fff; background: linear-gradient(180deg, rgba(92,160,255,.38), rgba(60,110,220,.22)); box-shadow: inset 0 1px 0 rgba(255,255,255,.22), 0 0 0 1px rgba(110,170,255,.35), 0 6px 22px -6px rgba(80,150,255,.7); }
  button.sel :global(svg) { color: #fff; filter: drop-shadow(0 0 6px rgba(160,200,255,.9)); }
  button:not(.sel):hover { color: var(--text); }
</style>
