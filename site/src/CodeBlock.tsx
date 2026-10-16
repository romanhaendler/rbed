// A code block with a copy button, after the umriss-ui demo's CodeBlock and CopyButton (MIT).
import { VisuallyHidden } from '@umriss-ui/core';
import { useEffect, useRef, useState } from 'react';
import { javaTokens } from './java';

type CopyState = 'settle' | 'copied' | 'failed';

/** Copies `text`, or the text it fetches; says "Copied" or "Failed" on itself for 1.6 s and
    never throws into the page. */
export function CopyButton({ text, label = 'Copy' }: { text: string | (() => Promise<string>); label?: string }) {
  const [state, setState] = useState<CopyState>('settle');

  useEffect(() => {
    if (state === 'settle') return;
    const t = window.setTimeout(() => setState('settle'), 1600);
    return () => window.clearTimeout(t);
  }, [state]);

  const copy = async () => {
    try {
      if (typeof text === 'string') await navigator.clipboard.writeText(text);
      // Handed the fetch as it runs, so the click still counts as the reader's (Safari).
      else await navigator.clipboard.write([new ClipboardItem({ 'text/plain': text().then((t) => new Blob([t], { type: 'text/plain' })) })]);
      setState('copied');
    } catch {
      setState('failed');
    }
  };

  return (
    <>
      <button type="button" className="codeCopy" onClick={() => void copy()} data-state={state}>
        {state === 'settle' ? label : state === 'copied' ? 'Copied' : 'Failed'}
      </button>
      {/* A changed button text is not announced; a status is. */}
      <VisuallyHidden role="status">{state === 'copied' ? 'Copied' : state === 'failed' ? 'Copying failed' : ''}</VisuallyHidden>
    </>
  );
}

/** `language` picks the highlighting; `label` is what the bar says (the language if none). */
export function CodeBlock({ language, label = language, source }: { language: string; label?: string; source: string }) {
  // A line that runs past the edge fades out there, until the reader has scrolled to its end.
  const pre = useRef<HTMLPreElement>(null);
  const [cut, setCut] = useState(false);
  const measure = () => {
    const p = pre.current;
    if (p) setCut(p.scrollLeft + p.clientWidth < p.scrollWidth - 1);
  };
  useEffect(() => {
    const observer = new ResizeObserver(measure);
    observer.observe(pre.current!);
    return () => observer.disconnect();
  }, []);
  useEffect(measure, [source]);
  return (
    <div className="codeBlock">
      <div className="codeBar">
        <span className="codeLanguage">{label}</span>
        <CopyButton text={source} />
      </div>
      {/* A long line scrolls the block sideways, so the keyboard has to reach it too. */}
      <pre ref={pre} className="code" tabIndex={0} aria-label={`${label} source`} data-cut={cut || undefined} onScroll={measure}>
        <code>
          {language.toLowerCase() === 'java'
            ? javaTokens(source).map(([kind, text], i) =>
                kind === '' ? text : <span key={i} className={`java-${kind}`}>{text}</span>,
              )
            : source}
        </code>
      </pre>
    </div>
  );
}
