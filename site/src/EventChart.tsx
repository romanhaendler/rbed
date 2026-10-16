// The chart of a Scenario's run: its Channels (a Pulse Channel as marks) and plotted Expression
// over time, each Rule's Events as a band on a lane of its own with their lifecycle marked, time
// advances and a restart, with a hover readout; and the key to its bands and marks under it. A
// click on a band selects its Event. On a Scenario whose Rules read latest(...), the readout
// reports the Event latest(...) reads at the hovered time, which the chart then highlights.
import { LimitBand, LimitLine, Tooltip, type TooltipHit, useChart } from '@umriss-ui/charts';
import { useEffect, useRef } from 'react';
import type { Event } from './bridge';
import {
  ADVANCE_COLOR,
  type BandRow,
  bandAxis,
  bandRows,
  CHANNEL_UNITS,
  type ChannelAxis,
  type ChartModel,
  channelColor,
  EVENT_COLOR,
  EVENT_STATES,
  EXPRESSION,
  expressionColor,
  expressionValue,
  LATEST_COLOR,
  laneMarks,
  latestEvent,
  MARKS,
  type MarkRow,
  RESTART_COLOR,
  type Row,
  ruleState,
  SEPARATE_UNITS,
  TRUNCATED,
  truncatedEdges,
  timeDomain,
  UNDEFINED_COLOR,
  undefinedStretches,
} from './chartModel.ts';
import { eventKey, ofRule } from './events.ts';

/** The tooltip: every shown Channel's value and Rule's state at the hovered time. */
function Readout({ model, t }: { model: ChartModel; t: number }) {
  const { scenario, rows, lifecycle, channels, rules, expression } = model;
  const row = rows.findLast((r) => r.t <= t) ?? rows[0];
  const { millis } = scenario.timeUnit;
  const latest = scenario.latest && latestEvent(ofRule(lifecycle, scenario.latest).events, t, millis);
  const latestKey = latest ? eventKey(latest) : undefined;
  // The chart highlights that Event; there is no hover callback, so the readout reports it.
  const { hoverLatest } = model;
  useEffect(() => hoverLatest(latestKey), [hoverLatest, latestKey]);
  useEffect(() => () => hoverLatest(undefined), [hoverLatest]);
  const line = (name: string, color: string, value: string) => (
    <div className="uc-tooltip-row" key={name}>
      <span className="uc-tooltip-chip" style={{ background: color }} />
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
        {channels.map((c) => line(c, channelColor(scenario, c), String(row[c] ?? '–')))}
        {expression &&
          line(
            expression.name,
            expressionColor(scenario),
            expressionValue(row[EXPRESSION]) + (expression.unit ? ` ${expression.unit}` : ''),
          )}
        {rules.map((r) => line(r, EVENT_COLOR, ruleState(ofRule(lifecycle, r), t, millis)))}
        {scenario.latest &&
          line(
            `latest(${scenario.latest}) reads`,
            LATEST_COLOR,
            latest ? `${latest.start / millis} to ${latest.end / millis} ${scenario.timeUnit.name}` : 'no Event yet',
          )}
      </div>
    </>
  );
}

export function EventChart({
  model,
  channelAxis,
  selected,
  latest,
  select,
}: {
  model: ChartModel;
  /** The Channels' axis, from channelAxisOf; it keeps its scale while Channels are hidden. */
  channelAxis: ChannelAxis;
  selected?: Event;
  /** The Event latest(...) reads at the hovered time. */
  latest?: Event;
  select: (key: string | undefined) => void;
}) {
  const { scenario, rows, lifecycle, channels, rules, expression, expressionRows } = model;
  const { name: unit, millis } = scenario.timeUnit;
  // An Expression with a unit of its own (a count of Events, a duration) is not on the Channels'
  // scale: it gets a band of the plot and an axis of its own, between the Channels and the lanes.
  const separate = expression?.unit !== undefined ? expression : undefined;
  const onChannels = separate ? undefined : expression;
  // A click (or Enter) on the chart selects the Event under it, if any.
  const { Chart, XAxis, YAxis, Line, Scatter, StateBand } = useChart(rows, {
    onSelect: (hit) => {
      const band = hit.points.find((p) => p.segment !== undefined && (p.datum as unknown as BandRow).s !== null);
      select(band && (band.datum as unknown as BandRow).key);
    },
  });
  // The tooltip's render is compared by its source text, so it reads the data through a ref.
  const live = useRef(model);
  live.current = model;
  const lanes = rules.length;
  const total = CHANNEL_UNITS + lanes + (separate ? SEPARATE_UNITS : 0);
  const separateAxis = separate && bandAxis(expressionRows, [EXPRESSION], [separate.threshold], lanes, SEPARATE_UNITS, total);
  return (
    <Chart
      height={separate ? 380 : 300}
      ariaLabel={`${scenario.channels.join(', ')} over time in ${unit}, with the Events of ${scenario.rules.join(', ')} as bands, Pending then Confirmed, and their lifecycle marked`}
    >
      <XAxis value="t" domain={timeDomain(rows)} label={`Time (${unit})`} />
      <YAxis domain={channelAxis.domain} ticks={channelAxis.ticks} label={channels.join(', ')} />
      {separate && separateAxis && <YAxis id="separate" grid={false} domain={separateAxis.domain} ticks={separateAxis.ticks} />}
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
        undefinedStretches(expressionRows).map(([from, to]) => (
          // No label: on the x axis it would cover a tick on a phone; the key names it.
          <LimitBand key={from} orientation="x" from={from} to={to} color={UNDEFINED_COLOR} />
        ))}
      {onChannels && <LimitLine value={onChannels.threshold} label={`threshold ${onChannels.threshold}`} />}
      {separate && <LimitLine axisId="separate" value={separate.threshold} label={`threshold ${separate.threshold} ${separate.unit}`} />}
      {selected && (
        <LimitBand orientation="x" from={selected.start / millis} to={selected.end / millis} color="var(--u-color-accent)" />
      )}
      {latest && <LimitBand orientation="x" from={latest.start / millis} to={latest.end / millis} color={LATEST_COLOR} />}
      {model.restart !== undefined && (
        // No label: on the x axis it would cover a tick on a phone; the key under the chart names it.
        <LimitLine orientation="x" value={model.restart / millis} color={RESTART_COLOR} />
      )}
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
        data={laneMarks(rules, lifecycle, millis, truncatedEdges)}
        value="y"
        yAxisId="lanes"
        name={TRUNCATED.name}
        color={TRUNCATED.color}
        radius={9}
      />
      {MARKS.map((m) => (
        <Scatter<MarkRow>
          key={m.name}
          data={laneMarks(rules, lifecycle, millis, m.moments)}
          value="y"
          yAxisId="lanes"
          name={m.name}
          color={m.color}
          radius={4}
        />
      ))}
      {model.advances.length > 0 && (
        <Scatter<MarkRow>
          data={model.advances.map((t) => ({ t: t / millis, y: lanes + 0.15 }))}
          value="y"
          yAxisId="lanes"
          name="Time advance"
          color={ADVANCE_COLOR}
          radius={3}
        />
      )}
      {/* A Pulse Channel has no value between its Samples, so it is drawn as marks, not a line. */}
      {channels.map((c) =>
        scenario.pulse.includes(c) ? (
          <Scatter key={c} value={c} name={c} color={channelColor(scenario, c)} radius={5} />
        ) : (
          <Line key={c} value={c} name={c} color={channelColor(scenario, c)} markers="always" />
        ),
      )}
      {onChannels && (
        <Line data={expressionRows} value={EXPRESSION} name={onChannels.name} color={expressionColor(scenario)} dash={[6, 4]} markers="always" />
      )}
      {separate && <Line data={expressionRows} value={EXPRESSION} name={separate.name} color={expressionColor(scenario)} dash={[6, 4]} yAxisId="separate" />}
      <Tooltip mode="x" render={(hit: TooltipHit<Row>) => <Readout model={live.current} t={hit.xValue} />} />
    </Chart>
  );
}

/** What the bands' colors and the marks mean; the text alternative says it in words. */
export function LifecycleKey({ advances, restart, undefinedExpression }: { advances: boolean; restart: boolean; undefinedExpression: boolean }) {
  const item = (label: string, color: string, mark: boolean) => (
    <span key={label}>
      <span className={mark ? 'swatch mark' : 'swatch'} style={{ background: color }} />
      {label}
    </span>
  );
  return (
    <div className="lifecycleKey" aria-hidden="true">
      <span>Bands:</span>
      {EVENT_STATES.map((s) => item(s.label, s.color, false))}
      <span>Marks:</span>
      {MARKS.map((m) => item(m.name, m.color, true))}
      <span>
        <span className="swatch mark halo" style={{ background: TRUNCATED.color }} />
        Truncated start or end
      </span>
      {advances && item('Time advance', ADVANCE_COLOR, true)}
      {undefinedExpression && item('Expression undefined', `color-mix(in srgb, ${UNDEFINED_COLOR} 30%, transparent)`, false)}
      {restart && (
        <span>
          <span className="swatch line" style={{ borderColor: RESTART_COLOR }} />
          Restart
        </span>
      )}
    </div>
  );
}
