// A Scenario live, with the tools every page that shows one offers: its Channels and Events on
// one time axis with a hover readout, a control per parameter, a reset, toggles for each Rule
// and Channel, the Java source with the current values, and the Event table with each Event's
// Aggregates, whose rows and the chart's bands select each other; a selected Event's Aggregates
// are shown in full under the chart. Non-default values live in the address. The Detector runs
// again on every change.
import { LimitBand, Tooltip, type TooltipHit, useChart } from '@umriss-ui/charts';
import { Alert, Button, Checkbox, FormField, Slider, VisuallyHidden } from '@umriss-ui/core';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type Aggregates, aggregatesLine, formatNumber } from './aggregates.ts';
import { type Event, type Scenario, run, source } from './bridge';
import { CodeBlock } from './CodeBlock';
import { fromQuery, toQuery } from './parameters';

type Row = { t: number } & Record<string, number | null>;
/** A StateBand row; `key` names the Event a segment belongs to. */
type BandRow = { t: number; s: number | null; key: string };

/** The Event lanes take the bottom of the plot, one unit each; the Channels the top three units. */
const COURSE_UNITS = 3;
const EVENT_COLOUR = 'var(--uc-series-2)';
const EVENT_STATES = [{ label: 'Event', color: EVENT_COLOUR }] as const;
/** The chart palette without the Events' colour, so a Channel never looks like a band. */
const CHANNEL_COLOURS = [1, 3, 4, 5, 6].map((n) => `var(--uc-series-${n})`);
const channelColour = (scenario: Scenario, channel: string) =>
  CHANNEL_COLOURS[scenario.channels.indexOf(channel) % CHANNEL_COLOURS.length];

const eventKey = (e: Event) => `${e.name} ${e.start}`;

function niceStep(rough: number): number {
  const power = 10 ** Math.floor(Math.log10(rough));
  const m = rough / power;
  return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * power;
}

/** The Channels' axis: ticks over the values only, and a domain stretched below them for the lanes. */
function courseAxis(rows: Row[], channels: string[], lanes: number) {
  const values = rows.flatMap((r) => channels.map((c) => r[c])).filter(Number.isFinite) as number[];
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const step = niceStep((hi - lo) / 4 || Math.abs(hi) || 1);
  const first = Math.floor(lo / step);
  const last = Math.max(Math.ceil(hi / step), first + 1);
  const ticks = [];
  for (let k = first; k <= last; k++) ticks.push(Number((k * step).toPrecision(12)));
  const top = last * step + step / 2;
  const bottom = first * step - step / 2;
  const domain: [number, number] = [top - ((top - bottom) * (COURSE_UNITS + lanes)) / COURSE_UNITS, top];
  return { ticks, domain };
}

/** A Rule's Events as StateBand rows: each Event starts a segment, its end closes it. */
function bandRows(events: Event[], rule: string, millis: number): BandRow[] {
  return events
    .filter((e) => e.name === rule)
    .flatMap((e) => [
      { t: e.start / millis, s: 0, key: eventKey(e) },
      { t: e.end / millis, s: null, key: eventKey(e) },
    ]);
}

/** What a Rule is at time t (in the Scenario's unit): Pending or Confirmed within one of its Events, or neither. */
function ruleState(events: Event[], rule: string, t: number, millis: number): string {
  const e = events.find((e) => e.name === rule && e.start / millis <= t && t < e.end / millis);
  return e === undefined ? 'no Event' : t < e.confirmed / millis ? 'Pending' : 'Confirmed';
}

type Shown = { scenario: Scenario; rows: Row[]; events: Event[]; channels: string[]; rules: string[] };

/** The tooltip: every shown Channel's value and Rule's state at the hovered time. */
function Readout({ shown, t }: { shown: Shown; t: number }) {
  const { scenario, rows, events, channels, rules } = shown;
  const row = rows.findLast((r) => r.t <= t) ?? rows[0];
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
        {rules.map((r) => line(r, EVENT_COLOUR, ruleState(events, r, t, scenario.timeUnit.millis)))}
      </div>
    </>
  );
}

function EventChart({
  shown,
  selected,
  select,
}: {
  shown: Shown;
  selected?: Event;
  select: (key: string | undefined) => void;
}) {
  const { scenario, rows, events, channels, rules } = shown;
  const { name: unit, millis } = scenario.timeUnit;
  // A click (or Enter) on the chart selects the Event under it, if any.
  const { Chart, XAxis, YAxis, Line, Scatter, StateBand } = useChart(rows, {
    onSelect: (hit) => {
      const band = hit.points.find((p) => p.segment !== undefined && (p.datum as unknown as BandRow).s === 0);
      select(band && (band.datum as unknown as BandRow).key);
    },
  });
  // The tooltip's render is compared by its source text, so it reads the data through a ref.
  const live = useRef(shown);
  live.current = shown;
  const lanes = rules.length;
  // The axis keeps its scale while Channels are hidden.
  const course = courseAxis(rows, scenario.channels, lanes);
  return (
    <Chart
      height={300}
      ariaLabel={`${scenario.channels.join(', ')} over time in ${unit}, with the Events of ${scenario.rules.join(', ')} as bands`}
    >
      <XAxis value="t" domain={[rows[0].t, rows[rows.length - 1].t]} label={`Time (${unit})`} />
      <YAxis domain={course.domain} ticks={course.ticks} label={channels.join(', ')} />
      {/* Lane i (first Rule on top) spans lanes - i - 1 .. lanes - i. The lane names change with
          the shown Rules; the chart's key remounts it then, since tickFormat is compared by text. */}
      <YAxis
        id="lanes"
        position="right"
        grid={false}
        domain={[0, COURSE_UNITS + lanes]}
        ticks={rules.map((_, i) => lanes - i - 0.5)}
        tickFormat={(v) => rules[Math.round(lanes - 0.5 - v)] ?? ''}
      />
      {selected && (
        <LimitBand orientation="x" from={selected.start / millis} to={selected.end / millis} color="var(--u-color-accent)" />
      )}
      {rules.map((rule, i) => (
        <StateBand<BandRow>
          key={rule}
          data={bandRows(events, rule, millis)}
          value="s"
          states={EVENT_STATES}
          yAxisId="lanes"
          laneFrom={lanes - i - 0.9}
          laneTo={lanes - i - 0.1}
          name={rule}
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
      <Tooltip mode="x" render={(hit: TooltipHit<Row>) => <Readout shown={live.current} t={hit.xValue} />} />
    </Chart>
  );
}

/** The chart's text alternative: its Events in words. */
function EventSummary({ scenario, events }: { scenario: Scenario; events: Event[] }) {
  const { name: unit, millis } = scenario.timeUnit;
  return (
    <VisuallyHidden as="p">
      {events.length === 0
        ? 'The chart shows no Event.'
        : `The chart shows ${events.length === 1 ? 'one Event' : `${events.length} Events`}: ` +
          events
            .map(
              (e) =>
                `${e.name} from ${e.start / millis} to ${e.end / millis} ${unit}` +
                (e.truncatedStart ? ', start Truncated' : '') +
                (e.truncatedEnd ? ', end Truncated' : ''),
            )
            .join('; ') +
          '.'}
    </VisuallyHidden>
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
  const result = useMemo(() => run(scenario.id, parameters), [scenario.id, parameters]);
  const rows = useMemo<Row[]>(
    () => ('error' in result ? [] : result.samples.map((s) => ({ t: s.timestamp / scenario.timeUnit.millis, ...s.values }))),
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
      events: result.events,
      channels: scenario.channels.filter((c) => !hidden.includes(c)),
      rules: scenario.rules.filter((r) => !hidden.includes(r)),
    };
    const event = result.events.find((e) => eventKey(e) === selected);
    stage = (
      <>
        <EventChart
          key={`${scenario.id} ${shown.rules.join(' ')}`}
          shown={shown}
          selected={event}
          select={select}
        />
        <EventSummary scenario={scenario} events={result.events} />
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
          {scenario.parameters.map((p) => (
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
        <EventTable scenario={scenario} events={result.events} selected={selected} select={select} />
      )}
    </>
  );
}
