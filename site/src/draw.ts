// A stroke drawn over a Channel: each point sets the Sample nearest in time, and the Samples the
// pointer passed since the stroke's previous point lie on the straight line between the two, so
// a fast stroke leaves no gaps. Imports nothing, so Node tests it directly.

/** The values with one more point of a stroke drawn, and that point (Sample index, value). */
export function drawTo(
  values: number[],
  times: number[],
  t: number,
  value: number,
  previous?: [number, number],
): { values: number[]; last: [number, number] } {
  let index = 0;
  for (let i = 1; i < times.length; i++) if (Math.abs(times[i] - t) < Math.abs(times[index] - t)) index = i;
  const next = [...values];
  const [from, fromValue] = previous ?? [index, value];
  const step = index > from ? 1 : -1;
  for (let i = from; i !== index; i += step) next[i] = fromValue + ((value - fromValue) * (i - from)) / (index - from);
  next[index] = value;
  return { values: next, last: [index, value] };
}
