// The night report: what the cameras saw overnight and which lights or plugs came on,
// read from Home Assistant's history. The Night report card shows a summary; the
// pop-up (components/NightReportPanel.svelte) the whole night on a timeline.
import { send, subscribe } from './ha.svelte.js';

export const REPORT_DEFAULTS = { night_from: '20:00', morning_from: '05:00', morning_to: '12:00', secs: 90 };

const minutes = (v, fallback) => {
  const m = /^(\d{1,2})[:h](\d{2})$/.exec(String(v ?? '').trim());
  return m ? Math.min(23, Number(m[1])) * 60 + Math.min(59, Number(m[2])) : minutes(fallback, '00:00');
};

/** When last night started: the most recent "night from" time (yesterday's, in the morning). */
export function nightStart(props, now = new Date()) {
  const m = minutes(props.night_from, REPORT_DEFAULTS.night_from);
  const d = new Date(now);
  d.setHours(Math.floor(m / 60), m % 60, 0, 0);
  if (d > now) d.setDate(d.getDate() - 1);
  return d;
}

/** Inside the morning window, when waking the tablet opens the report. */
export function inMorning(props, now = new Date()) {
  const m = now.getHours() * 60 + now.getMinutes();
  const from = minutes(props.morning_from, REPORT_DEFAULTS.morning_from);
  const to = minutes(props.morning_to, REPORT_DEFAULTS.morning_to);
  return from <= to ? m >= from && m < to : m >= from || m < to;
}

// Frigate makes binary_sensor.<camera>_person_occupancy for each camera, and image.<camera>_person
// with its last snapshot of a person. With no sensors chosen, every Frigate person sensor is used.
const LOOKUP = `{%- set ns = namespace(people=[], watch=[]) -%}
{%- set found = people if people else (states.binary_sensor | map(attribute='entity_id') | select('search', '_person_occupancy$') | list) -%}
{%- for id in found -%}
{%- set fn = state_attr(id, 'friendly_name') or id -%}
{%- set dn = device_attr(id, 'name') -%}
{%- set b = id[14:-17] if id.endswith('_person_occupancy') else '' -%}
{%- set img = 'image.' ~ b ~ '_person' -%}
{%- set ok = b != '' and has_value(img) -%}
{%- set ns.people = ns.people + [{'id': id, 'name': dn if dn and fn.startswith(dn) else fn, 'pic': state_attr(img, 'entity_picture') if ok else '', 'at': states(img) if ok else ''}] -%}
{%- endfor -%}
{%- for id in watch -%}{%- set ns.watch = ns.watch + [{'id': id, 'name': state_attr(id, 'friendly_name') or id}] -%}{%- endfor -%}
{{ {'people': ns.people, 'watch': ns.watch} | tojson }}`;

function lookup(people, watch) {
  return new Promise((resolve, reject) => {
    let stop = null, done = false;
    const timer = setTimeout(() => finish(null, new Error('timeout')), 15e3);
    const finish = (v, err) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      queueMicrotask(() => stop?.());
      err ? reject(err) : resolve(v);
    };
    stop = subscribe({ type: 'render_template', template: LOOKUP, variables: { people, watch } }, (ev) => {
      if (ev.error) return finish(null, new Error(ev.error));
      try { finish(typeof ev.result === 'string' ? JSON.parse(ev.result) : ev.result); } catch (e) { finish(null, e); }
    });
  });
}

/** The periods an entity was on, as [from, to] in ms, clipped to the night. */
function spans(points = [], start, end) {
  const out = [];
  let from = null;
  for (const p of points) {
    const at = Math.max(start, (p.lu ?? p.lc) * 1000);
    const on = p.s === 'on';
    if (on && from == null) from = at;
    else if (!on && from != null) { out.push([from, at]); from = null; }
  }
  if (from != null) out.push([from, end]);
  return out;
}

/** Last night, from "night from" until now: { start, end, people: [{ id, name, spans, pic }], watch: [{ id, name, spans }] }. */
export async function loadNight(props, now = new Date()) {
  const start = nightStart(props, now).getTime(), end = now.getTime();
  const list = (v) => (Array.isArray(v) ? v : []).filter(Boolean);
  const found = await lookup(list(props.people), list(props.watch));
  const ids = [...found.people, ...found.watch].map((x) => x.id);
  const hist = ids.length ? await send({
    type: 'history/history_during_period',
    start_time: new Date(start).toISOString(),
    end_time: new Date(end).toISOString(),
    entity_ids: ids,
    minimal_response: true,
    no_attributes: true,
    significant_changes_only: false,
    include_start_time_state: true,
  }) : {};
  const people = found.people.map((x) => {
    const s = spans(hist[x.id], start, end);
    // The snapshot only if Frigate took it last night (not one from days ago).
    const at = Date.parse(x.at);
    return { id: x.id, name: x.name, spans: s, pic: s.length && at >= start && at <= end + 60e3 ? x.pic : '' };
  });
  const watch = found.watch.map((x) => ({ id: x.id, name: x.name, spans: spans(hist[x.id], start, end) }));
  return { start, end, people, watch };
}

export const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });

/** 3h05 / 45 min */
export function duration(ms) {
  const m = Math.max(1, Math.round(ms / 60e3));
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
}
