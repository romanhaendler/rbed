// The search across all pages: one palette item per page (under its rubric), found by its title and by
// keywords from its Markdown: the methods its Java source calls (fenced code and the source of
// each Scenario it shows), the names in its inline code, its headings, its description and its
// text; and one item per section (## and ###), which leads to the section itself.
import { parse, slug } from './markdown.ts';
import { type Page, rubricOf } from './pages.ts';

// The group is the heading the items stand under; searchedGroup is empty, so the rows do not
// repeat it and a query does not find every item of a group by its name.
type SearchItem = { id: string; label: string; group: string; searchedGroup: string; keywords: string[] };

export function searchItems(
  pages: Page[],
  markdownOf: (page: Page) => string,
  sourceOf: (scenarioId: string) => string,
): SearchItem[] {
  return pages.flatMap((page) => {
    const markdown = markdownOf(page);
    const prose = markdown.replace(/^```[\s\S]*?^```$/gm, '').replace(/<!--.*?-->/g, '');
    const words = new Set<string>();
    for (const block of parse(markdown)) {
      if (block.kind === 'code') words.add(block.label);
      const java = block.kind === 'code' ? block.source : block.kind === 'scenario' ? sourceOf(block.id) : '';
      for (const m of java.matchAll(/\b([a-z]\w*)\s*\(/g)) words.add(m[1]);
    }
    for (const m of prose.matchAll(/`([^`\n]+)`/g)) {
      for (const name of m[1].match(/[A-Za-z_]\w+/g) ?? []) words.add(name);
    }
    const headings = [...prose.matchAll(/^#{2,3} +(.*)$/gm)].map((m) => m[1].replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/[`*]/g, ''));
    for (const heading of headings) words.add(heading);
    // The text as one keyword: the palette finds a query as a run of letters in it.
    const text = prose.replace(/[`*#]|\]\([^)]*\)|\[/g, '').replace(/\s+/g, ' ');
    return [
      { id: page.path, label: page.title, group: rubricOf(page), searchedGroup: '', keywords: [...words, page.description, text] },
      ...headings.map((heading) => ({ id: `${page.path}#${slug(heading)}`, label: heading, group: page.title, searchedGroup: '', keywords: [] })),
    ];
  });
}
