// Child lock: the screen keeps showing everything live, but touches do nothing
// until someone holds the padlock for a few seconds. Kept in this browser, so a
// reload or a power cut doesn't unlock it.
const KEY = 'capsuleos-locked';
let saved = false;
try { saved = localStorage.getItem(KEY) === '1'; } catch {}
export const lock = $state({ locked: saved, holding: 0, nudge: 0 });
export const HOLD_MS = 3000;

export function setLocked(on) {
  lock.locked = on;
  lock.holding = 0;
  try { on ? localStorage.setItem(KEY, '1') : localStorage.removeItem(KEY); } catch {}
}

// Holding the padlock: progress 0..1 while the finger stays on it; unlocks at 1.
let raf = 0, t0 = 0;
export function startHold() {
  cancelHold();
  t0 = performance.now();
  const step = (t) => {
    lock.holding = Math.min(1, (t - t0) / HOLD_MS);
    if (lock.holding >= 1) return setLocked(false);
    raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
}
export function cancelHold() {
  cancelAnimationFrame(raf);
  raf = 0;
  lock.holding = 0;
}
/** A touch somewhere else while locked: the padlock shows its hint for a moment. */
export const nudge = () => lock.nudge++;
