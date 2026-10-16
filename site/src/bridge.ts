// The two functions of the Scenario module (site/scenarios, Bridge.java), typed.
import * as compiled from 'rbed-scenarios';
import type { Aggregates } from './aggregates.ts';
import { filledSource } from './parameters.ts';

export type Parameter = {
  id: string;
  label: string;
  default: number;
  min: number;
  max: number;
  step: number;
  unit: string;
  /** A switch: 0 off, 1 on; the source says false or true. */
  toggle?: boolean;
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

export type Result =
  | {
      samples: { timestamp: number; values: Record<string, number | null> }[];
      events: Event[];
      discarded: Discard[];
      /** Timestamps the Detector was advanced to without a Sample. */
      advances: number[];
      /** Timestamp of the first Sample after the Detector was restarted, if it was. */
      restart?: number;
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
  return filledSource(scenario.source, scenario.parameters, parameters);
}
