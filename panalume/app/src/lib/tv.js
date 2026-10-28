// TV mode (?tv in the address): the remote's arrows move a highlight to the
// nearest card or button in that direction, OK presses it, Back closes a pop-up.
// TV boxes are slow at blurring what's behind glass, so ?tv also turns blur off
// (?effects=lite does just that on any screen, ?effects=full keeps it on a TV).
import { param } from './params.js';

const flag = (v) => v != null && v !== '0';
export const tvMode = flag(param('tv'));
export const liteEffects = param('effects') === 'lite' || (tvMode && param('effects') !== 'full');

const FOCUSABLE = 'button:not([disabled]), a[href], input, select, [role="button"], [tabindex="0"], .card.tappable';
const DIRS = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
// Remotes send these for Back, besides Escape.
const BACK = new Set(['Escape', 'GoBack', 'BrowserBack', 'Backspace']);

function candidates() {
  // Inside an open pop-up only its own controls; otherwise the whole page (views menu too).
  const scope = document.querySelector('[role="dialog"]') || document.body;
  return [...scope.querySelectorAll(FOCUSABLE)].filter((el) => {
    const r = el.getBoundingClientRect();
    return r.width > 4 && r.height > 4 && getComputedStyle(el).visibility !== 'hidden';
  });
}

/** The nearest element from `from` in direction [dx, dy], favouring ones straight ahead. */
function nearest(from, [dx, dy], list) {
  const a = from.getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  let best = null, score = Infinity;
  for (const el of list) {
    // Never back out to the card a button sits in.
    if (el === from || el.contains(from)) continue;
    const b = el.getBoundingClientRect();
    const bx = b.left + b.width / 2, by = b.top + b.height / 2;
    // Distance along the direction, measured edge to edge so big cards next to small buttons still line up.
    // Into a card's own buttons, measured from its centre.
    const along = from.contains(el) ? Math.abs(dx ? bx - ax : by - ay) : dx ? (dx > 0 ? b.left - a.right : a.left - b.right) : (dy > 0 ? b.top - a.bottom : a.top - b.bottom);
    const ahead = dx ? (bx - ax) * dx : (by - ay) * dy;
    // Overlapping a little is fine (a menu button sitting on a card's corner).
    if (ahead <= 0 || along < -(dx ? b.width : b.height) * 0.75) continue;
    const side = dx ? Math.abs(by - ay) : Math.abs(bx - ax);
    const s = Math.max(0, along) + side * 2;
    if (s < score) (score = s), (best = el);
  }
  return best;
}

function focus(el) {
  el.focus({ preventScroll: true });
  el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
}

/** Start listening to the remote. `back` closes whatever is open; returns false when nothing was. */
export function startTv({ back, active }) {
  document.documentElement.classList.add('tv');
  // Tappable cards can take the highlight and are pressed with OK.
  const mark = () => document.querySelectorAll('.card.tappable:not([tabindex])').forEach((c) => c.setAttribute('tabindex', '0'));
  new MutationObserver(mark).observe(document.body, { childList: true, subtree: true });
  mark();
  // Where the highlight was before a pop-up opened, to go back there when it closes.
  let last = null;
  addEventListener('focusin', (e) => { if (!e.target.closest?.('[role="dialog"]')) last = e.target; });
  addEventListener('keydown', (e) => {
    if (!active()) return;
    const typing = e.target.closest?.('input,textarea,select,[contenteditable]');
    if (BACK.has(e.key) && !(typing && e.key === 'Backspace')) {
      if (back()) e.preventDefault();
      return;
    }
    const d = DIRS[e.key];
    if (d && !typing) {
      e.preventDefault();
      const list = candidates();
      const cur = document.activeElement;
      if (!cur || cur === document.body || !list.includes(cur)) {
        if (last?.isConnected && list.includes(last)) return focus(last);
        // First press: start at the top-left card.
        const cards = list.filter((el) => el.closest('.card'));
        const first = (cards.length ? cards : list).sort((x, y) => { const a = x.getBoundingClientRect(), b = y.getBoundingClientRect(); return a.top - b.top || a.left - b.left; })[0];
        if (first) focus(first);
        return;
      }
      const next = nearest(cur, d, list);
      if (next) focus(next);
      return;
    }
    // OK on a card presses it like a tap (buttons and links press themselves).
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches?.('.card.tappable')) {
      e.preventDefault();
      e.target.dispatchEvent(new CustomEvent('tvpress'));
    }
  }, true);
}
