// How the site writes an Event's Aggregates. The live Event table and the build (prerender.mjs)
// share it, so it imports nothing.

/** One Channel's Aggregates over an Event, as the bridge returns them; null stands for NaN. */
export type Aggregates = {
  min: number | null;
  max: number | null;
  mean: number | null;
  count: number;
  timeWeightedMean: number | null;
  integral: number | null;
};

/** A number rounded to two decimals; NaN as a dash. */
export function formatNumber(value: number | null): string {
  return value === null ? '–' : String(Math.round(value * 100) / 100);
}

/** The Event table's line for one Channel: its minimum, maximum and mean. */
export function aggregatesLine(channel: string, a: Aggregates): string {
  return a.count === 0
    ? `${channel}: no value`
    : `${channel}: min ${formatNumber(a.min)}, max ${formatNumber(a.max)}, mean ${formatNumber(a.mean)}`;
}
