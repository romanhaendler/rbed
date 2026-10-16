// The two functions of the Scenario module (site/scenarios, Bridge.java), typed.
import * as compiled from 'rbed-scenarios';
import { fillSource } from './parameters';

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
};

/** For a Scenario that plots one: an Expression's value per Sample, null where it is undefined,
 *  and the threshold its Rule's Condition compares it to. */
export type PlottedExpression = { name: string; threshold: number; values: (number | null)[] };

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
      expression?: PlottedExpression;
    }
  | { error: string };

export function scenarios(): Scenario[] {
  return JSON.parse(compiled.scenarios());
}

export function run(scenarioId: string, parameters: Record<string, number>): Result {
  return JSON.parse(compiled.run(scenarioId, JSON.stringify(parameters), '{}'));
}

/** The source with each placeholder replaced by its parameter's value. */
export function source(scenario: Scenario, parameters: Record<string, number>): string {
  return fillSource(scenario.source, scenario.parameters, parameters);
}
