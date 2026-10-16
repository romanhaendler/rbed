// The two functions of the Scenario module (site/scenarios, Bridge.java), typed.
import * as compiled from 'rbed-scenarios';
import type { Aggregates } from './aggregates.ts';

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
};

/** A value at every Sample that a Rule compares with its limit, such as a running count of Events;
    derived from the Events rbed reported. */
export type Running = { label: string; unit: string; limit: number; values: number[] };

/** A Pending Event that stopped holding before it was Confirmed, and when that was noticed. */
export type Discard = { name: string; start: number; discarded: number };

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
      discarded: Discard[];
      running?: Running;
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
  return scenario.source.replace(/\$\{(\w+)\}/g, (_, id: string) =>
    Number.isNaN(parameters[id]) ? 'Double.NaN' : String(parameters[id]),
  );
}
