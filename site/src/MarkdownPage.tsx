// A page of src/pages.ts: its Markdown (src/content/), with each fenced block as a code block
// with a copy button and each Scenario marker as the live Scenario.
import { useMemo } from 'react';
import { scenarios } from './bridge';
import { CodeBlock } from './CodeBlock';
import { parse } from './markdown.ts';
import { markdownFile, type Page } from './pages.ts';
import { ScenarioView } from './ScenarioView';

const SOURCES = import.meta.glob<string>('./content/*.md', { query: '?raw', import: 'default', eager: true });

export const CATALOGUE = scenarios();

/** A page's Markdown, with the build's rbed version for {{version}} (as prerender.mjs reads it). */
export function markdownOf(page: Page): string {
  return SOURCES[`./content/${markdownFile(page)}`].replaceAll('{{version}}', __RBED_VERSION__);
}

export function MarkdownPage({ page }: { page: Page }) {
  const blocks = useMemo(() => parse(markdownOf(page)), [page]);
  return (
    <article className={page.path === '/' ? 'page front' : 'page'}>
      {blocks.map((block, i) =>
        block.kind === 'html' ? (
          <div key={i} className="flow" dangerouslySetInnerHTML={{ __html: block.html }} />
        ) : block.kind === 'code' ? (
          <div key={i} className="codeCard">
            <CodeBlock language={block.label} source={block.source} />
          </div>
        ) : (
          <ScenarioView key={block.id} scenario={CATALOGUE.find((s) => s.id === block.id)!} draw={block.draw} />
        ),
      )}
    </article>
  );
}
