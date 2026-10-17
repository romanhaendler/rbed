// A page of src/pages.ts: its rubric and its Markdown twin to copy or view, then its Markdown
// (src/content/), with each fenced block as a code block with a copy button (fenced blocks right
// after one another as tabs of one) and each Scenario marker as the live Scenario.
import { Tab, TabList, TabPanel, Tabs } from '@umriss-ui/core';
import { useMemo } from 'react';
import { scenarios } from './bridge';
import { CodeBlock, CopyButton } from './CodeBlock';
import { type Block, parse } from './markdown.ts';
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

type CodeBlockOf = Extract<Block, { kind: 'code' }>;

/** The blocks, with each run of fenced code blocks right after one another (Maven and Gradle) as
    one group: they say the same thing in different forms, so they stand as tabs. */
function grouped(blocks: Block[]): (Block | CodeBlockOf[])[] {
  const out: (Block | CodeBlockOf[])[] = [];
  for (const block of blocks) {
    const last = out.at(-1);
    if (block.kind !== 'code') out.push(block);
    else if (Array.isArray(last)) last.push(block);
    else if (last !== undefined && !Array.isArray(last) && last.kind === 'code') out[out.length - 1] = [last, block];
    else out.push(block);
  }
  return out;
}

export function MarkdownPage({ page }: { page: Page }) {
  const blocks = useMemo(() => grouped(parse(markdownOf(page))), [page]);
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
        Array.isArray(block) ? (
          <Tabs key={i} className="codeCard codeTabs" defaultValue={block[0].label}>
            <TabList aria-label={block.map((b) => b.label).join(' or ')}>
              {block.map((b) => (
                <Tab key={b.label} value={b.label}>
                  {b.label}
                </Tab>
              ))}
            </TabList>
            {block.map((b) => (
              <TabPanel key={b.label} value={b.label}>
                <CodeBlock language={b.language} label={b.label} source={b.source} />
              </TabPanel>
            ))}
          </Tabs>
        ) : block.kind === 'html' ? (
          <div key={i} className="flow" dangerouslySetInnerHTML={{ __html: block.html }} />
        ) : block.kind === 'code' ? (
          <div key={i} className="codeCard">
            <CodeBlock language={block.language} label={block.label} source={block.source} />
          </div>
        ) : (
          <ScenarioView key={block.id} scenario={CATALOGUE.find((s) => s.id === block.id)!} draw={block.draw} hidden={block.hidden} />
        ),
      )}
    </article>
  );
}
