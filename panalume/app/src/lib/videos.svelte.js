// A TV decodes one video at a time: on a Philips (MediaTek) TV a second camera
// video makes its decoder manager kill the whole app. So with ?tv only one
// camera plays video, a full-screen one before any card; the others show
// pictures until it stops.
import { untrack } from 'svelte';
import { tvMode } from './tv.js';

const MAX = tvMode ? 1 : Infinity;
const wanting = $state([]);
let seq = 0;

export const videoTicket = () => ++seq;

/** Asks for a video slot; returns the function that gives it back. */
export function wantVideo(me, main) {
  untrack(() => wanting.push({ me, main }));
  return () => untrack(() => {
    const i = wanting.findIndex((x) => x.me === me);
    if (i >= 0) wanting.splice(i, 1);
  });
}

/** Whether this camera may play video now (reactive). */
export function mayPlay(me) {
  if (MAX === Infinity) return true;
  return [...wanting].sort((a, b) => b.main - a.main || a.me - b.me).slice(0, MAX).some((x) => x.me === me);
}
