// The CapsuleOS version: the one in the app's config.yaml, which is what Home
// Assistant shows and updates to. Read by the server and baked into the page.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export function appVersion() {
  // In the repo: panalume/config.yaml; in the image: /config.yaml.
  for (const f of [path.resolve(here, '../../config.yaml'), path.resolve(here, '../config.yaml')]) {
    try {
      const m = fs.readFileSync(f, 'utf8').match(/^version:\s*"?([^"\s]+)"?\s*$/m);
      if (m) return m[1];
    } catch {}
  }
  return 'dev';
}
