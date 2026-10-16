// A page of src/pages.ts: its rubric and its Markdown twin to copy or view, then its Markdown
// (src/content/), with each fenced block as a code block with a copy button and each Scenario
// marker as the live Scenario.
import { useMemo } from 'react';
import { scenarios } from './bridge';
import { CodeBlock, CopyButton } from './CodeBlock';
import { parse } from './markdown.ts';
import { markdownFile, type Page } from './pages.ts';
import { ScenarioView } from './ScenarioView';

const SOURCES = import.meta.glob<string>('./content/*.md', { query: '?raw', import: 'default', eager: true });

export const CATALOGUE = scenarios();

/** A page's Markdown, with the build's rbed version for {{version}} (as prerender.mjs reads it). */
export function markdownOf(page: Page): string {
  return SOURCES[`./content/${markdownFile(page)}`].replaceAll('{{version}}', __RBED_VERSION__);
}

/** The page's twin, as the build wrote it: its text with each Scenario's source and Events. */
async function twinOf(page: Page): Promise<string> {
  const response = await fetch(import.meta.env.BASE_URL + markdownFile(page));
  if (!response.ok) throw new Error(`${response.status} for ${markdownFile(page)}`);
  return response.text();
}

export function MarkdownPage({ page }: { page: Page }) {
  const blocks = useMemo(() => parse(markdownOf(page)), [page]);
  return (
    <article className={page.path === '/' ? 'page front' : 'page'}>
      <div className="pageHead">
        {page.rubric && <p className="pageRubric">{page.rubric}</p>}
        <span className="pageActions">
          <CopyButton text={() => twinOf(page)} label="Copy page" />
          <a href={import.meta.env.BASE_URL + markdownFile(page)}>View as Markdown</a>
        </span>
      </div>
      {blocks.map((block, i) =>
        block.kind === 'html' ? (
          <div key={i} className="flow" dangerouslySetInnerHTML={{ __html: block.html }} />
        ) : block.kind === 'code' ? (
          <div key={i} className="codeCard">
            <CodeBlock language={block.language} label={block.label} source={block.source} />
          </div>
        ) : (
          <ScenarioView key={block.id} scenario={CATALOGUE.find((s) => s.id === block.id)!} draw={block.draw} />
        ),
      )}
    </article>
  );
}
