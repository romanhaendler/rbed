// What EventChart draws, computed: the chart rows, the Rules' bands and lifecycle marks on their
// lanes, the axes that share the plot between the Channels, an Expression with a unit of its own
// and the lanes, and the colors of it all.
import type { Event, PlottedExpression, Scenario } from './bridge';
import { eventKey, type Lifecycle, ofRule } from './events.ts';

/** A chart row: a Sample's time in the Scenario's unit and its Channels' values (and the Expression's). */
export type Row = { t: number } & Record<string, number | null>;
/** A StateBand row: state 0 Pending, 1 Confirmed, null none; `key` names the Event a segment
    belongs to (none for a Discarded one, which is not in the Event table). */
export type BandRow = { t: number; s: number | null; key?: string };
/** A mark on a lane, such as a lifecycle moment. */
export type MarkRow = { t: number; y: number };

/** The Event lanes take the bottom of the plot, one unit each; the Channels the top three units.
    An Expression with a unit of its own takes two units between them. */
export const CHANNEL_UNITS = 3;
export const SEPARATE_UNITS = 2;
export const EVENT_COLOR = 'var(--uc-series-2)';
export const EVENT_STATES = [
  { label: 'Pending', color: `color-mix(in srgb, ${EVENT_COLOR} 35%, transparent)` },
  { label: 'Confirmed', color: EVENT_COLOR },
] as const;
/** The four lifecycle moments, marked on each Rule's lane; Confirmed last, on top of a Pending
    start at the same moment. */
export const MARKS = [
  { name: 'Pending start', color: 'var(--u-color-warning)', moments: (l: Lifecycle) => [...l.events, ...l.discarded].map((e) => e.start) },
  { name: 'Ended', color: 'var(--u-color-text)', moments: (l: Lifecycle) => l.events.map((e) => e.end) },
  { name: 'Discarded', color: 'var(--u-color-danger)', moments: (l: Lifecycle) => l.discarded.map((d) => d.discarded) },
  { name: 'Confirmed', color: 'var(--u-color-success)', moments: (l: Lifecycle) => l.events.map((e) => e.confirmed) },
] as const;
/** Time advanced without a Sample (advanceTo), marked just above the lanes; a restart is a line. */
export const ADVANCE_COLOR = 'var(--u-color-text-muted)';
export const RESTART_COLOR = 'var(--u-color-text-secondary)';
/** A Truncated start or end: a halo behind the edge's mark, so it reads as more than a color. */
export const TRUNCATED = { name: 'Truncated', color: 'color-mix(in srgb, var(--u-color-text) 30%, transparent)' };
export const truncatedEdges = (l: Lifecycle) =>
  l.events.flatMap((e) => [...(e.truncatedStart ? [e.start] : []), ...(e.truncatedEnd ? [e.end] : [])]);
/** The chart palette without the Events' color, so a Channel never looks like a band. */
const CHANNEL_COLORS = [1, 3, 4, 5, 6].map((n) => `var(--uc-series-${n})`);
export const channelColor = (scenario: Scenario, channel: string) =>
  CHANNEL_COLORS[scenario.channels.indexOf(channel) % CHANNEL_COLORS.length];

/** The highlight of the Event latest(...) reads at the hovered time. */
export const LATEST_COLOR = 'var(--u-color-warning)';

/** The row key of the plotted Expression's values, and its color: the next one after the Channels'. */
export const EXPRESSION = 'expression';
export const expressionColor = (scenario: Scenario) => CHANNEL_COLORS[scenario.channels.length % CHANNEL_COLORS.length];
export const expressionValue = (v: number | null | undefined) => (v == null ? 'undefined' : String(Number(v.toPrecision(6))));

/** The plotted Expression's rows: its value at every moment of the run, at each Sample and each
    time advance, in time order. */
export function expressionRows(rows: Row[], expression: PlottedExpression | undefined, advances: number[], millis: number): Row[] {
  if (!expression) return [];
  return [
    ...rows.map((r) => ({ t: r.t, [EXPRESSION]: r[EXPRESSION] })),
    ...(expression.atAdvances ?? []).map((v, k) => ({ t: advances[k] / millis, [EXPRESSION]: v })),
  ].sort((a, b) => a.t - b.t) as Row[];
}

/** Where the plotted Expression is undefined, in the Scenario's unit: from each undefined moment to the next one. */
export function undefinedStretches(rows: Row[]): [number, number][] {
  const stretches: [number, number][] = [];
  rows.forEach((r, i) => {
    if (r[EXPRESSION] !== null) return;
    const to = rows[i + 1]?.t ?? r.t;
    const last = stretches.at(-1);
    if (last && last[1] === r.t) last[1] = to;
    else stretches.push([r.t, to]);
  });
  return stretches;
}

function niceStep(rough: number): number {
  const power = 10 ** Math.floor(Math.log10(rough));
  const m = rough / power;
  return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * power;
}

/** An axis with ticks over the values only, and a domain that puts them into the band from `below`
    to `below + units` of the plot's `total` units, counted from its bottom. */
export function bandAxis(rows: Row[], channels: string[], also: number[], below: number, units: number, total: number) {
  const values = [...rows.flatMap((r) => channels.map((c) => r[c])), ...also].filter(Number.isFinite) as number[];
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const step = niceStep((hi - lo) / 4 || Math.abs(hi) || 1);
  const first = Math.floor(lo / step);
  const last = Math.max(Math.ceil(hi / step), first + 1);
  const ticks = [];
  for (let k = first; k <= last; k++) ticks.push(Number((k * step).toPrecision(12)));
  const top = last * step + step / 2;
  const bottom = first * step - step / 2;
  const span = ((top - bottom) * total) / units;
  const from = bottom - (span * below) / total;
  const domain: [number, number] = [from, from + span];
  return { ticks, domain };
}
export type ChannelAxis = ReturnType<typeof bandAxis>;

/** A Rule's Events as StateBand rows: Pending from the start, Confirmed from then on, a hole after
    the end; a Discarded Pending Event is Pending until it was Discarded. */
export function bandRows(lifecycle: Lifecycle, millis: number): BandRow[] {
  return [
    ...lifecycle.events.flatMap((e) => [
      { t: e.start / millis, s: 0, key: eventKey(e) },
      { t: e.confirmed / millis, s: 1, key: eventKey(e) },
      { t: e.end / millis, s: null, key: eventKey(e) },
    ]),
    ...lifecycle.discarded.flatMap((d) => [
      { t: d.start / millis, s: 0 },
      { t: d.discarded / millis, s: null },
    ]),
  ].sort((a, b) => a.t - b.t);
}

/** Marks at the moments `moments` picks from each Rule's lifecycle, on that Rule's lane (the
    first Rule's lane on top). */
export const laneMarks = (rules: string[], lifecycle: Lifecycle, millis: number, moments: (l: Lifecycle) => readonly number[]): MarkRow[] =>
  rules.flatMap((rule, i) => moments(ofRule(lifecycle, rule)).map((t) => ({ t: t / millis, y: rules.length - i - 0.5 })));

/** What a Rule is at time t (in the Scenario's unit): Pending, Confirmed, the moment it Ended or
    was Discarded, or no Event. */
export function ruleState(lifecycle: Lifecycle, t: number, millis: number): string {
  const e = lifecycle.events.find((e) => e.start / millis <= t && t <= e.end / millis);
  if (e !== undefined) return t === e.end / millis ? 'Ended' : t < e.confirmed / millis ? 'Pending' : 'Confirmed';
  const d = lifecycle.discarded.find((d) => d.start / millis <= t && t <= d.discarded / millis);
  if (d !== undefined) return t === d.discarded / millis ? 'Discarded' : 'Pending';
  return 'no Event';
}

/** The Event latest(...) reads at time t: the most recently Confirmed one, running or Ended. */
export const latestEvent = (events: Event[], t: number, millis: number) => events.findLast((e) => e.confirmed / millis <= t);

/** What the chart shows: the shown Channels and Rules, and what the run returned. */
export type ChartModel = {
  scenario: Scenario;
  rows: Row[];
  lifecycle: Lifecycle;
  channels: string[];
  rules: string[];
  /** Told the key of the Event latest(...) reads at the hovered time; undefined when there is
      none. A state setter, so it stays the same function across renders. */
  hoverLatest: (key: string | undefined) => void;
  expression?: PlottedExpression;
  /** The plotted Expression's rows (expressionRows); none without one. */
  expressionRows: Row[];
  /** Timestamps time was advanced to without a Sample, and the restart, if any (ms). */
  advances: number[];
  restart?: number;
};

/** The Channels' axis of what is shown (with the plotted Expression and its threshold); it keeps
    its scale while Channels are hidden. */
export const channelAxisOf = ({ scenario, rows, rules, expression, expressionRows }: ChartModel): ChannelAxis => {
  const below = rules.length + (expression?.unit !== undefined ? SEPARATE_UNITS : 0);
  return expression && expression.unit === undefined
    ? bandAxis([...rows, ...expressionRows], [...scenario.channels, EXPRESSION], [expression.threshold], below, CHANNEL_UNITS, CHANNEL_UNITS + below)
    : bandAxis(rows, scenario.channels, [], below, CHANNEL_UNITS, CHANNEL_UNITS + below);
};
