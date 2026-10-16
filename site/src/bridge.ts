// The two functions of the Scenario module (site/scenarios, Bridge.java), typed.
import * as compiled from 'rbed-scenarios';

export type Parameter = {
  id: string;
  label: string;
  default: number;
  min: number;
  max: number;
  step: number;
  unit: string;
};

export type Scenario = {
  id: string;
  title: string;
  timeUnit: { name: string; millis: number };
  channels: string[];
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

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
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
  return scenario.source.replace(/\$\{(\w+)\}/g, (_, id: string) => String(parameters[id]));
}
