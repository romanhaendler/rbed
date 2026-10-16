// A Scenario's parameter values in the page address, so a link shows what the reader saw. Only
// values that differ from the default are written. Imports nothing, so Node can test it.

type Parameter = { id: string; default: number };

/** The values an address names, the default for each parameter it does not name. A value that
 *  is no finite number becomes NaN (JSON knows no other), which rbed rejects with its own message. */
export function fromQuery(search: string, parameters: Parameter[]): Record<string, number> {
  const query = new URLSearchParams(search);
  return Object.fromEntries(
    parameters.map((p) => {
      const given = query.get(p.id);
      if (given === null || given === '') return [p.id, p.default];
      const value = Number(given);
      return [p.id, Number.isFinite(value) ? value : NaN];
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
