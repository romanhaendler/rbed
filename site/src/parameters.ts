// A Scenario's parameter values in the page address, so a link shows what the reader saw. Only
// values that differ from the default are written. And the Java source for those values. Imports
// nothing, so Node (tests and prerender.mjs) can use it.

/** A choice's value is the index of its option; the source names the option's source. */
type Parameter = { id: string; default: number; min: number; max: number; step: number; options?: { source: string }[] };

const PLACEHOLDER = /\$\{(\w+)\}/g;

/** Every parameter's default value, by id. */
export const defaultsOf = (parameters: Parameter[]): Record<string, number> =>
  Object.fromEntries(parameters.map((p) => [p.id, p.default]));

/** The source with each ${id} replaced: a choice by its option's source (filled in the same way),
 *  a number by its value. */
export function fillSource(template: string, parameters: Parameter[], values: Record<string, number>): string {
  return template.replace(PLACEHOLDER, (_, id: string) => {
    const option = parameters.find((p) => p.id === id)?.options?.[values[id]];
    if (option !== undefined) return fillSource(option.source, parameters, values);
    return String(values[id]);
  });
}

/** The ids of the parameters that matter for these values: every choice, and every number the
 *  source names (one only an unchosen option names has no effect). */
export function usedParameters(template: string, parameters: Parameter[], values: Record<string, number>): string[] {
  const named = new Set<string>();
  const visit = (text: string) => {
    for (const [, id] of text.matchAll(PLACEHOLDER)) {
      named.add(id);
      const option = parameters.find((p) => p.id === id)?.options?.[values[id]];
      if (option !== undefined) visit(option.source);
    }
  };
  visit(template);
  return parameters.filter((p) => p.options || named.has(p.id)).map((p) => p.id);
}

/** The values an address names, the default for each parameter it does not name. A value that
 *  is no finite number gives the default, and one off its control is moved into its range and
 *  onto its steps, so the control, the source and the run show the same value. (The bridge
 *  rejects a value outside the range as well: TeaVM's Math.round throws on NaN.) */
export function fromQuery(search: string, parameters: Parameter[]): Record<string, number> {
  const query = new URLSearchParams(search);
  return Object.fromEntries(
    parameters.map((p) => {
      const given = Number(query.get(p.id) || NaN);
      if (!Number.isFinite(given)) return [p.id, p.default];
      const inRange = Math.min(Math.max(given, p.min), p.max);
      const stepped = p.min + Math.round((inRange - p.min) / p.step) * p.step;
      // toPrecision drops the binary noise of the steps (0.05 * 3)
      return [p.id, Math.min(Number(stepped.toPrecision(12)), p.max)];
    }),
  );
}

/** `search` with the parameters set to the values that differ from their default, or '' when
 *  nothing is left. Keys that are no parameter stay. */
export function toQuery(search: string, values: Record<string, number>, parameters: Parameter[]): string {
  const query = new URLSearchParams(search);
  for (const p of parameters) {
    if (Object.is(values[p.id], p.default)) query.delete(p.id);
    else query.set(p.id, String(values[p.id]));
  }
  const text = query.toString();
  return text === '' ? '' : `?${text}`;
}
