import { type PointerEvent, useRef } from 'react';
import type { ChannelAxis } from './chartModel.ts';
import { valueAt } from './plotArea.ts';

/** Over the chart while the reader draws a Channel: a stroke with mouse, pen or finger sets its
    values, rounded to a tenth of a tick step and kept inside the Channels' part of the plot.
    `touch-action: none` keeps a finger from scrolling the page. It takes no focus, so keyboard
    users keep the chart and the controls; drawing itself needs a pointer. */
export function DrawLayer({
  x,
  channelAxis,
  draw,
  end,
}: {
  x: [number, number];
  channelAxis: ChannelAxis;
  draw: (t: number, value: number, first: boolean) => void;
  end: () => void;
}) {
  const drawing = useRef(false);
  const point = (e: PointerEvent<HTMLDivElement>, first: boolean) => {
    const p = valueAt(e.currentTarget.parentElement!, e.clientX, e.clientY, x, channelAxis.domain);
    if (p === undefined) return;
    const step = channelAxis.ticks[1] - channelAxis.ticks[0];
    const value = Math.round(Math.max(p.y, channelAxis.ticks[0] - step / 2) / (step / 10)) * (step / 10);
    draw(p.x, Number(value.toPrecision(12)), first);
  };
  const stop = () => {
    drawing.current = false;
    end();
  };
  return (
    <div
      className="drawLayer"
      onPointerDown={(e) => {
        if (e.button !== 0) return;
        e.currentTarget.setPointerCapture(e.pointerId);
        drawing.current = true;
        point(e, true);
      }}
      onPointerMove={(e) => drawing.current && point(e, false)}
      onPointerUp={stop}
      onPointerCancel={stop}
    />
  );
}
