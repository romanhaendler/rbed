// The search across all pages: one palette item per page, found by its title and rubric, and by
// keywords from its Markdown: the methods its Java source calls (fenced code and the source of
// each Scenario it shows), the names in its inline code, and its headings.
import { parse } from './markdown.ts';
import { type Page, rubricOf } from './pages.ts';

export type SearchItem = { id: string; label: string; group: string; keywords: string[] };

export function searchItems(
  pages: Page[],
  markdownOf: (page: Page) => string,
  sourceOf: (scenarioId: string) => string,
): SearchItem[] {
  return pages.map((page) => {
    const markdown = markdownOf(page);
    const prose = markdown.replace(/^```[\s\S]*?^```$/gm, '');
    const words = new Set<string>();
    for (const block of parse(markdown)) {
      const java = block.kind === 'code' ? block.source : block.kind === 'scenario' ? sourceOf(block.id) : '';
      for (const m of java.matchAll(/\b([a-z]\w*)\s*\(/g)) words.add(m[1]);
    }
    for (const m of prose.matchAll(/`([^`\n]+)`/g)) {
      for (const name of m[1].match(/[A-Za-z_]\w+/g) ?? []) words.add(name);
    }
    for (const m of prose.matchAll(/^#{2,3} +(.*)$/gm)) words.add(m[1].replace(/[`*]/g, ''));
    return { id: page.path, label: page.title, group: rubricOf(page), keywords: [...words] };
  });
}
