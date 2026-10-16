// A Scenario live, with the tools every page that shows one offers: its Channels and Events on
// one time axis with a hover readout, a control per parameter, a reset, toggles for each Rule
// and Channel, the Java source with the current values, and the Event table with each Event's
// Aggregates, whose rows and the chart's bands select each other; a selected Event's Aggregates
// are shown in full under the chart. Non-default values live in the address. The Detector runs
// again on every change. A Scenario that plots an Expression adds it as a dashed line, with the
// threshold its Rule compares it to and the stretches where it is undefined; on an axis and a
// band of the plot of its own if it has a unit of its own (a count of Events, a duration). On a Scenario
// whose Rules read latest(...), hovering highlights the Event latest(...) reads at that time.
import { LimitBand, LimitLine, Tooltip, type TooltipHit, useChart } from '@umriss-ui/charts';
import { Alert, Button, Checkbox, FormField, RadioGroup, Slider, VisuallyHidden } from '@umriss-ui/core';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type Aggregates, aggregatesLine, formatNumber } from './aggregates.ts';
import { type Discard, type Event, type PlottedExpression, type Scenario, run, source } from './bridge';
import { CodeBlock } from './CodeBlock';
import { fromQuery, toQuery, usedParameters } from './parameters';

type Row = { t: number } & Record<string, number | null>;
/** A StateBand row: state 0 Pending, 1 Confirmed, null none; `key` names the Event a segment
    belongs to (none for a Discarded one, which is not in the Event table). */
type BandRow = { t: number; s: number | null; key?: string };
/** A lifecycle mark, on its Rule's lane. */
type MarkRow = { t: number; y: number };

/** The Event lanes take the bottom of the plot, one unit each; the Channels the top three units.
    An Expression with a unit of its own takes two units between them. */
const COURSE_UNITS = 3;
const OWN_UNITS = 2;
const EVENT_COLOUR = 'var(--uc-series-2)';
const EVENT_STATES = [
  { label: 'Pending', color: `color-mix(in srgb, ${EVENT_COLOUR} 35%, transparent)` },
  { label: 'Confirmed', color: EVENT_COLOUR },
] as const;
/** The four lifecycle moments, marked on each Rule's lane; Confirmed last, on top of a Pending
    start at the same moment. */
const MARKS = [
  { name: 'Pending start', colour: 'var(--u-color-warning)', moments: (l: Lifecycle) => [...l.events, ...l.discarded].map((e) => e.start) },
  { name: 'Ended', colour: 'var(--u-color-text)', moments: (l: Lifecycle) => l.events.map((e) => e.end) },
  { name: 'Discarded', colour: 'var(--u-color-danger)', moments: (l: Lifecycle) => l.discarded.map((d) => d.discarded) },
  { name: 'Confirmed', colour: 'var(--u-color-success)', moments: (l: Lifecycle) => l.events.map((e) => e.confirmed) },
] as const;
/** A Truncated start or end: a halo behind the edge's mark, so it reads as more than a colour. */
const TRUNCATED = { name: 'Truncated', colour: 'color-mix(in srgb, var(--u-color-text) 30%, transparent)' };
const truncatedEdges = (l: Lifecycle) =>
  l.events.flatMap((e) => [...(e.truncatedStart ? [e.start] : []), ...(e.truncatedEnd ? [e.end] : [])]);
/** The chart palette without the Events' colour, so a Channel never looks like a band. */
const CHANNEL_COLOURS = [1, 3, 4, 5, 6].map((n) => `var(--uc-series-${n})`);
const channelColour = (scenario: Scenario, channel: string) =>
  CHANNEL_COLOURS[scenario.channels.indexOf(channel) % CHANNEL_COLOURS.length];

const eventKey = (e: Event) => `${e.name} ${e.start}`;
/** The highlight of the Event latest(...) reads at the hovered time. */
const LATEST_COLOUR = 'var(--u-color-warning)';

/** The row key of the plotted Expression's values, and its colour: the next one after the Channels'. */
const EXPRESSION = 'expression';
const expressionColour = (scenario: Scenario) => CHANNEL_COLOURS[scenario.channels.length % CHANNEL_COLOURS.length];
const expressionValue = (v: number | null | undefined) => (v == null ? 'undefined' : String(Number(v.toPrecision(6))));

/** Where the plotted Expression is undefined, in the Scenario's unit: from each undefined Sample to the next one. */
function undefinedStretches(rows: Row[]): [number, number][] {
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
function bandAxis(rows: Row[], channels: string[], also: number[], below: number, units: number, total: number) {
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

/** Events and Discarded Pending Events, of every Rule or of one. */
type Lifecycle = { events: Event[]; discarded: Discard[] };

const ofRule = ({ events, discarded }: Lifecycle, rule: string): Lifecycle => ({
  events: events.filter((e) => e.name === rule),
  discarded: discarded.filter((d) => d.name === rule),
});

/** A Rule's Events as StateBand rows: Pending from the start, Confirmed from then on, a hole after
    the end; a Discarded Pending Event is Pending until it was Discarded. */
function bandRows(lifecycle: Lifecycle, millis: number): BandRow[] {
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

/** What a Rule is at time t (in the Scenario's unit): Pending, Confirmed, the moment it Ended or
    was Discarded, or no Event. */
function ruleState(lifecycle: Lifecycle, t: number, millis: number): string {
  const e = lifecycle.events.find((e) => e.start / millis <= t && t <= e.end / millis);
  if (e !== undefined) return t === e.end / millis ? 'Ended' : t < e.confirmed / millis ? 'Pending' : 'Confirmed';
  const d = lifecycle.discarded.find((d) => d.start / millis <= t && t <= d.discarded / millis);
  if (d !== undefined) return t === d.discarded / millis ? 'Discarded' : 'Pending';
  return 'no Event';
}

/** The Event latest(...) reads at time t: the most recently Confirmed one, running or Ended. */
const latestEvent = (events: Event[], t: number, millis: number) => events.findLast((e) => e.confirmed / millis <= t);

type Shown = {
  scenario: Scenario;
  rows: Row[];
  lifecycle: Lifecycle;
  channels: string[];
  rules: string[];
  /** Told the key of the Event latest(...) reads at the hovered time; undefined when there is
      none. A state setter, so it stays the same function across renders. */
  hoverLatest: (key: string | undefined) => void;
  expression?: PlottedExpression;
};

/** The tooltip: every shown Channel's value and Rule's state at the hovered time. */
function Readout({ shown, t }: { shown: Shown; t: number }) {
  const { scenario, rows, lifecycle, channels, rules, expression } = shown;
  const row = rows.findLast((r) => r.t <= t) ?? rows[0];
  const { millis } = scenario.timeUnit;
  const latest = scenario.latest && latestEvent(ofRule(lifecycle, scenario.latest).events, t, millis);
  const latestKey = latest ? eventKey(latest) : undefined;
  // The chart highlights that Event; there is no hover callback, so the readout reports it.
  const { hoverLatest } = shown;
  useEffect(() => hoverLatest(latestKey), [hoverLatest, latestKey]);
  useEffect(() => () => hoverLatest(undefined), [hoverLatest]);
  const line = (name: string, colour: string, value: string) => (
    <div className="uc-tooltip-row" key={name}>
      <span className="uc-tooltip-chip" style={{ background: colour }} />
      <span className="uc-tooltip-name">{name}</span>
      <span className="uc-tooltip-value">{value}</span>
    </div>
  );
  return (
    <>
      <div className="uc-tooltip-head">
        {t} {scenario.timeUnit.name}
      </div>
      <div className="uc-tooltip-list">
        {channels.map((c) => line(c, channelColour(scenario, c), String(row[c] ?? '–')))}
        {expression &&
          line(
            expression.name,
            expressionColour(scenario),
            expressionValue(row[EXPRESSION]) + (expression.unit ? ` ${expression.unit}` : ''),
          )}
        {rules.map((r) => line(r, EVENT_COLOUR, ruleState(ofRule(lifecycle, r), t, millis)))}
        {scenario.latest &&
          line(
            `latest(${scenario.latest}) reads`,
            LATEST_COLOUR,
            latest ? `${latest.start / millis} to ${latest.end / millis} ${scenario.timeUnit.name}` : 'no Event yet',
          )}
      </div>
    </>
  );
}

function EventChart({
  shown,
  selected,
  latest,
  select,
}: {
  shown: Shown;
  selected?: Event;
  /** The Event latest(...) reads at the hovered time. */
  latest?: Event;
  select: (key: string | undefined) => void;
}) {
  const { scenario, rows, lifecycle, channels, rules, expression } = shown;
  const { name: unit, millis } = scenario.timeUnit;
  // An Expression with a unit of its own (a count of Events, a duration) is not on the Channels'
  // scale: it gets a band of the plot and an axis of its own, between the Channels and the lanes.
  const own = expression?.unit !== undefined ? expression : undefined;
  const onCourse = own ? undefined : expression;
  // A click (or Enter) on the chart selects the Event under it, if any.
  const { Chart, XAxis, YAxis, Line, Scatter, StateBand } = useChart(rows, {
    onSelect: (hit) => {
      const band = hit.points.find((p) => p.segment !== undefined && (p.datum as unknown as BandRow).s !== null);
      select(band && (band.datum as unknown as BandRow).key);
    },
  });
  // The tooltip's render is compared by its source text, so it reads the data through a ref.
  const live = useRef(shown);
  live.current = shown;
  const lanes = rules.length;
  const below = lanes + (own ? OWN_UNITS : 0);
  const total = COURSE_UNITS + below;
  // The axis keeps its scale while Channels are hidden.
  const course = onCourse
    ? bandAxis(rows, [...scenario.channels, EXPRESSION], [onCourse.threshold], below, COURSE_UNITS, total)
    : bandAxis(rows, scenario.channels, [], below, COURSE_UNITS, total);
  const ownAxis = own && bandAxis(rows, [EXPRESSION], [own.threshold], lanes, OWN_UNITS, total);
  return (
    <Chart
      height={own ? 380 : 300}
      ariaLabel={`${scenario.channels.join(', ')} over time in ${unit}, with the Events of ${scenario.rules.join(', ')} as bands, Pending then Confirmed, and their lifecycle marked`}
    >
      <XAxis value="t" domain={[rows[0].t, rows[rows.length - 1].t]} label={`Time (${unit})`} />
      <YAxis domain={course.domain} ticks={course.ticks} label={channels.join(', ')} />
      {own && ownAxis && <YAxis id="own" grid={false} domain={ownAxis.domain} ticks={ownAxis.ticks} />}
      {/* Lane i (first Rule on top) spans lanes - i - 1 .. lanes - i. The lane names change with
          the shown Rules; the chart's key remounts it then, since tickFormat is compared by text. */}
      <YAxis
        id="lanes"
        position="right"
        grid={false}
        domain={[0, total]}
        ticks={rules.map((_, i) => lanes - i - 0.5)}
        tickFormat={(v) => rules[Math.round(lanes - 0.5 - v)] ?? ''}
      />
      {expression &&
        undefinedStretches(rows).map(([from, to]) => (
          <LimitBand key={from} orientation="x" from={from} to={to} color="var(--u-color-text-muted)" label="undefined" />
        ))}
      {onCourse && <LimitLine value={onCourse.threshold} label={`threshold ${onCourse.threshold}`} />}
      {own && <LimitLine axisId="own" value={own.threshold} label={`threshold ${own.threshold} ${own.unit}`} />}
      {selected && (
        <LimitBand orientation="x" from={selected.start / millis} to={selected.end / millis} color="var(--u-color-accent)" />
      )}
      {latest && <LimitBand orientation="x" from={latest.start / millis} to={latest.end / millis} color={LATEST_COLOUR} />}
      {rules.map((rule, i) => (
        <StateBand<BandRow>
          key={rule}
          data={bandRows(ofRule(lifecycle, rule), millis)}
          value="s"
          states={EVENT_STATES}
          yAxisId="lanes"
          laneFrom={lanes - i - 0.9}
          laneTo={lanes - i - 0.1}
          name={rule}
        />
      ))}
      <Scatter<MarkRow>
        data={rules.flatMap((rule, i) => truncatedEdges(ofRule(lifecycle, rule)).map((t) => ({ t: t / millis, y: lanes - i - 0.5 })))}
        value="y"
        yAxisId="lanes"
        name={TRUNCATED.name}
        color={TRUNCATED.colour}
        radius={9}
      />
      {MARKS.map((m) => (
        <Scatter<MarkRow>
          key={m.name}
          data={rules.flatMap((rule, i) => m.moments(ofRule(lifecycle, rule)).map((t) => ({ t: t / millis, y: lanes - i - 0.5 })))}
          value="y"
          yAxisId="lanes"
          name={m.name}
          color={m.colour}
          radius={4}
        />
      ))}
      {/* A Pulse Channel has no value between its Samples, so it is drawn as marks, not a line. */}
      {channels.map((c) =>
        scenario.pulse.includes(c) ? (
          <Scatter key={c} value={c} name={c} color={channelColour(scenario, c)} radius={5} />
        ) : (
          <Line key={c} value={c} name={c} color={channelColour(scenario, c)} markers="always" />
        ),
      )}
      {onCourse && (
        <Line value={EXPRESSION} name={onCourse.name} color={expressionColour(scenario)} dash={[6, 4]} markers="always" />
      )}
      {own && <Line value={EXPRESSION} name={own.name} color={expressionColour(scenario)} dash={[6, 4]} yAxisId="own" />}
      <Tooltip mode="x" render={(hit: TooltipHit<Row>) => <Readout shown={live.current} t={hit.xValue} />} />
    </Chart>
  );
}

/** The plotted Expression in words: what it is compared to and where it is undefined. */
function expressionSummary(expression: PlottedExpression | undefined, rows: Row[], unit: string): string {
  if (!expression) return '';
  const stretches = undefinedStretches(rows).map(([from, to]) => `from ${from} to ${to} ${unit}`);
  return (
    ` The dashed line is ${expression.name}, compared with the threshold ${expression.threshold}` +
    (stretches.length === 0 ? '.' : `; it is undefined ${stretches.join(' and ')}.`)
  );
}

/** What the bands' colours and the marks mean; the text alternative says it in words. */
function LifecycleKey() {
  const item = (label: string, colour: string, mark: boolean) => (
    <span key={label}>
      <span className={mark ? 'swatch mark' : 'swatch'} style={{ background: colour }} />
      {label}
    </span>
  );
  return (
    <div className="lifecycleKey" aria-hidden="true">
      <span>Bands:</span>
      {EVENT_STATES.map((s) => item(s.label, s.color, false))}
      <span>Marks:</span>
      {MARKS.map((m) => item(m.name, m.colour, true))}
      <span>
        <span className="swatch mark halo" style={{ background: TRUNCATED.colour }} />
        Truncated start or end
      </span>
    </div>
  );
}

/** The chart's text alternative: its Events and Discarded Pending Events in words. */
function EventSummary({ scenario, lifecycle, also = '' }: { scenario: Scenario; lifecycle: Lifecycle; also?: string }) {
  const { name: unit, millis } = scenario.timeUnit;
  const { events, discarded } = lifecycle;
  const at = (ms: number) => `${ms / millis} ${unit}`;
  const count = (n: number, one: string, many: string) => (n === 1 ? `one ${one}` : `${n} ${many}`);
  return (
    <VisuallyHidden as="p">
      {events.length === 0 && discarded.length === 0
        ? 'The chart shows no Event.'
        : `The chart shows ${[
            events.length > 0 && count(events.length, 'Event', 'Events'),
            discarded.length > 0 && count(discarded.length, 'Discarded Pending Event', 'Discarded Pending Events'),
          ]
            .filter(Boolean)
            .join(' and ')}: ` +
          [
            ...events.map(
              (e) =>
                `${e.name} Pending from ${at(e.start)}, Confirmed at ${at(e.confirmed)}, Ended at ${at(e.end)}` +
                (e.truncatedStart ? ', start Truncated' : '') +
                (e.truncatedEnd ? ', end Truncated' : ''),
            ),
            ...discarded.map((d) => `${d.name} Pending from ${at(d.start)}, Discarded at ${at(d.discarded)}`),
          ].join('; ') +
          '.'}
      {also}
    </VisuallyHidden>
  );
}

/** The Pending Events that were Discarded; no table while there is none. */
function DiscardedTable({ scenario, discarded }: { scenario: Scenario; discarded: Discard[] }) {
  const { name: unit, millis } = scenario.timeUnit;
  if (discarded.length === 0) return null;
  return (
    <div className="tableScroll">
      <table className="eventTable" id="discarded">
        <caption>Discarded Pending Events</caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Start ({unit})</th>
            <th scope="col">Discarded ({unit})</th>
          </tr>
        </thead>
        <tbody>
          {discarded.map((d) => (
            <tr key={`${d.name} ${d.start}`}>
              <td>{d.name}</td>
              <td>{d.start / millis}</td>
              <td>{d.discarded / millis}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const AGGREGATE_COMPONENTS: [Exclude<keyof Aggregates, 'count'>, string][] = [
  ['min', 'min'],
  ['max', 'max'],
  ['mean', 'mean'],
  ['timeWeightedMean', 'time-weighted mean'],
  ['integral', 'integral (value × s)'],
];

/** Every Aggregate of the selected Event, a column per Channel. */
function AggregatesPanel({ scenario, event }: { scenario: Scenario; event: Event }) {
  const { name: unit, millis } = scenario.timeUnit;
  const channels = Object.keys(event.aggregates);
  return (
    <div className="tableScroll aggregatesPanel">
      <table className="eventTable">
        <caption>
          Aggregates of {event.name}, {event.start / millis} to {event.end / millis} {unit}
        </caption>
        <thead>
          <tr>
            <td />
            {channels.map((c) => (
              <th scope="col" key={c}>
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {AGGREGATE_COMPONENTS.map(([component, label]) => (
            <tr key={component}>
              <th scope="row">{label}</th>
              {channels.map((c) => (
                <td key={c}>{formatNumber(event.aggregates[c][component])}</td>
              ))}
            </tr>
          ))}
          <tr>
            <th scope="row">count</th>
            {channels.map((c) => (
              <td key={c}>{event.aggregates[c].count}</td>
            ))}
          </tr>
        </tbody>
      </table>
    </div>
  );
}

function EventTable({
  scenario,
  events,
  selected,
  select,
}: {
  scenario: Scenario;
  events: Event[];
  selected?: string;
  select: (key: string | undefined) => void;
}) {
  const { name: unit, millis } = scenario.timeUnit;
  const notes = events.some((e) => e.note !== undefined);
  return (
    <div className="tableScroll">
      <table className="eventTable" id="events">
        <caption>Events</caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Start ({unit})</th>
            <th scope="col">Confirmed ({unit})</th>
            <th scope="col">End ({unit})</th>
            <th scope="col">Truncated</th>
            {notes && <th scope="col">Note</th>}
          </tr>
        </thead>
        <tbody>
          {events.length === 0 && (
            <tr>
              <td colSpan={5}>No Event</td>
            </tr>
          )}
          {events.map((e) => {
            const key = eventKey(e);
            return [
              <tr key={key} data-selected={key === selected || undefined}>
                <td>
                  <button
                    type="button"
                    className="eventPick"
                    aria-pressed={key === selected}
                    onClick={() => select(key === selected ? undefined : key)}
                  >
                    {e.name}
                  </button>
                </td>
                <td>{e.start / millis}</td>
                <td>{e.confirmed / millis}</td>
                <td>{e.end / millis}</td>
                <td>{[e.truncatedStart && 'start', e.truncatedEnd && 'end'].filter(Boolean).join(', ')}</td>
                {notes && <td>{e.note}</td>}
              </tr>,
              // The Aggregates under their Event, so the table stays five columns wide on a phone.
              <tr key={`${key} aggregates`} className="aggregateRow" data-selected={key === selected || undefined}>
                <td colSpan={notes ? 6 : 5}>
                  <span className="aggregateLabel">Aggregates</span>{' '}
                  {Object.entries(e.aggregates).map(([channel, a], i) => [
                    i > 0 && '; ',
                    <span key={channel}>{aggregatesLine(channel, a)}</span>,
                  ])}
                </td>
              </tr>,
            ];
          })}
        </tbody>
      </table>
    </div>
  );
}

export function ScenarioView({ scenario }: { scenario: Scenario }) {
  const defaults = Object.fromEntries(scenario.parameters.map((p) => [p.id, p.default]));
  const [parameters, setParameters] = useState(() => fromQuery(window.location.search, scenario.parameters));
  const [hidden, setHidden] = useState<string[]>([]);
  const [selected, select] = useState<string>();
  const [hoveredLatest, hoverLatest] = useState<string>();
  const result = useMemo(() => run(scenario.id, parameters), [scenario.id, parameters]);
  const rows = useMemo<Row[]>(
    () =>
      'error' in result
        ? []
        : result.samples.map((s, i) => ({
            t: s.timestamp / scenario.timeUnit.millis,
            ...s.values,
            ...(result.expression && { [EXPRESSION]: result.expression.values[i] }),
          })),
    [result, scenario.timeUnit.millis],
  );

  // The address follows the values a moment after the last change: browsers limit how often a
  // page may replace its address, and a slider moves many times a second.
  useEffect(() => {
    const t = window.setTimeout(() => {
      const { pathname, search, hash } = window.location;
      const next = toQuery(search, parameters, scenario.parameters);
      if (next !== search) window.history.replaceState(window.history.state, '', pathname + next + hash);
    }, 250);
    return () => window.clearTimeout(t);
  }, [parameters, scenario.parameters]);

  // A parameter only an unchosen option uses has no control.
  const used = usedParameters(scenario.source, scenario.parameters, parameters);
  const atDefaults = scenario.parameters.every((p) => Object.is(parameters[p.id], p.default));
  const toggle = (name: string) =>
    setHidden(hidden.includes(name) ? hidden.filter((n) => n !== name) : [...hidden, name]);
  const visibility = (names: string[], colour: (name: string) => string) =>
    names.map((name) => (
      <Checkbox
        key={name}
        checked={!hidden.includes(name)}
        onChange={() => toggle(name)}
        label={
          <>
            <span className="swatch" style={{ background: colour(name) }} />
            {name}
          </>
        }
      />
    ));

  let stage;
  if ('error' in result) {
    stage = (
      <Alert tone="danger" title="rbed rejects these parameters">
        {result.error}
      </Alert>
    );
  } else {
    const shown: Shown = {
      scenario,
      rows,
      lifecycle: result,
      channels: scenario.channels.filter((c) => !hidden.includes(c)),
      rules: scenario.rules.filter((r) => !hidden.includes(r)),
      hoverLatest,
      expression: result.expression,
    };
    const event = result.events.find((e) => eventKey(e) === selected);
    stage = (
      <>
        <EventChart
          key={`${scenario.id} ${shown.rules.join(' ')}`}
          shown={shown}
          selected={event}
          latest={result.events.find((e) => eventKey(e) === hoveredLatest)}
          select={select}
        />
        <LifecycleKey />
        <EventSummary
          scenario={scenario}
          lifecycle={result}
          also={expressionSummary(result.expression, rows, scenario.timeUnit.name)}
        />
        {event && <AggregatesPanel scenario={scenario} event={event} />}
      </>
    );
  }

  return (
    <>
      <div className="configurator">
        <div className="configuratorStage">{stage}</div>
        <div className="configuratorPanel">
          <div className="configuratorHead">
            <span>Parameters</span>
            <Button variant="ghost" size="sm" disabled={atDefaults} onClick={() => setParameters(defaults)}>
              Reset
            </Button>
          </div>
          {scenario.parameters.map(
            (p) =>
              p.options && (
                <FormField key={p.id} label={p.label}>
                  <RadioGroup
                    name={p.id}
                    options={p.options.map((o, i) => ({ value: String(i), label: o.label }))}
                    value={String(parameters[p.id])}
                    onChange={(v) => setParameters({ ...parameters, [p.id]: Number(v) })}
                  />
                </FormField>
              ),
          )}
          {scenario.parameters.filter((p) => !p.options && used.includes(p.id)).map((p) => (
            <FormField key={p.id} label={p.label}>
              <Slider
                name={p.id}
                min={p.min}
                max={p.max}
                step={p.step}
                value={parameters[p.id]}
                onChange={(v) => setParameters({ ...parameters, [p.id]: v })}
                format={(v) => `${v} ${p.unit}`}
                showValue
              />
            </FormField>
          ))}
          <fieldset className="configuratorShow">
            <legend>Show</legend>
            {visibility(scenario.rules, () => EVENT_COLOUR)}
            {visibility(scenario.channels, (c) => channelColour(scenario, c))}
          </fieldset>
        </div>
        <div className="configuratorCode">
          <CodeBlock language="Java" source={source(scenario, parameters)} />
        </div>
      </div>
      {!('error' in result) && (
        <>
          <EventTable scenario={scenario} events={result.events} selected={selected} select={select} />
          <DiscardedTable scenario={scenario} discarded={result.discarded} />
        </>
      )}
    </>
  );
}
