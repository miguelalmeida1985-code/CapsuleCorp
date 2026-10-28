// Fox ESS inverters (H1, H3, KH…), from either integration:
// foxess_modbus (local): sensor.[prefix_]pv_power_now, load_power, feed_in… all in kW.
// FoxESS cloud (macxq/foxess-ha): sensor.foxess_pv_power, foxess_load_power… in kW.
import { states } from './ha.svelte.js';
import { find } from './octopus.js';

/** The Fox ESS sensors found, by role; {} when there is no Fox ESS inverter. */
export function foxess() {
  const has = (id) => (states.has(id) ? id : '');
  // Modbus: the prefix (if one was set) comes from the PV power sensor.
  const pv = find(/^sensor\.([a-z0-9_]+_)?pv_power_now$/);
  if (pv) {
    const p = pv.slice(7, -'pv_power_now'.length);
    const s = (k) => has(`sensor.${p}${k}`);
    return {
      solar: pv, solar_today: s('solar_energy_today'), home: s('load_power'),
      grid_import: s('grid_consumption'), grid_export: s('feed_in'),
      // Grid CT is + exporting; Fox's battery power is + discharging.
      grid: s('grid_ct'), grid_positive_export: true,
      battery: s('invbatpower'), battery_soc: s('battery_soc'),
      battery_temp: s('battery_temp'), battery_soh: s('battery_soh'),
      charge_today: s('battery_charge_today'), discharge_today: s('battery_discharge_today'),
    };
  }
  const cloud = find(/^sensor\.foxess[a-z0-9_]*_pv_power$/);
  if (cloud) {
    const p = cloud.slice(7, -'pv_power'.length);
    const s = (k) => has(`sensor.${p}${k}`);
    return {
      solar: cloud, solar_today: s('energy_generated'), home: s('load_power'),
      grid_import: s('grid_consumption_power'), grid_export: s('feedin_power'),
      battery_soc: s('bat_soc'), battery_charge: s('bat_charge_power'), battery_discharge: s('bat_discharge_power'),
      charge_today: s('bat_charge'), discharge_today: s('bat_discharge'),
    };
  }
  return {};
}

/** Fills the blanks in a card's autofill from `extra`, keeping what was already found. */
export const fillBlanks = (found, extra) => {
  const out = { ...found };
  for (const [k, v] of Object.entries(extra)) if (v !== '' && v != null && !out[k]) out[k] = v;
  return out;
};
