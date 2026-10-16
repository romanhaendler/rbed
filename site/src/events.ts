// A Scenario's Events and Discarded Pending Events as the site writes them: in the live tables,
// the chart's text alternative and the build's prerendered tables (prerender.mjs). Imports only
// types and aggregates.ts, so Node can use it.
import { aggregatesLine } from './aggregates.ts';
import type { Discard, Event, Result } from './bridge.ts';

/** Events and Discarded Pending Events, of every Rule or of one. */
export type Lifecycle = { events: Event[]; discarded: Discard[] };

/** A Scenario's time unit: its name and its length in milliseconds. */
type TimeUnit = { name: string; millis: number };

export const ofRule = ({ events, discarded }: Lifecycle, rule: string): Lifecycle => ({
  events: events.filter((e) => e.name === rule),
  discarded: discarded.filter((d) => d.name === rule),
});

/** What selects an Event in the table and on the chart. */
export const eventKey = (e: Event) => `${e.name} ${e.start}`;

/** A timestamp (ms) in the Scenario's unit, with the unit: "12 s". */
export const timeIn =
  ({ name, millis }: TimeUnit) =>
  (ms: number) =>
    `${ms / millis} ${name}`;

/** Which edges of the Event are Truncated: "start", "end", "start, end" or "". */
const truncatedText = (e: Event) => [e.truncatedStart && 'start', e.truncatedEnd && 'end'].filter(Boolean).join(', ');

/** Whether the Event table has a Note column: only when the Scenario's listener notes something. */
export const hasNotes = (events: Event[]) => events.some((e) => e.note !== undefined);

/** The Event table's column heads, without the Aggregates. */
export const eventHead = (unit: string, notes: boolean) => [
  'Name',
  `Start (${unit})`,
  `Confirmed (${unit})`,
  `End (${unit})`,
  'Truncated',
  ...(notes ? ['Note'] : []),
];

/** An Event's cells under eventHead, in the Scenario's unit. */
export const eventCells = (e: Event, millis: number, notes: boolean) => [
  e.name,
  String(e.start / millis),
  String(e.confirmed / millis),
  String(e.end / millis),
  truncatedText(e),
  ...(notes ? [e.note ?? ''] : []),
];

/** What the Aggregates of an Event whose Rule reads no Channel (one that only refers to other
 *  Rules) say. */
export const NO_AGGREGATES = 'none (the Rule reads no Channel)';

/** An Event's Aggregates, one line per Channel, joined by "; ". */
export const aggregatesText = (e: Event) =>
  Object.entries(e.aggregates)
    .map(([channel, a]) => aggregatesLine(channel, a))
    .join('; ') || NO_AGGREGATES;

/** The Events in the order they started, then by name, for display; the bridge reports them in
 *  rbed's order, the order they Ended. */
export const byStart = (events: Event[]) =>
  [...events].sort((a, b) => a.start - b.start || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));

/** A bridge result as the site shows it, live and prerendered: its Events byStart. */
export const forDisplay = (result: Result): Result =>
  'error' in result ? result : { ...result, events: byStart(result.events) };

/** The table of Discarded Pending Events: its column heads, and a row's cells. */
export const discardedHead = (unit: string) => ['Name', `Start (${unit})`, `Discarded (${unit})`];
export const discardedCells = (d: Discard, millis: number) => [d.name, String(d.start / millis), String(d.discarded / millis)];
