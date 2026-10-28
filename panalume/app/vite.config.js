import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import oldSafari from './build/old-safari.js';
import { appVersion } from './server/version.js';
import cardMeta from './build/card-meta.js';
import { fileURLToPath } from 'node:url';

// Relative base so the app works behind HA ingress at any path.
export default defineConfig({
  base: './',
  plugins: [cardMeta({ dir: fileURLToPath(new URL('./src/cards', import.meta.url)) }), svelte(), oldSafari()],
  // Shown in the corner of every screen, to compare with the server's.
  define: { __APP_VERSION__: JSON.stringify(appVersion()) },
  // Older tablets too: Safari 14+ (e.g. iPads that stop at iPadOS 15).
  build: { target: ['es2020', 'safari14'], cssCodeSplit: true },
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8099', ws: true },
      '/ha': 'http://localhost:8099',
    },
  },
});
