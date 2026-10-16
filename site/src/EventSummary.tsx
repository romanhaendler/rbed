// The chart's text alternative: its Events, Discarded Pending Events, plotted Expression, time
// advances and restart in words, for a screen reader.
import { VisuallyHidden } from '@umriss-ui/core';
import type { PlottedExpression, Scenario } from './bridge';
import { type Row, undefinedStretches } from './chartModel.ts';
import { type Lifecycle, timeIn } from './events.ts';

/** The plotted Expression in words: what it is compared to and where it is undefined. */
export function expressionSummary(expression: PlottedExpression | undefined, rows: Row[], unit: string): string {
  if (!expression) return '';
  const stretches = undefinedStretches(rows).map(([from, to]) => `from ${from} to ${to} ${unit}`);
  return (
    ` The dashed line is ${expression.name}, compared with the threshold ${expression.threshold}` +
    (stretches.length === 0 ? '.' : `; it is undefined ${stretches.join(' and ')}.`)
  );
}

/** The time advances and the restart in words, for the text alternative. */
export function timeSummary({ advances, restart }: { advances: number[]; restart?: number }, scenario: Scenario): string {
  const at = timeIn(scenario.timeUnit);
  return (
    (advances.length === 0
      ? ''
      : ` Time is advanced without a Sample ${advances.length === 1 ? 'once' : `${advances.length} times`}, from ${at(advances[0])} to ${at(advances[advances.length - 1])}.`) +
    (restart === undefined ? '' : ` The Detector is restarted at ${at(restart)}.`)
  );
}

/** The chart's text alternative: its Events and Discarded Pending Events in words. */
export function EventSummary({ scenario, lifecycle, also = '' }: { scenario: Scenario; lifecycle: Lifecycle; also?: string }) {
  const { events, discarded } = lifecycle;
  const at = timeIn(scenario.timeUnit);
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
