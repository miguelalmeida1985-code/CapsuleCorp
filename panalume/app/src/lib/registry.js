// Card registry: every file in src/cards exports a default component and a
// `meta` object (name, icon, default size, editor fields). Drop in a new file
// to add a card type. The metas are all here from the start; each card's
// component loads the first time it's shown (see build/card-meta.js).
import metas from 'virtual:card-metas';

const loaders = import.meta.glob('../cards/*.svelte');

export const cards = {};
for (const [file, meta] of Object.entries(metas)) {
  if (!meta) continue;
  const load = loaders[`../cards/${file}`];
  const def = { meta, component: null };
  def.load = () => (def.component ? Promise.resolve(def.component) : load().then((m) => (def.component = m.default)));
  cards[meta.type] = def;
}

/** Load the components of these card types in the background (the ones the dashboard uses). */
export function preload(types) {
  const go = () => { for (const t of new Set(types)) cards[t]?.load().catch(() => {}); };
  (window.requestIdleCallback || ((f) => setTimeout(f, 1500)))(go);
}

export const categories = ['Controls', 'Info', 'Media', 'Energy', 'Layout'];
