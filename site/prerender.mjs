// What the site is for a reader without JavaScript, a coding agent or a search engine, after the
// umriss-ui Pages (ADR-0037 there): for every page of src/pages.ts its own index.html with the
// head and the prerendered text, Java source and default Events, and its Markdown twin; then
// llms.txt, llms-full.txt and sitemap.xml. vite.config.ts runs prerender() after `vite build`.
//
// The default Events come from the compiled Scenario module (scenarios/target/js/rbed.js) run in
// Node, not from a second JVM step: the Node parity test, which runs before every build in the
// Pages workflow, holds that module to the JVM's results for every Scenario at its defaults.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { aggregatesLine } from './src/aggregates.ts';
import { escape, parse, SCENARIO_MARKER } from './src/markdown.ts';
import { markdownFile, PAGES, titleOf } from './src/pages.ts';

export const SITE = 'https://romanhaendler.github.io/rbed/';
const REPOSITORY = 'https://github.com/romanhaendler/rbed';
const RUBRICS = ['Overview', 'Guide', 'Examples'];

const site = (path) => new URL(path, import.meta.url);
const rubricOf = (page) => page.rubric ?? 'Overview';

/** The rbed version of this commit, as the root pom states it. */
export function rbedVersion() {
  const pom = readFileSync(site('../pom.xml'), 'utf8');
  return /<artifactId>rbed<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom)[1];
}

/** A page's Markdown, with the build's rbed version for {{version}}. */
export function markdownOf(page, version) {
  return readFileSync(site(`src/content/${markdownFile(page)}`), 'utf8').replaceAll('{{version}}', version);
}

function factOf(facts, id) {
  const fact = facts.get(id);
  if (fact === undefined) throw new Error(`No Scenario ${id} in the catalogue (Bridge.SCENARIOS)`);
  return fact;
}

/** The Event table at the default parameters as rows of text, the header first; or a sentence. */
function eventRows({ unit, millis, result }) {
  if ('error' in result) return `rbed rejects the default parameters: ${result.error}`;
  if (result.events.length === 0) return 'No Event at the default parameters.';
  // A Note column only for a Scenario whose listener notes something, as in the live table.
  const notes = result.events.some((e) => e.note !== undefined);
  return [
    ['Name', `Start (${unit})`, `Confirmed (${unit})`, `End (${unit})`, 'Truncated', ...(notes ? ['Note'] : []), 'Aggregates'],
    ...result.events.map((e) => [
      e.name,
      String(e.start / millis),
      String(e.confirmed / millis),
      String(e.end / millis),
      [e.truncatedStart && 'start', e.truncatedEnd && 'end'].filter(Boolean).join(', '),
      ...(notes ? [e.note ?? ''] : []),
      Object.entries(e.aggregates).map(([channel, a]) => aggregatesLine(channel, a)).join('; '),
    ]),
  ];
}

/** The Markdown twin: the page's Markdown, each Scenario marker replaced by its source and Events. */
export function twin(markdown, facts) {
  return markdown.replace(new RegExp(SCENARIO_MARKER.source, 'gm'), (_, id) => {
    const fact = factOf(facts, id);
    const rows = eventRows(fact);
    const events =
      typeof rows === 'string'
        ? rows
        : 'Events at the default parameters:\n\n' +
          [rows[0], rows[0].map(() => '---'), ...rows.slice(1)].map((r) => `| ${r.join(' | ')} |`).join('\n');
    return '```java\n' + fact.source + '\n```\n\n' + events;
  });
}

function scenarioHtml(fact) {
  const rows = eventRows(fact);
  const table =
    typeof rows === 'string'
      ? `<p>${escape(rows)}</p>`
      : '<table class="eventTable"><caption>Events at the default parameters</caption>' +
        `<thead><tr>${rows[0].map((h) => `<th>${escape(h)}</th>`).join('')}</tr></thead><tbody>` +
        rows
          .slice(1)
          .map((r) => `<tr>${r.map((c) => `<td>${escape(c)}</td>`).join('')}</tr>`)
          .join('') +
        '</tbody></table>';
  return `<pre><code>${escape(fact.source)}</code></pre>\n${table}`;
}

/** The built index.html with the page's head, and its text inside #root until the app replaces it. */
export function pageHtml(template, page, markdown, facts) {
  if (!template.includes('<title>rbed</title>') || !template.includes('<div id="root"></div>')) {
    throw new Error('The template has no <title>rbed</title> or no empty #root');
  }
  const url = SITE + page.path.slice(1);
  const title = escape(titleOf(page));
  const description = escape(page.description);
  const head = [
    `<title>${title}</title>`,
    `<meta name="description" content="${description}" />`,
    `<link rel="canonical" href="${url}" />`,
    `<link rel="alternate" type="text/markdown" href="${SITE}${markdownFile(page)}" />`,
    '<meta property="og:type" content="website" />',
    '<meta property="og:site_name" content="rbed" />',
    `<meta property="og:title" content="${title}" />`,
    `<meta property="og:description" content="${description}" />`,
    `<meta property="og:url" content="${url}" />`,
    `<meta property="og:image" content="${SITE}og-image.png" />`,
    '<meta property="og:image:width" content="1200" />',
    '<meta property="og:image:height" content="630" />',
    '<meta name="twitter:card" content="summary_large_image" />',
    // With JavaScript the prerendered text stays for crawlers but is not seen; the app replaces
    // it on its first render, so nothing jumps. Without, it is the page, plainly styled.
    "<script>document.documentElement.classList.add('js')</script>",
    '<style>.js .prerendered{visibility:hidden}.prerendered{max-width:48rem;margin:0 auto;padding:2rem 1rem;font:15px/1.55 system-ui}.prerendered pre,.prerendered table{display:block;overflow-x:auto}</style>',
  ].join('\n    ');
  const body = parse(markdown)
    .map((b) =>
      b.kind === 'html' ? b.html : b.kind === 'code' ? `<pre><code>${escape(b.source)}</code></pre>` : scenarioHtml(factOf(facts, b.id)),
    )
    .join('\n');
  return template
    .replace('<title>rbed</title>', () => head)
    .replace('<div id="root"></div>', () => `<div id="root"><div class="prerendered">\n${body}\n</div></div>`);
}

export function llmsTxt(pages) {
  const front = pages.find((p) => p.path === '/');
  const sections = RUBRICS.map((rubric) => {
    const entries = pages.filter((p) => rubricOf(p) === rubric);
    return entries.length === 0
      ? ''
      : `## ${rubric}\n\n${entries.map((p) => `- [${p.title}](${SITE}${markdownFile(p)}): ${p.description}`).join('\n')}\n\n`;
  }).join('');
  return (
    `# rbed\n\n> ${front.description}\n\n` +
    `The site at ${SITE} runs rbed live in the browser. Each page below is its Markdown twin. ` +
    `The complete documentation as one file: [llms-full.txt](${SITE}llms-full.txt)\n\n` +
    sections +
    '## Reference\n\n' +
    `- [README](${REPOSITORY}/blob/main/README.md): the complete guide to the library, chapter by chapter\n` +
    `- [Glossary](${REPOSITORY}/blob/main/CONTEXT.md): the domain terms (Sample, Channel, Rule, Event, Detector, the Event lifecycle)\n` +
    '- [Javadoc](https://javadoc.io/doc/io.github.romanhaendler/rbed): the contract of every public type and method\n' +
    `- [llms-full.txt](${SITE}llms-full.txt): the README, the glossary and every page in one plain-text file\n`
  );
}

/** Relative links of a Markdown file in the repository root, made absolute. */
function absoluteLinks(markdown) {
  return markdown.replace(/\]\((?!https?:|mailto:)([^)\s]+)\)/g, (_, link) =>
    link.startsWith('#') ? `](${REPOSITORY}${link})` : `](${REPOSITORY}/blob/main/${link})`,
  );
}

export function llmsFull(pages, twins, readme, context) {
  const front = pages.find((p) => p.path === '/');
  return [
    `# rbed: the complete documentation\n\n> ${front.description}\n\n` +
      `The README of ${REPOSITORY}, then the glossary (CONTEXT.md), then every page of ${SITE} as Markdown, in the order of the site's navigation.`,
    absoluteLinks(readme).trim(),
    absoluteLinks(context).trim(),
    ...pages.map((p, i) => `Page: ${SITE}${p.path.slice(1)}\n\n${twins[i].trim()}`),
  ].join('\n\n---\n\n') + '\n';
}

export function sitemap(pages, date) {
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    pages.map((p) => `  <url><loc>${SITE}${p.path.slice(1)}</loc><lastmod>${date}</lastmod></url>\n`).join('') +
    '</urlset>\n'
  );
}

/** Writes every page's HTML and twin, llms.txt, llms-full.txt, sitemap.xml and 404.html into dist. */
export async function prerender(dist, version) {
  const bridge = await import('./scenarios/target/js/rbed.js');
  const facts = new Map(
    JSON.parse(bridge.scenarios()).map((s) => [
      s.id,
      {
        source: s.source.replace(/\$\{(\w+)\}/g, (_, id) => String(s.parameters.find((p) => p.id === id).default)).trimEnd(),
        unit: s.timeUnit.name,
        millis: s.timeUnit.millis,
        result: JSON.parse(bridge.run(s.id, '{}', '{}')),
      },
    ]),
  );
  const write = (path, text) => {
    mkdirSync(dirname(`${dist}/${path}`), { recursive: true });
    writeFileSync(`${dist}/${path}`, text);
  };
  const template = readFileSync(`${dist}/index.html`, 'utf8');
  // GitHub Pages serves it for any other path; the app shows its "no page" view there.
  write('404.html', template.replace('<title>rbed</title>', '<title>No page · rbed</title><meta name="robots" content="noindex" />'));
  const twins = PAGES.map((page) => {
    const markdown = markdownOf(page, version);
    write(`${page.path.slice(1)}index.html`, pageHtml(template, page, markdown, facts));
    const text = twin(markdown, facts);
    write(markdownFile(page), text);
    return text;
  });
  write('llms.txt', llmsTxt(PAGES));
  const root = (file) => readFileSync(fileURLToPath(site(`../${file}`)), 'utf8');
  write('llms-full.txt', llmsFull(PAGES, twins, root('README.md'), root('CONTEXT.md')));
  write('sitemap.xml', sitemap(PAGES, new Date().toISOString().slice(0, 10)));
}
