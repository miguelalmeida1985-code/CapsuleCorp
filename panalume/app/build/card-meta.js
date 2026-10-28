// Cards load on demand. Every card's `<script module>` (its meta: name, fields,
// autofill…) is needed up front for the editor and the entity list, but the
// card itself only when it's on screen. This serves each card's module script on
// its own as `Card.svelte.meta.js`, and `virtual:card-metas` gathers them all, so
// the main bundle carries the metas and each card's component becomes its own chunk.
import fs from 'node:fs';
import path from 'node:path';

const VIRTUAL = 'virtual:card-metas';
const SUFFIX = '.svelte.meta.js';

export default function cardMeta({ dir }) {
  const files = () => fs.readdirSync(dir).filter((f) => f.endsWith('.svelte')).sort();
  return {
    name: 'card-meta',
    enforce: 'pre',
    resolveId(id, importer) {
      if (id === VIRTUAL) return '\0' + VIRTUAL;
      if (id.endsWith(SUFFIX)) return path.resolve(importer ? path.dirname(importer) : dir, id);
    },
    load(id) {
      if (id === '\0' + VIRTUAL) {
        const list = files();
        return list.map((f, i) => `import { meta as m${i} } from ${JSON.stringify(path.join(dir, f) + '.meta.js')};`).join('\n')
          + `\nexport default { ${list.map((f, i) => `${JSON.stringify(f)}: m${i}`).join(', ')} };\n`;
      }
      if (id.endsWith(SUFFIX)) {
        const file = id.slice(0, -'.meta.js'.length);
        this.addWatchFile(file);
        const src = fs.readFileSync(file, 'utf8');
        const m = src.match(/<script\b[^>]*\bmodule\b[^>]*>([\s\S]*?)<\/script>/);
        // The name ends in .svelte…js, so vite-plugin-svelte compiles it as a Svelte module (runes work).
        return m ? m[1] : 'export const meta = null;';
      }
    },
  };
}
