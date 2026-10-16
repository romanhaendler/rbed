// The two functions of the Scenario module (site/scenarios, Bridge.java), typed.
import * as compiled from 'rbed-scenarios';
import type { Aggregates } from './aggregates.ts';
import { fillSource } from './parameters.ts';

export type Parameter = {
  id: string;
  label: string;
  default: number;
  min: number;
  max: number;
  step: number;
  unit: string;
  /** Present on a choice, whose value is the index of an option. */
  options?: { label: string; source: string }[];
};

export type Scenario = {
  id: string;
  title: string;
  timeUnit: { name: string; millis: number };
  channels: string[];
  /** Those of the Channels that are Pulse Channels: a value only in the Samples that carry one. */
  pulse: string[];
  /** The Rule whose latest Event the Rules read with latest(...), if any. */
  latest?: string;
  /** Names of the Rules, which are the names of their Events. */
  rules: string[];
  parameters: Parameter[];
  /** Java source with a placeholder such as ${threshold} per parameter. */
  source: string;
};

export type Event = {
  name: string;
  start: number;
  confirmed: number;
  end: number;
  truncatedStart: boolean;
  truncatedEnd: boolean;
  /** By Channel name, for each Channel the Rule aggregates. */
  aggregates: Record<string, Aggregates>;
  /** What the Scenario's listener makes of the Event, such as the load a crane lifted. */
  note?: string;
};

/** A Discarded Pending Event: one whose Conditions stopped holding before it was Confirmed, and
 *  when that was noticed. */
export type Discard = { name: string; start: number; discarded: number };

/** An Expression a Scenario plots: its value per Sample, null where it is undefined, and the
 *  threshold its Rule's Condition compares it to (null where it is compared with another
 *  Expression). One with a `unit` of its own (a count of Events, a duration) is not on the
 *  Channels' scale. */
export type PlottedExpression = {
  name: string;
  unit?: string;
  threshold: number | null;
  values: (number | null)[];
  /** Its value at each time advance (Result.advances), where time was advanced. */
  atAdvances?: (number | null)[];
};

/** Where a Rule compares a Channel with a value: `condition` is such as "when >" or "endWhen <";
 *  `rule` names the Rule, or the Rules, it belongs to. */
export type Threshold = { channel: string; rule: string; condition: string; value: number };

/** The time after each Event of the Rule `after` that a Rule's within(...) counts. */
export type Window = { after: string; millis: number };

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
      discarded: Discard[];
      expressions: PlottedExpression[];
      thresholds: Threshold[];
      windows: Window[];
      /** Timestamps the Detector was advanced to without a Sample. */
      advances: number[];
      /** Timestamp of the first Sample after the Detector was restarted, if it was. */
      restart?: number;
    }
  /** `by` is 'rbed' when rbed rejected the Rules or the Samples; otherwise the site cannot run
   *  the input. */
  | { error: string; by?: 'rbed' };

export function scenarios(): Scenario[] {
  return JSON.parse(compiled.scenarios());
}

/** `drawn`: Channel values the reader drew, by Channel name, one per Sample (null: absent), in place of the Scenario's own. */
export function run(
  scenarioId: string,
  parameters: Record<string, number>,
  drawn: Record<string, (number | null)[]> = {},
): Result {
  return JSON.parse(compiled.run(scenarioId, JSON.stringify(parameters), JSON.stringify(drawn)));
}

/** The source with each placeholder replaced by its parameter's value. */
export function source(scenario: Scenario, parameters: Record<string, number>): string {
  return fillSource(scenario.source, scenario.parameters, parameters);
}
