// A stroke drawn over a Channel: each point sets the Sample nearest in time, and the Samples the
// pointer passed since the stroke's previous point lie on the straight line between the two, so
// a fast stroke leaves no gaps. A Sample the stroke does not reach keeps its value, or stays
// without one (null). And the Java source with the drawn values. Imports nothing, so Node tests
// it directly.

/** The values with one more point of a stroke drawn, and that point (Sample index, value). */
export function drawTo(
  values: (number | null)[],
  times: number[],
  t: number,
  value: number,
  previous?: [number, number],
): { values: (number | null)[]; last: [number, number] } {
  let index = 0;
  for (let i = 1; i < times.length; i++) if (Math.abs(times[i] - t) < Math.abs(times[index] - t)) index = i;
  const next = [...values];
  const [from, fromValue] = previous ?? [index, value];
  const step = index > from ? 1 : -1;
  for (let i = from; i !== index; i += step) next[i] = fromValue + ((value - fromValue) * (i - from)) / (index - from);
  next[index] = value;
  return { values: next, last: [index, value] };
}

const ARRAY = /^([ \t]*)(double\[\] values = \{)[^}]*\};/m;
const PUSHED = /Map\.of\((\w+), (-?[\d.]+)\)/g;

/** The Java source with the drawn values (by Channel, one per Sample) in it: in place of its
 *  array of the Sample values, or of the values it pushes if it pushes every Sample; otherwise a
 *  last line says which Channels were drawn, so the source never shows values the chart does not. */
export function drawnSource(source: string, drawn: Record<string, (number | null)[]>): string {
  const channels = Object.keys(drawn);
  if (channels.length === 0) return source;
  const values = drawn[channels[0]];
  const complete = channels.length === 1 && values.every((v) => v !== null);
  if (complete && ARRAY.test(source)) {
    return source.replace(ARRAY, (_, indent: string, head: string) => {
      const lines = [];
      for (let i = 0; i < values.length; i += 15) lines.push(values.slice(i, i + 15).join(', '));
      return `${indent}${head}${lines.join(`,\n${indent}    `)}};`;
    });
  }
  const pushed = [...source.matchAll(PUSHED)];
  if (complete && pushed.length === values.length && pushed.every((m) => m[1] === channels[0])) {
    let k = 0;
    return source.replace(PUSHED, (_, channel: string) => {
      const v = values[k++] as number;
      return `Map.of(${channel}, ${Number.isInteger(v) ? `${v}.0` : v})`;
    });
  }
  return `${source.endsWith('\n') ? source : `${source}\n`}// The values of ${channels.join(' and ')} are the ones drawn on the chart.\n`;
}
