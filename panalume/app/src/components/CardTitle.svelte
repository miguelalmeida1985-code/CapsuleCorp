<script>
  // A card's heading in the CapsuleOS style: an icon in a small well, the title,
  // and anything on the right (a status, a link).
  import Icon from './Icon.svelte';
  let { icon = '', title = '', color = '', children = null } = $props();
</script>

<header class="ct">
  <h2>
    {#if icon}<span class="hi" style={color ? `color:${color}` : ''}><Icon {icon} size="17px" /></span>{/if}
    <span class="tt">{title}</span>
  </h2>
  {#if children}<div class="right">{@render children()}</div>{/if}
</header>

<style>
  .ct { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 14px; min-width: 0; }
  h2 { font-family: var(--display); font-weight: 400; font-size: 16px; margin: 0; display: flex; align-items: center; gap: 10px; min-width: 0; }
  .tt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .hi { width: 30px; height: 30px; border-radius: 10px; display: grid; place-items: center; flex: none; background: var(--well); box-shadow: inset 0 1px 0 var(--edge-hi); }
  .right { font-size: 12px; color: var(--muted); white-space: nowrap; }
</style>
