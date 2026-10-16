// A Scenario live: its Channels and Events on one time axis, a control per parameter, and the
// Java source with the current values. The Detector runs again on every change.
import { Legend, Tooltip, useChart } from '@umriss-ui/charts';
import { Alert, FormField, Slider } from '@umriss-ui/core';
import { useState } from 'react';
import { type Event, type Scenario, run, source } from './bridge';
import { CodeBlock } from './CodeBlock';

type Samples = Exclude<ReturnType<typeof run>, { error: string }>['samples'];
type Row = { t: number } & Record<string, number | null>;

/** The Event lanes take the bottom of the plot, one unit each; the Channels the top three units. */
const COURSE_UNITS = 3;
const EVENT_STATES = [{ label: 'Event', color: 'var(--uc-series-2)' }] as const;

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
function bandRows(events: Event[], rule: string, millis: number) {
  return events
    .filter((e) => e.name === rule)
    .flatMap((e) => [
      { t: e.start / millis, s: 0 },
      { t: e.end / millis, s: null },
    ]);
}

function EventChart({ scenario, samples, events }: { scenario: Scenario; samples: Samples; events: Event[] }) {
  const { name: unit, millis } = scenario.timeUnit;
  const rows: Row[] = samples.map((s) => ({ t: s.timestamp / millis, ...s.values }));
  const { Chart, XAxis, YAxis, Line, StateBand } = useChart(rows);
  const lanes = scenario.rules.length;
  const course = courseAxis(rows, scenario.channels, lanes);
  // Lane i (first Rule on top) spans lanes - i - 1 .. lanes - i on the lane axis.
  const laneNames = scenario.rules;
  return (
    <Chart
      height={300}
      ariaLabel={`${scenario.channels.join(', ')} over time in ${unit}, with the Events of ${laneNames.join(', ')} as bands`}
    >
      <XAxis value="t" domain={[rows[0].t, rows[rows.length - 1].t]} label={`Time (${unit})`} />
      <YAxis domain={course.domain} ticks={course.ticks} label={scenario.channels.join(', ')} />
      <YAxis
        id="lanes"
        position="right"
        grid={false}
        domain={[0, COURSE_UNITS + lanes]}
        ticks={laneNames.map((_, i) => lanes - i - 0.5)}
        tickFormat={(v) => laneNames[Math.round(lanes - 0.5 - v)] ?? ''}
      />
      {laneNames.map((rule, i) => (
        <StateBand
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
      {scenario.channels.map((c) => (
        <Line key={c} value={c} name={c} markers="always" />
      ))}
      <Legend placement="top" />
      <Tooltip mode="x" />
    </Chart>
  );
}

function EventTable({ scenario, events }: { scenario: Scenario; events: Event[] }) {
  const { name: unit, millis } = scenario.timeUnit;
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
          </tr>
        </thead>
        <tbody>
          {events.length === 0 && (
            <tr>
              <td colSpan={5}>No Event</td>
            </tr>
          )}
          {events.map((e) => (
            <tr key={`${e.name} ${e.start}`}>
              <td>{e.name}</td>
              <td>{e.start / millis}</td>
              <td>{e.confirmed / millis}</td>
              <td>{e.end / millis}</td>
              <td>{[e.truncatedStart && 'start', e.truncatedEnd && 'end'].filter(Boolean).join(', ')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ScenarioView({ scenario }: { scenario: Scenario }) {
  const [parameters, setParameters] = useState(() =>
    Object.fromEntries(scenario.parameters.map((p) => [p.id, p.default])),
  );
  const result = run(scenario.id, parameters);

  return (
    <>
      <div className="configurator">
        <div className="configuratorStage">
          {'error' in result ? (
            <Alert tone="danger">{result.error}</Alert>
          ) : (
            <EventChart key={scenario.id} scenario={scenario} samples={result.samples} events={result.events} />
          )}
        </div>
        <div className="configuratorPanel">
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
        </div>
        <div className="configuratorCode">
          <CodeBlock language="Java" source={source(scenario, parameters)} />
        </div>
      </div>
      {!('error' in result) && <EventTable scenario={scenario} events={result.events} />}
    </>
  );
}
