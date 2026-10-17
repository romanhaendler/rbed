// The chart's text alternative: its Events, Discarded Pending Events, plotted Expression, time
// advances and restart in words, for a screen reader.
import { VisuallyHidden } from '@umriss-ui/core';
import type { PlottedExpression, Scenario, Threshold, Window } from './bridge';
import { type Row, thresholdText, undefinedStretches } from './chartModel.ts';
import { type Lifecycle, timeIn } from './events.ts';

/** The plotted Expressions in words: what each is compared to and where they are undefined;
    `rows` are their expressionRows. */
export function expressionSummary(expressions: PlottedExpression[], rows: Row[], unit: string): string {
  if (expressions.length === 0) return '';
  const stretches = undefinedStretches(rows, expressions.length).map(([from, to]) => `from ${from} to ${to} ${unit}`);
  const advances = expressions.some((x) => (x.atAdvances ?? []).length > 0);
  const lines = expressions.map(
    (x) => x.name + (x.threshold === null ? '' : `, compared with the threshold ${x.threshold}${x.unit ? ` ${x.unit}` : ''}`),
  );
  return (
    (lines.length === 1 ? ` The dashed line is ${lines[0]}` : ` The dashed lines are ${lines.join('; and ')}`) +
    (advances ? `, read at every Sample and every time advance` : '') +
    (stretches.length === 0 ? '.' : `; ${lines.length === 1 ? 'it is' : 'they are'} undefined ${stretches.join(' and ')}.`)
  );
}

/** The thresholds and within(...) windows in words, for the text alternative. */
export function limitsSummary(thresholds: Threshold[], windows: Window[], scenario: Scenario): string {
  const at = timeIn(scenario.timeUnit);
  return (
    (thresholds.length === 0 ? '' : ` Lines mark the thresholds: ${thresholds.map(thresholdText).join(', ')}.`) +
    windows.map((w) => ` A strip over the ${w.rule} lane marks the ${at(w.millis)} after each ${w.after} Event that its within(...) counts.`).join('')
  );
}

/** Which Channels' values the reader drew, for the text alternative. */
export const drawnSummary = (channels: string[]) =>
  channels.length === 0 ? '' : ` The values of ${channels.join(' and ')} are drawn, not the Scenario's own.`;

/** The time advances and the restart in words, for the text alternative. */
export function timeSummary(
  { advances, restart, restartForgets }: { advances: number[]; restart?: number; restartForgets?: boolean },
  scenario: Scenario,
): string {
  const at = timeIn(scenario.timeUnit);
  return (
    (advances.length === 0
      ? ''
      : ` Time is advanced without a Sample ${advances.length === 1 ? 'once' : `${advances.length} times`}, from ${at(advances[0])} to ${at(advances[advances.length - 1])}.`) +
    (restart === undefined
      ? ''
      : ` The Detector is restarted at ${at(restart)}${
          restartForgets ? ' without History: it knows no earlier Event, so a within(...) strip from before it ends there' : ' with History'
        }.`)
  );
}

/** The chart's text alternative: its Events and Discarded Pending Events in words. */
export function EventSummary({ id, scenario, lifecycle, also = '' }: { id: string; scenario: Scenario; lifecycle: Lifecycle; also?: string }) {
  const { events, discarded } = lifecycle;
  const at = timeIn(scenario.timeUnit);
  const count = (n: number, one: string, many: string) => (n === 1 ? `one ${one}` : `${n} ${many}`);
  return (
    <VisuallyHidden as="p" id={id}>
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
