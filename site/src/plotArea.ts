// Where a pointer is in a chart's values. @umriss-ui/charts 0.40.0 has no public mapping from
// pixels to values and does not expose its plot rectangle, so this reads the rectangle from the
// chart's internal axis elements (.uc-axis-x.uc-axis-bottom spans the plot's width,
// .uc-axis-y.uc-axis-left its height) and maps linearly onto the axes' domains, which the caller
// must have fixed. This is the only module that depends on those internals (site.css styles a
// few of the same class names): replace it once the charts package offers a mapping. The class
// names may change with any release, which is why the package version is pinned.

/** The plot's rectangle inside `chart` in client pixels; none before the chart has drawn its axes. */
export function plotRect(chart: Element): { left: number; top: number; width: number; height: number } | undefined {
  const xAxis = chart.querySelector('.uc-axis-x.uc-axis-bottom')?.getBoundingClientRect();
  const yAxis = chart.querySelector('.uc-axis-y.uc-axis-left')?.getBoundingClientRect();
  if (!xAxis?.width || !yAxis?.height) return undefined;
  return { left: xAxis.left, top: yAxis.top, width: xAxis.width, height: yAxis.height };
}

/** The x and y values at a client point inside `chart`, clamped to the plot; none before the
    chart has drawn its axes. */
export function valueAt(
  chart: Element,
  clientX: number,
  clientY: number,
  x: [number, number],
  y: [number, number],
): { x: number; y: number } | undefined {
  const plot = plotRect(chart);
  if (plot === undefined) return undefined;
  const across = Math.min(Math.max((clientX - plot.left) / plot.width, 0), 1);
  const up = Math.min(Math.max((plot.top + plot.height - clientY) / plot.height, 0), 1);
  return { x: x[0] + across * (x[1] - x[0]), y: y[0] + up * (y[1] - y[0]) };
}
