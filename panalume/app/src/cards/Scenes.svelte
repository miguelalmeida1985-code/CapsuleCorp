<script module>
  export const meta = {
    type: 'scenes', name: 'Scenes', icon: 'mdi:palette-outline', category: 'Controls',
    size: { w: 280, h: 300 }, tap: 'none',
    defaults: { title: 'Cenas', icon: 'mdi:home-outline', entities: [], columns: 2 },
    fields: [
      { key: 'title', label: 'Title', type: 'text' },
      { key: 'icon', label: 'Title icon', type: 'icon' },
      { key: 'entities', label: 'Scenes, scripts or switches', type: 'entities' },
      { key: 'columns', label: 'Columns', type: 'number', placeholder: '2' },
    ],
  };
</script>

<script>
  // Scene buttons in a grid. Scenes and scripts run on a tap; switches and
  // lights toggle and glow while on.
  import Icon from '../components/Icon.svelte';
  import CardTitle from '../components/CardTitle.svelte';
  import { t, ent } from '../lib/tpl.js';
  import { name, entityIcon, isActive, toggle, domain } from '../lib/entity.js';
  let { props } = $props();

  // A scene or script shows as pressed for a moment after the tap.
  let fired = $state('');
  let timer;
  function press(id) {
    const e = ent(id);
    if (!e) return;
    toggle(e);
    if (['scene', 'script', 'button', 'input_button'].includes(domain(id))) {
      fired = id;
      clearTimeout(timer);
      timer = setTimeout(() => (fired = ''), 1500);
    }
  }
</script>

<div class="scn">
  {#if props.title}<CardTitle icon={props.icon} title={t(props.title)} />{/if}
  <div class="grid" style="grid-template-columns:repeat({Number(props.columns) || 2},1fr)">
    {#each props.entities || [] as id (id)}
      {@const e = ent(id)}
      {@const d = domain(id)}
      <button class="scene" data-stop aria-pressed={fired === id || (!['scene', 'script', 'button', 'input_button'].includes(d) && isActive(e))} onclick={() => press(id)}>
        <Icon icon={entityIcon(e)} size="18px" />
        <span>{name(e, '') || id}</span>
      </button>
    {/each}
  </div>
</div>

<style>
  .scn { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .grid { display: grid; gap: 8px; align-content: start; }
  .scene { border: 1px solid var(--edge); border-radius: 13px; padding: 9px 10px; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; font-size: 12px; text-align: left; color: var(--text); min-width: 0;
    background: linear-gradient(180deg, rgba(255,255,255,.06), rgba(255,255,255,.015)); box-shadow: inset 0 1px 0 var(--edge-hi), 0 6px 14px -8px rgba(0,0,0,.8); transition: transform .12s, box-shadow .2s, background .2s; }
  .scene span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
  .scene:active { transform: scale(.97); }
  .scene :global(svg) { color: var(--muted); }
  .scene[aria-pressed='true'] { border-color: rgba(255,184,77,.55); background: linear-gradient(180deg, rgba(255,184,77,.24), rgba(255,184,77,.07)); box-shadow: inset 0 1px 0 rgba(255,255,255,.18), 0 0 22px -6px rgba(255,184,77,.7); }
  .scene[aria-pressed='true'] :global(svg) { color: var(--sun); filter: drop-shadow(0 0 6px var(--sun)); }
</style>
