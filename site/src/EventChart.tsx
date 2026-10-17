// The chart of a Scenario's run: its Channels (a Pulse Channel as marks; Channels of very
// different scales in bands of their own) with the thresholds their Rules compare them to, the
// plotted Expressions, each Rule's Events as a band on a lane of its own with their lifecycle
// marked, the windows a within(...) counts as a strip over the lane of the Rule that counts
// them, time advances and a restart, with a hover readout; and the key to its bands, lines and
// marks under it. A Channel's line joins its Samples; where rbed holds a value (a Sample lacks
// the Channel, or time was advanced without a Sample) the line breaks and a thin step line
// carries the held value, which the readout marks "(kept)". A click on a band selects its Event.
// On a Scenario whose Rules read latest(...), the readout reports the Event latest(...) reads at
// the hovered time, which the chart then highlights.
import { LimitBand, LimitLine, Tooltip, type TooltipHit, useChart } from '@umriss-ui/charts';
import { type CSSProperties, type ReactNode, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { Event } from './bridge';
import {
  ADVANCE_COLOR,
  allMoments,
  type BandRow,
  bandRows,
  type ChannelAxis,
  type ChartModel,
  channelColor,
  EVENT_STATES,
  expressionColor,
  expressionKey,
  expressionLabel,
  expressionThreshold,
  forgetsAt,
  expressionValue,
  heldChannels,
  heldRows,
  isSeparate,
  keyOf,
  LATEST_COLOR,
  lane,
  laneMarks,
  latestEvent,
  lineRows,
  MARKS,
  type MarkRow,
  plotUnits,
  RESTART_COLOR,
  RING_COLOR,
  type Row,
  readoutValue,
  ruleState,
  stateColor,
  separateAxisOf,
  THRESHOLD_COLOR,
  TRUNCATED,
  thresholdLines,
  thresholdText,
  tickText,
  timeDomain,
  timeTicks,
  truncatedEdges,
  UNDEFINED_COLOR,
  undefinedStretches,
  WINDOW_COLOR,
  windowRows,
} from './chartModel.ts';
import { eventKey, ofRule } from './events.ts';
import { plotRect } from './plotArea.ts';

const NARROW = '(max-width: 600px)';

/** Whether the screen is a phone's: the chart then writes the Rules' names over their lanes. */
export function useNarrow(): boolean {
  return useSyncExternalStore(
    (changed) => {
      const query = window.matchMedia(NARROW);
      query.addEventListener('change', changed);
      return () => query.removeEventListener('change', changed);
    },
    () => window.matchMedia(NARROW).matches,
  );
}

/** The tooltip: every shown Channel's and Expression's value and Rule's state at the hovered time. */
function Readout({ model, t }: { model: ChartModel; t: number }) {
  const { scenario, rows, lifecycle, channels, rules, expressions, expressionRows } = model;
  // The Expressions are also read at time advances, which have no Sample of their own.
  const expressionRow = expressionRows.findLast((r) => r.t <= t) ?? expressionRows[0];
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
        {channels.map((c) => line(c, channelColor(scenario, c), readoutValue(scenario, rows, c, t)))}
        {expressions.map((x, k) =>
          line(x.name, expressionColor(scenario, k), expressionValue(expressionRow?.[expressionKey(k)]) + (x.unit ? ` ${x.unit}` : '')),
        )}
        {rules.map((r) => {
          const state = ruleState(ofRule(lifecycle, r), t, millis);
          return line(r, stateColor(state), state);
        })}
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

/** On a narrow chart, each Rule's name over its lane, at the plot's left edge: the lanes' axis
    has no room for them there. Placed from the plot's rectangle once the chart has drawn it. */
function LaneNames({ rules, total }: { rules: string[]; total: number }) {
  const layer = useRef<HTMLDivElement>(null);
  const [plot, setPlot] = useState<{ left: number; top: number; height: number }>();
  useEffect(() => {
    const stage = layer.current!.parentElement!;
    const observer = new ResizeObserver(() => {
      const rect = plotRect(stage);
      const origin = stage.getBoundingClientRect();
      if (rect) setPlot({ left: rect.left - origin.left, top: rect.top - origin.top, height: rect.height });
    });
    observer.observe(stage);
    // The chart lays its axes out after its own first measure.
    const axes = stage.querySelector('.uc-axes');
    if (axes) observer.observe(axes);
    return () => observer.disconnect();
  }, []);
  return (
    <div ref={layer} className="laneNames" aria-hidden="true">
      {plot &&
        rules.map((rule, i) => (
          <span key={rule} style={{ left: plot.left + 4, top: plot.top + (1 - lane(i, rules.length, true).name / total) * plot.height }}>
            {rule}
          </span>
        ))}
    </div>
  );
}

export function EventChart({
  model,
  channelAxes,
  narrow,
  selected,
  latest,
  select,
}: {
  model: ChartModel;
  /** The Channels' axes, from channelAxesOf; they keep their scale while Channels are hidden. */
  channelAxes: ChannelAxis[];
  /** A phone's screen (useNarrow): the Rules' names stand over their lanes. */
  narrow: boolean;
  selected?: Event;
  /** The Event latest(...) reads at the hovered time. */
  latest?: Event;
  select: (key: string | undefined) => void;
}) {
  const { scenario, rows, lifecycle, channels, rules, expressions, expressionRows, thresholds } = model;
  const { name: unit, millis } = scenario.timeUnit;
  const advances = model.advances.map((t) => t / millis);
  // A click on the chart selects the Event in the lane under the pointer, if any, and clears the
  // selection elsewhere; Enter on the Active point selects an Event at its time. The chart hits
  // every series at the pointer's time, so the lane comes from where the pointer went down.
  const pointer = useRef<PointerEvent>(undefined);
  useEffect(() => {
    const down = (event: PointerEvent) => (pointer.current = event);
    const key = () => (pointer.current = undefined);
    document.addEventListener('pointerdown', down);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('pointerdown', down);
      document.removeEventListener('keydown', key);
    };
  }, []);
  const { Chart, XAxis, YAxis, Line, Scatter, StateBand } = useChart(lineRows(rows, advances), {
    onSelect: (hit) => {
      const bands = hit.points.filter((p) => p.segment !== undefined && (p.datum as unknown as BandRow).key !== undefined && (p.datum as unknown as BandRow).s !== null);
      const chart = (pointer.current?.target as Element | null)?.closest('.uc-root');
      const plot = chart && plotRect(chart);
      if (!pointer.current || !plot) return select(bands.length > 0 ? (bands[0].datum as unknown as BandRow).key : undefined);
      const at = ((plot.top + plot.height - pointer.current.clientY) / plot.height) * total;
      const rule = at >= 0 && at < lanes ? rules[lanes - 1 - Math.floor(at)] : undefined;
      const band = bands.find((p) => p.seriesName === rule);
      select(band && (band.datum as unknown as BandRow).key);
    },
  });
  // The tooltip's render is compared by its source text, so it reads the data through a ref.
  const live = useRef(model);
  live.current = model;
  const lanes = rules.length;
  const separate = expressions.filter(isSeparate);
  const total = plotUnits(lanes, separate.length > 0, channelAxes.length);
  const separateAxis = separate.length > 0 ? separateAxisOf(model, channelAxes.length) : undefined;
  const axisOf = (channel: string) => channelAxes.find((a) => a.channels.includes(channel)) ?? channelAxes[0];
  const lines = thresholdLines(thresholds, channelAxes, channels, narrow);
  // The first band's axis carries a threshold label (a Channel's, or an Expression's on its scale).
  const firstLabelled =
    lines.some((l) => l.axis === channelAxes[0].id && l.label !== '') || expressions.some((x) => !isSeparate(x) && x.threshold !== null);
  // Room for the labels of a band with more than two threshold lines.
  const crowded = channelAxes.reduce((sum, a) => sum + Math.max(0, lines.filter((l) => l.axis === a.id && l.label !== '').length - 2), 0) * 30;
  const windows = windowRows(model.windows, lifecycle, millis, forgetsAt(model));
  // A Channel's line with a marker at every Sample, unless the Samples lie too close for markers
  // to read (on a phone sooner). An Expression's line has none: its dash says it is derived.
  const markers = rows.length <= (narrow ? 30 : 100) ? 'always' : 'auto';
  // On a phone, smaller lifecycle marks, so a short Event's band still shows between them.
  const radius = narrow ? { mark: 3.5, ring: 4.5, truncated: 6 } : { mark: 4, ring: 5.5, truncated: 9 };
  // Few Samples: a tick at each, written as the Samples' times are.
  const ticks = timeTicks(rows);
  // Each axis' title stands beside its band (site.css), not in the middle of the plot.
  const titles = Object.fromEntries([
    ...channelAxes.map((a) => [`--title-${a.id}`, a.middle]),
    ...(separateAxis ? [['--title-separate', separateAxis.middle]] : []),
  ]) as CSSProperties;
  return (
    <>
      <Chart
        height={crowded + (narrow ? 260 + (separateAxis ? 60 : 0) + (channelAxes.length - 1) * 80 : 300 + (separateAxis ? 80 : 0) + (channelAxes.length - 1) * 100)}
        style={titles}
        ariaLabel={`${scenario.channels.join(', ')} over time in ${unit}, with the Events of ${scenario.rules.join(', ')} as bands, Pending then Confirmed, and their lifecycle marked`}
      >
        <XAxis value="t" domain={timeDomain(rows)} ticks={ticks} tickFormat={ticks && ((v) => String(v))} label={`Time (${unit})`} />
        {/* The first band's axis on the left, every further one on the right, next to the plot
            (charts 0.40.0 gives each y axis a column of its own); the Expressions' with a unit
            of their own is titled with the unit, as the key names the Expression. On a phone an
            axis with a threshold label, and every right-hand one, has no title: its column is
            narrow, the label would run into the title, and the key names each threshold in full. */}
        {channelAxes.map((a, b) => (
          <YAxis
            key={a.id}
            id={a.id}
            grid={b === 0}
            position={b > 0 ? 'right' : 'left'}
            domain={a.domain}
            ticks={a.ticks}
            tickFormat={tickText}
            label={narrow && (b > 0 || firstLabelled) ? undefined : a.channels.filter((c) => channels.includes(c)).join(', ')}
          />
        ))}
        {separateAxis && (
          <YAxis
            id="separate"
            grid={false}
            position="right"
            domain={separateAxis.domain}
            ticks={separateAxis.ticks}
            tickFormat={tickText}
            label={narrow ? undefined : [...new Set(separate.map((x) => x.unit))].join(', ')}
          />
        )}
        {/* Lane i (first Rule on top) spans lanes - i - 1 .. lanes - i. The lane names change with
            the shown Rules and the width; the chart's key remounts it then, since tickFormat is
            compared by text. */}
        {narrow ? (
          <YAxis id="lanes" position="right" grid={false} domain={[0, total]} ticks={[]} tickFormat={() => ''} />
        ) : (
          <YAxis
            id="lanes"
            position="right"
            grid={false}
            domain={[0, total]}
            ticks={rules.map((_, i) => lanes - i - 0.5)}
            tickFormat={(v) => rules[Math.round(lanes - 0.5 - v)] ?? ''}
          />
        )}
        {undefinedStretches(expressionRows, expressions.length).map(([from, to]) => (
          // No label: on the x axis it would cover a tick on a phone; the key names it.
          <LimitBand key={from} orientation="x" from={from} to={to} color={UNDEFINED_COLOR} />
        ))}
        {lines.map((l) => (
          <LimitLine key={`${l.axis} ${l.value}`} axisId={l.axis} value={l.value} label={l.label} />
        ))}
        {expressions.map(
          (x, k) =>
            x.threshold !== null && (
              <LimitLine
                key={`expression ${k}`}
                axisId={isSeparate(x) ? 'separate' : channelAxes[0].id}
                value={x.threshold}
                // As a Channel threshold's: the comparison and the value; the key names the Rule.
                label={expressionLabel(x)}
                color={narrow ? THRESHOLD_COLOR : undefined}
              />
            ),
        )}
        {selected && (
          <LimitBand orientation="x" from={selected.start / millis} to={selected.end / millis} color="var(--u-color-accent)" />
        )}
        {latest && <LimitBand orientation="x" from={latest.start / millis} to={latest.end / millis} color={LATEST_COLOR} />}
        {model.restart !== undefined && (
          // No label: on the x axis it would cover a tick on a phone; the key under the chart names it.
          <LimitLine orientation="x" value={model.restart / millis} color={RESTART_COLOR} />
        )}
        {rules.map(
          (rule, i) =>
            windows.has(rule) && (
              <StateBand<BandRow>
                key={`window ${rule}`}
                data={windows.get(rule)!}
                value="s"
                states={[{ label: 'within', color: WINDOW_COLOR }]}
                yAxisId="lanes"
                laneFrom={lane(i, lanes, narrow).strip[0]}
                laneTo={lane(i, lanes, narrow).strip[1]}
                name={`within, ${rule}`}
              />
            ),
        )}
        {rules.map((rule, i) => (
          <StateBand<BandRow>
            key={rule}
            data={bandRows(ofRule(lifecycle, rule), millis)}
            value="s"
            states={EVENT_STATES}
            yAxisId="lanes"
            laneFrom={lane(i, lanes, narrow).from}
            laneTo={lane(i, lanes, narrow).to}
            name={rule}
          />
        ))}
        <Scatter<MarkRow>
          data={laneMarks(rules, lifecycle, millis, truncatedEdges, narrow)}
          value="y"
          yAxisId="lanes"
          name={TRUNCATED.name}
          color={TRUNCATED.color}
          radius={radius.truncated}
        />
        {/* A ring in the surface's color sets each mark off its band. */}
        <Scatter<MarkRow> data={laneMarks(rules, lifecycle, millis, allMoments, narrow)} value="y" yAxisId="lanes" name="Mark outline" color={RING_COLOR} radius={radius.ring} />
        {MARKS.map((m) => (
          <Scatter<MarkRow>
            key={m.name}
            data={laneMarks(rules, lifecycle, millis, m.moments, narrow)}
            value="y"
            yAxisId="lanes"
            name={m.name}
            color={m.color}
            radius={radius.mark}
          />
        ))}
        {model.advances.length > 0 && (
          <Scatter<MarkRow>
            data={model.advances.map((t) => ({ t: t / millis, y: lanes + 0.15 }))}
            value="y"
            yAxisId="lanes"
            name="Time advance"
            color={ADVANCE_COLOR}
            // Small, so many advances read as ticks, not as a bar.
            radius={2.5}
          />
        )}
        {/* A Pulse Channel has no value between its Samples, so it is drawn as marks, not a line. */}
        {channels.map((c) =>
          scenario.pulse.includes(c) ? (
            <Scatter key={c} value={c} name={c} color={channelColor(scenario, c)} radius={5} yAxisId={axisOf(c).id} />
          ) : (
            <Line key={c} value={c} name={c} color={channelColor(scenario, c)} markers={markers} yAxisId={axisOf(c).id} />
          ),
        )}
        {/* A Channel some Samples lack keeps its last value in rbed: a held step line under the
            line through the values it has, drawn as marks. */}
        {heldChannels(scenario, rows, advances)
          .filter((c) => channels.includes(c))
          .map((c) => (
            <Line key={`held ${c}`} data={heldRows(rows, c, advances)} value={c} name={`${c} (kept)`} color={channelColor(scenario, c)} step markers="never" strokeWidth={1} yAxisId={axisOf(c).id} />
          ))}
        {expressions.map((x, k) => (
          <Line
            key={`expression ${k}`}
            data={expressionRows}
            value={expressionKey(k)}
            name={x.name}
            color={expressionColor(scenario, k)}
            dash={[6, 4]}
            markers="never"
            yAxisId={isSeparate(x) ? 'separate' : channelAxes[0].id}
          />
        ))}
        <Tooltip mode="x" render={(hit: TooltipHit<Row>) => <Readout model={live.current} t={hit.xValue} />} />
      </Chart>
      {narrow && <LaneNames rules={rules} total={total} />}
    </>
  );
}

/** What the bands' colors, the lines and the marks mean; the text alternative says it in words.
    Each group keeps its heading with its first item when the key wraps. */
export function LifecycleKey({ model }: { model: ChartModel }) {
  const { scenario, lifecycle, expressions, windows, advances, restart, thresholds, channels } = model;
  const key = keyOf(lifecycle);
  const item = (label: string, color: string, kind = '') => (
    <span key={label} className="keyItem">
      <span className={`swatch ${kind}`} style={kind === 'dash' || kind === 'line' ? { borderColor: color } : { background: color }} />
      {label}
    </span>
  );
  const group = (heading: string, items: ReactNode[]) =>
    items.some(Boolean) && (
      <span className="keyGroup">
        <span>{heading}</span>
        {items}
      </span>
    );
  const truncated = lifecycle.events.some((e) => e.truncatedStart || e.truncatedEnd);
  return (
    <div className="lifecycleKey" aria-hidden="true">
      {group(
        'Bands:',
        key.states.map((s) => item(s.label, s.color)),
      )}
      {group('Marks:', [
        ...key.marks.map((m) => item(m.name, m.color, 'mark')),
        truncated && (
          <span key="truncated" className="keyItem">
            <span className="swatch mark halo" style={{ background: TRUNCATED.color }} />
            Truncated start or end
          </span>
        ),
        advances.length > 0 && item('Time advance', ADVANCE_COLOR, 'mark'),
      ])}
      {group('Lines:', [
        ...expressions.map((x, k) => item(x.name, expressionColor(scenario, k), 'dash')),
        restart !== undefined && item('Restart', RESTART_COLOR, 'line'),
      ])}
      {group(
        'Thresholds:',
        [
          ...thresholds.filter((t) => channels.includes(t.channel)).map((t) => thresholdText(t)),
          ...expressions.flatMap((x) => (x.threshold === null ? [] : [thresholdText(expressionThreshold({ ...x, threshold: x.threshold }), x.unit)])),
        ].map((text) => item(text, THRESHOLD_COLOR, 'dash')),
      )}
      {group('Shaded:', [
        undefinedStretches(model.expressionRows, expressions.length).length > 0 &&
          item(expressions.length > 1 ? 'An Expression undefined' : 'Expression undefined', `color-mix(in srgb, ${UNDEFINED_COLOR} 30%, transparent)`),
      ])}
      {group(
        'Strips:',
        windows.map((w) => item(`${w.rule}: within, after ${w.after}`, WINDOW_COLOR)),
      )}
    </div>
  );
}
