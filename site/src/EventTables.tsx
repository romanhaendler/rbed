// The tables under a Scenario's chart: its Events with their Aggregates (a row selects its
// Event), the Discarded Pending Events, and every Aggregate of the selected Event. Their columns
// are those of the prerendered tables (src/events.ts).
import { type Aggregates, formatNumber } from './aggregates.ts';
import type { Discard, Event, Scenario } from './bridge';
import { aggregatesText, discardedCells, discardedHead, eventCells, eventHead, eventKey, hasNotes } from './events.ts';

/** The Pending Events that were Discarded; no table while there is none. */
export function DiscardedTable({ scenario, discarded }: { scenario: Scenario; discarded: Discard[] }) {
  const { name: unit, millis } = scenario.timeUnit;
  if (discarded.length === 0) return null;
  return (
    <div className="tableScroll">
      <table className="eventTable" id="discarded">
        <caption>Discarded Pending Events</caption>
        <thead>
          <tr>
            {discardedHead(unit).map((h) => (
              <th scope="col" key={h}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {discarded.map((d) => (
            <tr key={`${d.name} ${d.start}`}>
              {discardedCells(d, millis).map((c, i) => (
                <td key={i}>{c}</td>
              ))}
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
export function AggregatesPanel({ scenario, event }: { scenario: Scenario; event: Event }) {
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

export function EventTable({
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
  const notes = hasNotes(events);
  const head = eventHead(unit, notes);
  return (
    <div className="tableScroll">
      <table className="eventTable" id="events">
        <caption>Events</caption>
        <thead>
          <tr>
            {head.map((h) => (
              <th scope="col" key={h}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {events.length === 0 && (
            <tr>
              <td colSpan={head.length}>No Event</td>
            </tr>
          )}
          {events.map((e) => {
            const key = eventKey(e);
            const [name, ...cells] = eventCells(e, millis, notes);
            return [
              <tr key={key} data-selected={key === selected || undefined}>
                <td>
                  <button
                    type="button"
                    className="eventPick"
                    aria-pressed={key === selected}
                    onClick={() => select(key === selected ? undefined : key)}
                  >
                    {name}
                  </button>
                </td>
                {cells.map((c, i) => (
                  <td key={i}>{c}</td>
                ))}
              </tr>,
              // The Aggregates under their Event, so the table stays five columns wide on a phone.
              <tr key={`${key} aggregates`} className="aggregateRow" data-selected={key === selected || undefined}>
                <td colSpan={head.length}>
                  <span className="aggregateLabel">Aggregates</span> {aggregatesText(e)}
                </td>
              </tr>,
            ];
          })}
        </tbody>
      </table>
    </div>
  );
}
