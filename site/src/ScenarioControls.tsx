// The panel beside a Scenario's chart: a control per parameter (a choice as radio buttons, a
// number as a slider), a reset, the Channel to draw (where the page allows it) and a toggle for
// each Rule and Channel. And the address, which follows the parameter values.
import { Button, Checkbox, FormField, RadioGroup, SegmentedControl, Slider } from '@umriss-ui/core';
import { useEffect } from 'react';
import type { Scenario } from './bridge';
import { channelColor, EVENT_COLOR } from './chartModel.ts';
import { toQuery, usedParameters } from './parameters';

/** The address follows the values a moment after the last change: browsers limit how often a
    page may replace its address, and a slider moves many times a second. */
export function useAddress(scenario: Scenario, parameters: Record<string, number>) {
  useEffect(() => {
    const t = window.setTimeout(() => {
      const { pathname, search, hash } = window.location;
      const next = toQuery(search, parameters, scenario.parameters);
      if (next !== search) window.history.replaceState(window.history.state, '', pathname + next + hash);
    }, 250);
    return () => window.clearTimeout(t);
  }, [parameters, scenario.parameters]);
}

export function ScenarioControls({
  scenario,
  parameters,
  setParameters,
  atDefaults,
  reset,
  drawable,
  pen,
  setPen,
  hidden,
  setHidden,
}: {
  scenario: Scenario;
  parameters: Record<string, number>;
  setParameters: (values: Record<string, number>) => void;
  /** Whether Reset has nothing to do. */
  atDefaults: boolean;
  reset: () => void;
  /** The Channels the reader may draw; none where the page does not allow drawing. */
  drawable: string[];
  /** The Channel the pointer draws, '' for none. */
  pen: string;
  setPen: (channel: string) => void;
  /** The Rules and Channels hidden from the chart. */
  hidden: string[];
  setHidden: (names: string[]) => void;
}) {
  // A parameter only an unchosen option uses has no control.
  const used = usedParameters(scenario.source, scenario.parameters, parameters);
  const toggle = (name: string) => setHidden(hidden.includes(name) ? hidden.filter((n) => n !== name) : [...hidden, name]);
  const visibility = (names: string[], color: (name: string) => string) =>
    names.map((name) => (
      <Checkbox
        key={name}
        checked={!hidden.includes(name)}
        onChange={() => toggle(name)}
        label={
          <>
            <span className="swatch" style={{ background: color(name) }} />
            {name}
          </>
        }
      />
    ));
  return (
    <>
      <div className="configuratorHead">
        <span>Parameters</span>
        <Button variant="ghost" size="sm" disabled={atDefaults} onClick={reset}>
          Reset
        </Button>
      </div>
      {scenario.parameters.map(
        (p) =>
          p.options && (
            <FormField key={p.id} label={p.label}>
              <RadioGroup
                name={p.id}
                options={p.options.map((o, i) => ({ value: String(i), label: o.label }))}
                value={String(parameters[p.id])}
                onChange={(v) => setParameters({ ...parameters, [p.id]: Number(v) })}
              />
            </FormField>
          ),
      )}
      {scenario.parameters
        .filter((p) => !p.options && used.includes(p.id))
        .map((p) => (
          <FormField key={p.id} label={p.label}>
            <Slider
              name={p.id}
              min={p.min}
              max={p.max}
              step={p.step}
              value={parameters[p.id]}
              onChange={(v) => setParameters({ ...parameters, [p.id]: v })}
              format={(v) => `${v} ${p.unit}`}
              showValue
            />
          </FormField>
        ))}
      {drawable.length > 0 && (
        <FormField
          label="Draw"
          hint={
            pen === ''
              ? 'Choose a Channel to draw its values on the chart.'
              : `Drag across the chart to draw ${pen}; the hover readout is back once Draw is Off. Reset brings back the Scenario's values.`
          }
        >
          <SegmentedControl
            options={[{ value: '', label: 'Off' }, ...drawable.map((c) => ({ value: c, label: c }))]}
            value={pen}
            onChange={setPen}
          />
        </FormField>
      )}
      <fieldset className="configuratorShow">
        <legend>Show</legend>
        {visibility(scenario.rules, () => EVENT_COLOR)}
        {visibility(scenario.channels, (c) => channelColor(scenario, c))}
      </fieldset>
    </>
  );
}
