// A Scenario live, with the tools every page that shows one offers: its Channels and Events on
// one time axis with a hover readout (EventChart), a control per parameter, a reset, toggles for
// each Rule and Channel (ScenarioControls), under them the Event table with each Event's
// Aggregates, whose rows and the chart's bands select each other (a selected Event's Aggregates
// are shown in full above the table, EventTables), and then the Java source with the current
// values. Non-default values live in the address. Where the page asks for it, the reader can
// draw a Channel's values on the chart (DrawLayer; not kept in the address). The Detector runs
// again on every change, and a screen reader hears what came of it.
import { Alert, VisuallyHidden } from '@umriss-ui/core';
import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { type Result, run, type Scenario, source } from './bridge';
import { type ChannelAxis, type ChartModel, channelAxesOf, expressionKey, expressionRows, type Row, timeDomain } from './chartModel.ts';
import { CodeBlock } from './CodeBlock';
import { drawnSource, drawTo } from './draw.ts';
import { DrawLayer } from './DrawLayer';
import { EventChart, LifecycleKey, useNarrow } from './EventChart';
import { drawnSummary, EventSummary, expressionSummary, limitsSummary, timeSummary } from './EventSummary';
import { AggregatesPanel, DiscardedTable, EventTable } from './EventTables';
import { eventKey } from './events.ts';
import { defaultsOf, fromQuery } from './parameters';
import { ScenarioControls, useAddress } from './ScenarioControls';

type Drawn = Record<string, (number | null)[]>;

/** What came of a run, in a few words: "2 Events, 1 Discarded Pending Event", or the error. */
function outcome(result: Result): string {
  if ('error' in result) return result.error;
  const count = (n: number, one: string) => `${n} ${one}${n === 1 ? '' : 's'}`;
  return [count(result.events.length, 'Event'), ...(result.discarded.length > 0 ? [count(result.discarded.length, 'Discarded Pending Event')] : [])].join(', ');
}

/** The outcome of the latest change, for a screen reader, half a second after the last one; not
    on the first run, which the page itself shows. */
function useAnnouncement(result: Result): string {
  const first = useRef(result);
  const [said, say] = useState('');
  useEffect(() => {
    if (result === first.current) return;
    const t = window.setTimeout(() => say(outcome(result)), 500);
    return () => window.clearTimeout(t);
  }, [result]);
  return said;
}

/** `draw` lets the reader draw the values of the Scenario's Channels other than Pulse Channels;
    `hidden` names the Channels and Rules the chart hides until the reader shows them. */
export function ScenarioView({ scenario, draw = false, hidden: hiddenAtFirst = [] }: { scenario: Scenario; draw?: boolean; hidden?: string[] }) {
  const [parameters, setParameters] = useState(() => fromQuery(window.location.search, scenario.parameters));
  const [hidden, setHidden] = useState(hiddenAtFirst);
  const [selected, select] = useState<string>();
  // Drawn values by Channel, one per Sample (also in a ref, so two pointer moves before a render
  // both build on the latest); the Channel the pointer draws ('' for none); the Channels' axes
  // held still during a stroke; the stroke's previous point.
  const [drawn, setDrawn] = useState<Drawn>({});
  const latestDrawn = useRef<Drawn>({});
  const [pen, setPen] = useState('');
  const [frozen, setFrozen] = useState<ChannelAxis[]>();
  const [hoveredLatest, hoverLatest] = useState<string>();
  const stroke = useRef<[number, number]>(undefined);
  const narrow = useNarrow();
  const summaryId = useId();
  const stageRef = useRef<HTMLDivElement>(null);
  const sticky = useRef<HTMLDivElement>(null);
  const result = useMemo(() => run(scenario.id, parameters, drawn), [scenario.id, parameters, drawn]);
  const announcement = useAnnouncement(result);
  const rows = useMemo<Row[]>(
    () =>
      'error' in result
        ? []
        : result.samples.map((s, i) => ({
            t: s.timestamp / scenario.timeUnit.millis,
            ...s.values,
            ...Object.fromEntries(result.expressions.map((x, k) => [expressionKey(k), x.values[i]])),
          })),
    [result, scenario.timeUnit.millis],
  );
  const event = 'error' in result ? undefined : result.events.find((e) => eventKey(e) === selected);
  useAddress(scenario, parameters);
  // The chart describes itself (its series and ranges); the Events in words are ours, and its API
  // has no way to add them, so they join its description here.
  useEffect(() => {
    const plot = stageRef.current?.querySelector('.uc-plot');
    const described = plot?.getAttribute('aria-describedby') ?? '';
    if (plot && !described.split(' ').includes(summaryId)) plot.setAttribute('aria-describedby', `${described} ${summaryId}`.trim());
  });

  // A control the keyboard reaches on a phone scrolls into view below the sticky stage, not under it.
  useEffect(() => {
    const stage = sticky.current!;
    const observer = new ResizeObserver(() => stage.parentElement!.style.setProperty('--stage-height', `${stage.offsetHeight}px`));
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  const setDrawnNow = (next: Drawn) => {
    latestDrawn.current = next;
    setDrawn(next);
  };

  let stage;
  if ('error' in result) {
    stage = (
      <Alert tone="danger" title={result.by === 'rbed' ? 'rbed rejects these parameters' : 'The site cannot run these parameters'}>
        {result.error}
      </Alert>
    );
  } else {
    const plotted = expressionRows(rows, result.expressions, result.advances, scenario.timeUnit.millis);
    const model: ChartModel = {
      scenario,
      rows,
      lifecycle: result,
      channels: scenario.channels.filter((c) => !hidden.includes(c)),
      rules: scenario.rules.filter((r) => !hidden.includes(r)),
      hoverLatest,
      expressions: result.expressions,
      expressionRows: plotted,
      thresholds: result.thresholds,
      windows: result.windows,
      advances: result.advances,
      restart: result.restart,
      restartForgets: result.restartForgets,
    };
    const channelAxes = frozen ?? channelAxesOf(model);
    const penAxis = channelAxes.find((a) => a.channels.includes(pen)) ?? channelAxes[0];
    const times = rows.map((r) => r.t);
    const drawPoint = (t: number, value: number, first: boolean) => {
      const values = latestDrawn.current[pen] ?? rows.map((r) => r[pen] ?? null);
      const next = drawTo(values, times, t, value, first ? undefined : stroke.current);
      stroke.current = next.last;
      setDrawnNow({ ...latestDrawn.current, [pen]: next.values });
      if (first) setFrozen(channelAxes);
    };
    stage = (
      <>
        {/* Esc on the chart clears the selection, as a click that hits no band does (EventChart). */}
        <div className="drawStage" ref={stageRef} onKeyDown={(event) => event.key === 'Escape' && select(undefined)}>
          <EventChart
            key={`${scenario.id} ${model.rules.join(' ')} ${narrow}`}
            model={model}
            channelAxes={channelAxes}
            narrow={narrow}
            selected={event}
            latest={result.events.find((e) => eventKey(e) === hoveredLatest)}
            select={select}
          />
          {pen !== '' && <DrawLayer x={timeDomain(rows)} channelAxis={penAxis} draw={drawPoint} end={() => setFrozen(undefined)} />}
        </div>
        <LifecycleKey model={model} />
        <EventSummary
          id={summaryId}
          scenario={scenario}
          lifecycle={result}
          also={
            expressionSummary(result.expressions, plotted, scenario.timeUnit.name) +
            limitsSummary(result.thresholds, result.windows, scenario) +
            timeSummary(result, scenario) +
            drawnSummary(Object.keys(drawn))
          }
        />
      </>
    );
  }

  return (
    <>
      <div className="configurator">
        {/* On a phone the stage stays in view while the reader works the controls under it. */}
        <div className="configuratorLive">
          <div className="configuratorStage" ref={sticky}>
            {stage}
          </div>
          <div className="configuratorPanel">
            <ScenarioControls
              scenario={scenario}
              parameters={parameters}
              setParameters={setParameters}
              atDefaults={
                scenario.parameters.every((p) => Object.is(parameters[p.id], p.default)) && Object.keys(drawn).length === 0
              }
              reset={() => {
                setParameters(defaultsOf(scenario.parameters));
                setDrawnNow({});
              }}
              drawable={draw ? scenario.channels.filter((c) => !scenario.pulse.includes(c)) : []}
              pen={pen}
              setPen={setPen}
              hidden={hidden}
              setHidden={setHidden}
            />
          </div>
        </div>
      </div>
      <VisuallyHidden role="status">{announcement}</VisuallyHidden>
      {/* The Event tables right under the chart, so a selected band's row and a changed table
          stay in view; the source after them. */}
      {!('error' in result) && (
        <>
          {/* An Event whose Rule reads no Channel has no Aggregates to show in full. */}
          {event && Object.keys(event.aggregates).length > 0 && <AggregatesPanel scenario={scenario} event={event} />}
          <EventTable scenario={scenario} events={result.events} selected={selected} select={select} />
          <DiscardedTable scenario={scenario} discarded={result.discarded} />
        </>
      )}
      <div className="codeCard scenarioCode">
        <CodeBlock language="java" label="Java" source={drawnSource(source(scenario, parameters), drawn)} />
      </div>
    </>
  );
}
