// What EventChart draws, computed: the chart rows, the Rules' bands and lifecycle marks on their
// lanes, the axes that share the plot between the Channels (a band per scale), the Expressions
// with a unit of their own and the lanes, and the colors of it all. Imports only types and
// events.ts, so Node tests it.
import type { Event, PlottedExpression, Scenario, Threshold, Window } from './bridge';
import { eventKey, type Lifecycle, ofRule } from './events.ts';

/** A chart row: a Sample's time in the Scenario's unit and its Channels' values (and the Expressions'). */
export type Row = { t: number } & Record<string, number | null>;
/** A StateBand row: state 0 Pending, 1 Confirmed, null none; `key` names the Event a segment
    belongs to (none for a Discarded one, which is not in the Event table). */
export type BandRow = { t: number; s: number | null; key?: string };
/** A mark on a lane, such as a lifecycle moment. */
export type MarkRow = { t: number; y: number };

/** The Event lanes take the bottom of the plot, one unit each; each band of Channels three units
    above them, the first band on top. Expressions with a unit of their own take two units
    between the Channels and the lanes. */
const CHANNEL_UNITS = 3;
const SEPARATE_UNITS = 2;
/** The Events' colors (site.css): Pending a lighter tone of Confirmed in light, a darker one in
    dark, each at least 3:1 against the surface. */
export const EVENT_COLOR = 'var(--event-confirmed)';
export const EVENT_STATES = [
  { label: 'Pending', color: 'var(--event-pending)' },
  { label: 'Confirmed', color: EVENT_COLOR },
] as const;
/** The four lifecycle moments, marked on each Rule's lane; Confirmed last, on top of a Pending
    start at the same moment. Named for the moment, so no name is also a band's. */
export const MARKS = [
  { name: 'Went Pending', color: 'var(--u-color-warning)', moments: (l: Lifecycle) => [...l.events, ...l.discarded].map((e) => e.start) },
  { name: 'Ended at', color: 'var(--u-color-text)', moments: (l: Lifecycle) => l.events.map((e) => e.end) },
  { name: 'Discarded at', color: 'var(--u-color-danger)', moments: (l: Lifecycle) => l.discarded.map((d) => d.discarded) },
  { name: 'Confirmed at', color: 'var(--u-color-success)', moments: (l: Lifecycle) => l.events.map((e) => e.confirmed) },
] as const;

/** Whether some Event, or Discarded Pending Event, was Pending for a while: only then does the
    chart show a Pending stretch and a Went Pending mark apart from the Confirmed one. */
const somePending = (l: Lifecycle) =>
  l.events.some((e) => e.confirmed > e.start) || l.discarded.some((d) => d.discarded > d.start);

/** The key's bands and marks, in lifecycle order, of those the chart shows. */
export function keyOf(l: Lifecycle) {
  const pending = somePending(l);
  const mark = (name: (typeof MARKS)[number]['name']) => MARKS.find((m) => m.name === name)!;
  return {
    states: EVENT_STATES.filter((s) => s.label !== 'Pending' || pending),
    marks: [
      ...(pending ? [mark('Went Pending')] : []),
      mark('Confirmed at'),
      mark('Ended at'),
      ...(l.discarded.length > 0 ? [mark('Discarded at')] : []),
    ],
  };
}
/** Every lifecycle moment, for the ring in the surface's color that sets each mark off its band. */
export const allMoments = (l: Lifecycle) => MARKS.flatMap((m) => m.moments(l));
export const RING_COLOR = 'var(--u-color-surface)';
/** Time advanced without a Sample (advanceTo), marked just above the lanes; a restart is a line. */
export const ADVANCE_COLOR = 'var(--u-color-text-secondary)';
export const RESTART_COLOR = 'var(--u-color-text-secondary)';
/** A Truncated start or end: a halo behind the edge's mark, so it reads as more than a color. */
export const TRUNCATED = { name: 'Truncated', color: 'color-mix(in srgb, var(--u-color-text) 30%, transparent)' };
export const truncatedEdges = (l: Lifecycle) =>
  l.events.flatMap((e) => [...(e.truncatedStart ? [e.start] : []), ...(e.truncatedEnd ? [e.end] : [])]);
/** The chart palette without the Events' color, so a Channel never looks like a band; blue,
    violet and cyan first, since green and amber are close to the Confirmed and Went Pending
    marks (every Scenario has at most three Channels and Expressions together). */
const CHANNEL_COLORS = [1, 5, 6, 3, 4].map((n) => `var(--uc-series-${n})`);
export const channelColor = (scenario: Scenario, channel: string) =>
  CHANNEL_COLORS[scenario.channels.indexOf(channel) % CHANNEL_COLORS.length];

/** The highlight of the Event latest(...) reads at the hovered time. */
export const LATEST_COLOR = 'var(--u-color-warning)';
/** The time a within(...) counts after an Event. */
export const WINDOW_COLOR = 'var(--uc-series-4)';
/** A threshold line: the color charts gives a LimitLine of severity alarm. */
export const THRESHOLD_COLOR = 'var(--u-color-danger)';

/** The row key of the k-th plotted Expression's values, and its color: the next ones after the Channels'. */
export const expressionKey = (k: number) => `expression ${k}`;
export const expressionColor = (scenario: Scenario, k: number) =>
  CHANNEL_COLORS[(scenario.channels.length + k) % CHANNEL_COLORS.length];
export const expressionValue = (v: number | null | undefined) => (v == null ? 'undefined' : String(Number(v.toPrecision(6))));
/** An Expression with a unit of its own (a count of Events, a duration) is not on the Channels'
    scale: it gets a band of the plot and an axis of its own, between the Channels and the lanes. */
export const isSeparate = (expression: PlottedExpression) => expression.unit !== undefined;

/** The plotted Expressions' rows: their values at every moment of the run, at each Sample and
    each time advance, in time order. */
export function expressionRows(rows: Row[], expressions: PlottedExpression[], advances: number[], millis: number): Row[] {
  if (expressions.length === 0) return [];
  const keys = expressions.map((_, k) => expressionKey(k));
  return [
    ...rows.map((r) => Object.fromEntries([['t', r.t], ...keys.map((key) => [key, r[key]])])),
    ...advances.map((at, i) =>
      Object.fromEntries([['t', at / millis], ...expressions.map((x, k) => [keys[k], x.atAdvances?.[i] ?? null])]),
    ),
  ].sort((a, b) => a.t - b.t) as Row[];
}

/** Where a plotted Expression is undefined, in the Scenario's unit: from each moment one of
    them is undefined to the next moment. */
export function undefinedStretches(rows: Row[], count: number): [number, number][] {
  const stretches: [number, number][] = [];
  rows.forEach((r, i) => {
    let defined = true;
    for (let k = 0; k < count; k++) if (r[expressionKey(k)] === null) defined = false;
    if (defined) return;
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
function bandAxis(values: (number | null | undefined)[], below: number, units: number, total: number) {
  const finite = values.filter(Number.isFinite) as number[];
  const lo = Math.min(...finite);
  const hi = Math.max(...finite);
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
  // Where the band's middle lies, from the plot's top, for its axis title.
  const middle = `${(1 - (below + units / 2) / total) * 100}%`;
  return { ticks, domain, middle };
}

/** A band of the plot with the Channels of one scale, and its axis. */
export type ChannelAxis = ReturnType<typeof bandAxis> & { id: string; channels: string[] };

/** Channels whose largest values differ by more than this factor get bands of their own. */
const SCALE_FACTOR = 4;

/** The Channels in bands by scale, in their order: a Channel joins the first band whose
    Channels' largest absolute values are within SCALE_FACTOR of its own. A Pulse Channel is
    drawn as marks on the first band. */
export function channelBands(scenario: Scenario, rows: Row[]): string[][] {
  const bands: { channels: string[]; magnitude: number }[] = [];
  for (const c of scenario.channels) {
    if (scenario.pulse.includes(c)) continue;
    const magnitude = Math.max(...rows.map((r) => Math.abs(r[c] ?? 0))) || 1;
    const band = bands.find((b) => Math.max(b.magnitude, magnitude) / Math.min(b.magnitude, magnitude) <= SCALE_FACTOR);
    if (band) {
      band.channels.push(c);
      band.magnitude = Math.max(band.magnitude, magnitude);
    } else bands.push({ channels: [c], magnitude });
  }
  if (bands.length === 0) bands.push({ channels: [], magnitude: 1 });
  bands[0].channels.push(...scenario.pulse);
  return bands.map((b) => b.channels);
}

/** Whether some Sample lacks a value of the Channel. */
export const hasGaps = (rows: Row[], channel: string) => rows.some((r) => r[channel] == null);

/** The Channels' lines' rows: the Samples, and a row without values at each time advance (in
    the Scenario's unit), so a line breaks where time moved on without a Sample instead of
    running straight across. */
export const lineRows = (rows: Row[], advances: number[]): Row[] =>
  advances.length === 0 ? rows : [...rows, ...advances.map((t) => ({ t }) as Row)].sort((a, b) => a.t - b.t);

/** The Channel's value where rbed holds it (in rbed, a Channel keeps its last value): in a
    Sample that lacks it, and at a time advance (in the Scenario's unit), from the Sample
    before. For a Channel some Samples lack, at every Sample and advance (none before the
    first); otherwise only from the Sample before an advance through the advances, so only the
    held stretches are drawn. */
export function heldRows(rows: Row[], channel: string, advances: number[] = []): Row[] {
  const gaps = hasGaps(rows, channel);
  const moments = [...rows.map((r) => ({ t: r.t, value: r[channel] ?? null, sample: true })), ...advances.map((t) => ({ t, value: null, sample: false }))].sort(
    (a, b) => a.t - b.t,
  );
  let held: number | null = null;
  return moments.map((m, i) => {
    held = m.value ?? held;
    const silent = !m.sample || moments[i + 1]?.sample === false;
    return { t: m.t, [channel]: gaps || silent ? held : null } as Row;
  });
}

/** The Channels whose held values the chart draws as a thin step line: those some Samples
    lack, and every one where time was advanced without a Sample; never a Pulse Channel, which
    holds nothing. */
export const heldChannels = (scenario: Scenario, rows: Row[], advances: number[]) =>
  scenario.channels.filter((c) => !scenario.pulse.includes(c) && (advances.length > 0 || hasGaps(rows, c)));

/** What the readout says of a Channel at time t (in the Scenario's unit): the value of the
    Sample at t; where there is none (an advance) or the Sample lacks the Channel, the value rbed
    holds, "(kept)", or "–" before the first; a Pulse Channel holds nothing, so "–". */
export function readoutValue(scenario: Scenario, rows: Row[], channel: string, t: number): string {
  const value = rows.find((r) => r.t === t)?.[channel];
  if (value != null) return String(value);
  if (scenario.pulse.includes(channel)) return '–';
  const kept = rows.findLast((r) => r.t <= t && r[channel] != null)?.[channel];
  return kept != null ? `${kept} (kept)` : '–';
}

/** The plot's units: the lanes, the band of the Expressions with a unit, each band of Channels. */
export const plotUnits = (lanes: number, separate: boolean, bands: number) =>
  lanes + (separate ? SEPARATE_UNITS : 0) + bands * CHANNEL_UNITS;

/** The Channels' axes of what is shown, one per band (with the Expressions on the Channels'
    scale and the thresholds on the first, or on their Channel's); they keep their scale while
    Channels are hidden. */
export function channelAxesOf({ scenario, rows, rules, expressions, expressionRows, thresholds }: ChartModel): ChannelAxis[] {
  const bands = channelBands(scenario, rows);
  const separate = expressions.some(isSeparate);
  const total = plotUnits(rules.length, separate, bands.length);
  return bands.map((channels, b) => {
    const below = total - (b + 1) * CHANNEL_UNITS;
    const values = [
      ...rows.flatMap((r) => channels.map((c) => r[c])),
      ...thresholds.filter((t) => channels.includes(t.channel)).map((t) => t.value),
      ...(b > 0
        ? []
        : expressions.flatMap((x, k) =>
            isSeparate(x) ? [] : [...expressionRows.map((r) => r[expressionKey(k)]), x.threshold],
          )),
    ];
    return { id: `band${b}`, channels, ...bandAxis(values, below, CHANNEL_UNITS, total) };
  });
}

/** The axis of the Expressions with a unit of their own, between the Channels and the lanes. */
export function separateAxisOf({ rules, expressions, expressionRows }: ChartModel, bands: number) {
  const values = expressions.flatMap((x, k) => (isSeparate(x) ? [...expressionRows.map((r) => r[expressionKey(k)]), x.threshold] : []));
  return bandAxis(values, rules.length, SEPARATE_UNITS, plotUnits(rules.length, true, bands));
}

/** The time axis' domain: the Samples' span with a little room at both ends, so a mark at the
    first or last Sample does not reach into the axes' labels; and `labels`, a share of the span
    more on the right for threshold labels (labelRoom). */
export function timeDomain(rows: Row[], labels = 0): [number, number] {
  const first = rows[0].t;
  const last = rows[rows.length - 1].t;
  const room = (last - first) * 0.04;
  return [first - room, last + room + (last - first) * labels];
}

/** The share of the time span to leave right of the last Sample for the threshold labels of the
    first band's axis: on a phone, charts puts a label wider than the axis' column (even "> 100")
    into the plot at its right end, where it would hide the data. */
export function labelRoom(model: ChartModel, axes: ChannelAxis[], narrow: boolean): number {
  const widest = Math.max(0, ...thresholdLines(model.thresholds, axes, model.channels, narrow).filter((l) => l.axis === axes[0].id).map((l) => l.label.length));
  return narrow && widest > 0 ? (widest * 6.5 + 12) / 250 : 0;
}

/** The time axis' ticks: at the Samples where there are few, each at a whole unit (so no tick
    falls between them); otherwise the chart's own. */
export function timeTicks(rows: Row[]): number[] | undefined {
  return rows.length <= 12 && rows.every((r) => Number.isInteger(r.t)) ? rows.map((r) => r.t) : undefined;
}

/** Where the plotted Expressions are undefined, shaded; the key names it. */
export const UNDEFINED_COLOR = 'var(--u-color-text-muted)';

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

/** Lane i of `lanes` (the first Rule's on top), on the lanes axis: where its band lies. On a
    narrow chart the band takes the lane's lower half, and the Rule's name hangs from the
    lane's top, `name`, over its upper half. A thin strip just above the band, `strip`, shows
    the time a within(...) of the Rule counts. */
export function lane(i: number, lanes: number, narrow: boolean) {
  const bottom = lanes - i - 1;
  return narrow
    ? { from: bottom + 0.05, to: bottom + 0.5, name: bottom + 1, strip: [bottom + 0.53, bottom + 0.62] }
    : { from: bottom + 0.1, to: bottom + 0.9, name: bottom + 0.9, strip: [bottom + 0.92, bottom + 1.04] };
}

/** Marks at the moments `moments` picks from each Rule's lifecycle, in the middle of that Rule's band. */
export const laneMarks = (
  rules: string[],
  lifecycle: Lifecycle,
  millis: number,
  moments: (l: Lifecycle) => readonly number[],
  narrow: boolean,
): MarkRow[] =>
  rules.flatMap((rule, i) => {
    const { from, to } = lane(i, rules.length, narrow);
    return moments(ofRule(lifecycle, rule)).map((t) => ({ t: t / millis, y: (from + to) / 2 }));
  });

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

/** The comparison in a threshold's condition: "when >" gives ">". */
const COMPARISON = /[<>≤≥]=?/;

/** A threshold in words, naming its Rule, Channel and value: "Tachycardia when hr > 100". */
export const thresholdText = (t: Threshold) =>
  `${t.rule} ${COMPARISON.test(t.condition) ? t.condition.replace(COMPARISON, `${t.channel} $&`) : `${t.condition} ${t.channel}`} ${t.value}`;

/** A line the chart draws at a threshold: its axis, its value, its label (empty where the line
    lies too close to a labelled one to tell them apart; the key names it) and the thresholds
    it stands for. */
export type ThresholdLine = { axis: string; value: number; label: string; thresholds: Threshold[] };

/** Labels of lines closer than this share of their axis' span are left out. */
const LABEL_SPACING = 0.04;

/** The lines of the thresholds of the shown Channels: one per axis and value, labelled with
    the comparison alone ("> 100"; the key says the rest), or with the Channel too where the
    axis has several shown Channels ("temp > 26"), but on a phone (`narrow`), where the column
    is narrow; from the top, a line too close to a labelled one has no label. */
export function thresholdLines(thresholds: Threshold[], axes: ChannelAxis[], channels: string[], narrow = false): ThresholdLine[] {
  const lines: ThresholdLine[] = [];
  for (const t of thresholds) {
    if (!channels.includes(t.channel)) continue;
    const axis = axes.find((a) => a.channels.includes(t.channel)) ?? axes[0];
    const line = lines.find((l) => l.axis === axis.id && l.value === t.value);
    if (line) line.thresholds.push(t);
    else lines.push({ axis: axis.id, value: t.value, label: '', thresholds: [t] });
  }
  const labelled: ThresholdLine[] = [];
  for (const line of [...lines].sort((a, b) => b.value - a.value)) {
    const axis = axes.find((a) => a.id === line.axis)!;
    const span = axis.ticks[axis.ticks.length - 1] - axis.ticks[0] || 1;
    if (labelled.some((l) => l.axis === line.axis && Math.abs(l.value - line.value) <= span * LABEL_SPACING)) continue;
    const several = !narrow && axis.channels.filter((c) => channels.includes(c)).length > 1;
    const parts = line.thresholds.map((t) => `${several ? `${t.channel} ` : ''}${t.condition.match(COMPARISON)?.[0] ?? t.condition} ${t.value}`);
    line.label = [...new Set(parts)].join(', ');
    labelled.push(line);
  }
  return lines;
}

/** An axis' tick, written with as many decimals as it has: 0.5 stays 0.5, 1500 is 1,500. */
export const tickText = (v: number) => v.toLocaleString('en-US', { maximumFractionDigits: 6 });

/** Each Rule's within(...) windows as StateBand rows of one state, over the time it counts after
    each Event of the Rule it follows (in the Scenario's unit); overlapping windows merge. A
    Detector restarted without History at `forgetsAt` (ms) knows no earlier Event, so a window
    opened before it ends there. */
export function windowRows(windows: Window[], lifecycle: Lifecycle, millis: number, forgetsAt?: number): Map<string, BandRow[]> {
  const byRule = new Map<string, BandRow[]>();
  const until = (end: number, to: number) => (forgetsAt !== undefined && end < forgetsAt ? Math.min(to, forgetsAt) : to);
  for (const rule of new Set(windows.map((w) => w.rule))) {
    const stretches = windows
      .filter((w) => w.rule === rule)
      .flatMap((w) => ofRule(lifecycle, w.after).events.map((e) => [e.end / millis, until(e.end, e.end + w.millis) / millis]))
      .sort((a, b) => a[0] - b[0]);
    const merged: number[][] = [];
    for (const [from, to] of stretches) {
      const last = merged.at(-1);
      if (last && from <= last[1]) last[1] = Math.max(last[1], to);
      else merged.push([from, to]);
    }
    byRule.set(
      rule,
      merged.flatMap(([from, to]) => [
        { t: from, s: 0 },
        { t: to, s: null },
      ]),
    );
  }
  return byRule;
}

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
  expressions: PlottedExpression[];
  /** The plotted Expressions' rows (expressionRows); none without one. */
  expressionRows: Row[];
  thresholds: Threshold[];
  windows: Window[];
  /** Timestamps time was advanced to without a Sample, and the restart, if any (ms). */
  advances: number[];
  restart?: number;
  /** Whether the Detector was restarted without History (windowRows). */
  restartForgets?: boolean;
};
