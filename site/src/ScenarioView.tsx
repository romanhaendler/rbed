// A Scenario live, with the tools every page that shows one offers: its Channels and Events on
// one time axis with a hover readout (EventChart), a control per parameter, a reset, toggles for
// each Rule and Channel (ScenarioControls), the Java source with the current values, and the
// Event table with each Event's Aggregates, whose rows and the chart's bands select each other; a
// selected Event's Aggregates are shown in full under the chart (EventTables). Non-default values
// live in the address. Where the page asks for it, the reader can draw a Channel's values on the
// chart (DrawLayer; not kept in the address). The Detector runs again on every change.
import { Alert } from '@umriss-ui/core';
import { useMemo, useRef, useState } from 'react';
import { run, type Scenario, source } from './bridge';
import {
  type ChannelAxis,
  type ChartModel,
  channelAxisOf,
  EXPRESSION,
  expressionRows,
  type Row,
  timeDomain,
  undefinedStretches,
} from './chartModel.ts';
import { CodeBlock } from './CodeBlock';
import { drawnSource, drawTo } from './draw.ts';
import { DrawLayer } from './DrawLayer';
import { EventChart, LifecycleKey } from './EventChart';
import { drawnSummary, EventSummary, expressionSummary, timeSummary } from './EventSummary';
import { AggregatesPanel, DiscardedTable, EventTable } from './EventTables';
import { eventKey } from './events.ts';
import { defaultsOf, fromQuery } from './parameters';
import { ScenarioControls, useAddress } from './ScenarioControls';

/** `draw` lets the reader draw the values of the Scenario's Channels other than Pulse Channels. */
export function ScenarioView({ scenario, draw = false }: { scenario: Scenario; draw?: boolean }) {
  const [parameters, setParameters] = useState(() => fromQuery(window.location.search, scenario.parameters));
  const [hidden, setHidden] = useState<string[]>([]);
  const [selected, select] = useState<string>();
  // Drawn values by Channel, one per Sample; the Channel the pointer draws ('' for none); the
  // Channels' axis held still during a stroke; the stroke's previous point.
  const [drawn, setDrawn] = useState<Record<string, (number | null)[]>>({});
  const [pen, setPen] = useState('');
  const [frozen, setFrozen] = useState<ChannelAxis>();
  const [hoveredLatest, hoverLatest] = useState<string>();
  const stroke = useRef<[number, number]>(undefined);
  const result = useMemo(() => run(scenario.id, parameters, drawn), [scenario.id, parameters, drawn]);
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
  useAddress(scenario, parameters);

  let stage;
  if ('error' in result) {
    stage = (
      <Alert tone="danger" title={result.by === 'rbed' ? 'rbed rejects these parameters' : 'The site cannot run these parameters'}>
        {result.error}
      </Alert>
    );
  } else {
    const plotted = expressionRows(rows, result.expression, result.advances, scenario.timeUnit.millis);
    const model: ChartModel = {
      scenario,
      rows,
      lifecycle: result,
      channels: scenario.channels.filter((c) => !hidden.includes(c)),
      rules: scenario.rules.filter((r) => !hidden.includes(r)),
      hoverLatest,
      expression: result.expression,
      expressionRows: plotted,
      advances: result.advances,
      restart: result.restart,
    };
    const event = result.events.find((e) => eventKey(e) === selected);
    const channelAxis = frozen ?? channelAxisOf(model);
    const times = rows.map((r) => r.t);
    const drawPoint = (t: number, value: number, first: boolean) => {
      const previous = first ? undefined : stroke.current;
      const scenarioValues = rows.map((r) => r[pen] ?? null);
      setDrawn((d) => {
        const next = drawTo(d[pen] ?? scenarioValues, times, t, value, previous);
        stroke.current = next.last;
        return { ...d, [pen]: next.values };
      });
      if (first) setFrozen(channelAxis);
    };
    stage = (
      <>
        <div className="drawStage">
          <EventChart
            key={`${scenario.id} ${model.rules.join(' ')}`}
            model={model}
            channelAxis={channelAxis}
            selected={event}
            latest={result.events.find((e) => eventKey(e) === hoveredLatest)}
            select={select}
          />
          {pen !== '' && (
            <DrawLayer x={timeDomain(rows)} channelAxis={channelAxis} draw={drawPoint} end={() => setFrozen(undefined)} />
          )}
        </div>
        <LifecycleKey
          advances={result.advances.length > 0}
          restart={result.restart !== undefined}
          undefinedExpression={undefinedStretches(plotted).length > 0}
        />
        <EventSummary
          scenario={scenario}
          lifecycle={result}
          also={
            expressionSummary(result.expression, plotted, scenario.timeUnit.name) +
            timeSummary(result, scenario) +
            drawnSummary(Object.keys(drawn))
          }
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
          <ScenarioControls
            scenario={scenario}
            parameters={parameters}
            setParameters={setParameters}
            atDefaults={
              scenario.parameters.every((p) => Object.is(parameters[p.id], p.default)) && Object.keys(drawn).length === 0
            }
            reset={() => {
              setParameters(defaultsOf(scenario.parameters));
              setDrawn({});
            }}
            drawable={draw ? scenario.channels.filter((c) => !scenario.pulse.includes(c)) : []}
            pen={pen}
            setPen={setPen}
            hidden={hidden}
            setHidden={setHidden}
          />
        </div>
        <div className="configuratorCode">
          <CodeBlock language="Java" source={drawnSource(source(scenario, parameters), drawn)} />
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
