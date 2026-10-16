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

/** A Pending Event that stopped holding before it was Confirmed, and when that was noticed. */
export type Discard = { name: string; start: number; discarded: number };

/** For a Scenario that plots one: an Expression's value per Sample, null where it is undefined,
 *  and the threshold its Rule's Condition compares it to. */
export type PlottedExpression = { name: string; threshold: number; values: (number | null)[] };

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
      discarded: Discard[];
      expression?: PlottedExpression;
    }
  | { error: string };

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
