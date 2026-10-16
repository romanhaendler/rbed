// A code block with a copy button, after the umriss-ui demo's CodeBlock and CopyButton (MIT).
import { useEffect, useState } from 'react';

type CopyState = 'settle' | 'copied' | 'failed';

/** Copies `text`; says "Copied" or "Failed" on itself for 1.6 s and never throws into the page. */
export function CopyButton({ text }: { text: string }) {
  const [state, setState] = useState<CopyState>('settle');

  useEffect(() => {
    if (state === 'settle') return;
    const t = window.setTimeout(() => setState('settle'), 1600);
    return () => window.clearTimeout(t);
  }, [state]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed');
    }
  };

  return (
    <button type="button" className="codeCopy" onClick={() => void copy()} data-state={state}>
      {state === 'settle' ? 'Copy' : state === 'copied' ? 'Copied' : 'Failed'}
    </button>
  );
}

export function CodeBlock({ language, source }: { language: string; source: string }) {
  return (
    <div className="codeBlock">
      <div className="codeBar">
        <span className="codeLanguage">{language}</span>
        <CopyButton text={source} />
      </div>
      {/* A long line scrolls the block sideways, so the keyboard has to reach it too. */}
      <pre className="code" tabIndex={0} aria-label={`${language} source`}>
        <code>{source}</code>
      </pre>
    </div>
  );
}
