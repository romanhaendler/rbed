// What the site is for a reader without JavaScript, a coding agent or a search engine, after the
// umriss-ui Pages (ADR-0037 there): for every page of src/pages.ts its own index.html with the
// head and the prerendered text, Java source and default Events, and its Markdown twin; then
// llms.txt, llms-full.txt, sitemap.xml, 404.html and the Javadoc. vite.config.mjs runs prerender()
// after `vite build`.
//
// The default Events come from the compiled Scenario module (scenarios/target/js/rbed.js) run in
// Node, not from a second JVM step: the Node parity test, which runs before every build in the
// Pages workflow, holds that module to the JVM's results for every Scenario at its defaults.
import { appendFileSync, cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { aggregatesText, discardedCells, discardedHead, eventCells, eventHead, forDisplay, hasNotes } from './src/events.ts';
import { escape, LIVE_MARKER, parse, SCENARIO_MARKER } from './src/markdown.ts';
import { markdownFile, PAGES, REPOSITORY, RUBRICS, rubricOf, titleOf } from './src/pages.ts';
import { defaultsOf, fillSource } from './src/parameters.ts';

export const SITE = 'https://romanhaendler.github.io/rbed/';

const site = (path) => new URL(path, import.meta.url);

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
  if ('error' in result) {
    return `${result.by === 'rbed' ? 'rbed rejects' : 'The site cannot run'} the default parameters: ${result.error}`;
  }
  if (result.events.length === 0) return 'No Event at the default parameters.';
  // The live table's columns (src/events.ts), with the Aggregates as a column of their own.
  const notes = hasNotes(result.events);
  return [
    [...eventHead(unit, notes), 'Aggregates'],
    ...result.events.map((e) => [...eventCells(e, millis, notes), aggregatesText(e)]),
  ];
}

/** The Discarded Pending Events at the default parameters, like eventRows; none, no rows. */
function discardedRows({ unit, millis, result }) {
  if ('error' in result || result.discarded.length === 0) return [];
  return [discardedHead(unit), ...result.discarded.map((d) => discardedCells(d, millis))];
}

/** The tables of a Scenario at its defaults: a caption with its rows, or a sentence. */
function tables(fact) {
  const events = eventRows(fact);
  const discarded = discardedRows(fact);
  return [
    typeof events === 'string' ? events : { caption: 'Events at the default parameters', rows: events },
    ...(discarded.length === 0 ? [] : [{ caption: 'Discarded Pending Events at the default parameters', rows: discarded }]),
  ];
}

/** The page's links made absolute, outside fenced and inline code: a twin is read away from its
 *  page (at <name>.md, in llms-full.txt), where a link relative to the page would go wrong. */
function absolutePageLinks(markdown, page) {
  const base = SITE + page.path.slice(1);
  let fenced = false;
  return markdown
    .split('\n')
    .map((line) => {
      if (line.startsWith('```')) fenced = !fenced;
      if (fenced) return line;
      return line.replace(/`[^`]*`|(\[(?:[^\]`]|`[^`]*`)+\]\()([^)\s]+)\)/g, (all, text, link) =>
        text === undefined ? all : `${text}${new URL(link, base).href})`,
      );
    })
    .join('\n');
}

/** A Scenario's parameters as a list, for a reader who cannot move the controls: each as the page
 *  address names it, with its range or options and its default. */
function parametersText(parameters = []) {
  if (parameters.length === 0) return '';
  const line = (p) =>
    p.options
      ? `- ${p.label} (\`${p.id}\`): ${p.options.map((o, i) => `${i} for ${o.label}${i === p.default ? ' (default)' : ''}`).join(', ')}`
      : `- ${p.label} (\`${p.id}\`): ${p.min} to ${p.max}${p.unit ? ` ${p.unit}` : ''} in steps of ${p.step}, default ${p.default}`;
  return `Parameters (in the page address as ?${parameters[0].id}=…):\n\n${parameters.map(line).join('\n')}\n\n`;
}

/** The Markdown without the blocks that speak of the live page's controls (<!-- live -->). */
const withoutLive = (markdown) => markdown.replace(new RegExp(`^${LIVE_MARKER}\\n(?:[^\\n]+\\n?)*\\n*`, 'gm'), '');

/** The Markdown twin: the page's Markdown, each Scenario marker replaced by its source, its
 *  parameters and its Events, and its links absolute. */
export function twin(markdown, facts, page) {
  return absolutePageLinks(withoutLive(markdown), page).replace(new RegExp(SCENARIO_MARKER.source, 'gm'), (_, id) => {
    const fact = factOf(facts, id);
    const row = (r) => `| ${r.map((c) => c.replace(/\|/g, '\\|')).join(' | ')} |`;
    const text = tables(fact).map((t) =>
      typeof t === 'string'
        ? t
        : `${t.caption}:\n\n` + [t.rows[0], t.rows[0].map(() => '---'), ...t.rows.slice(1)].map(row).join('\n'),
    );
    return '```java\n' + fact.source + '\n```\n\n' + parametersText(fact.parameters) + text.join('\n\n');
  });
}

/** The site's path below its host: /rbed/. */
const BASE = new URL(SITE).pathname;

/** The navigation as the app's sidebar shows it, the current page marked. */
function railHtml(pages, current) {
  const entry = (p, title) =>
    `<a class="railEntry" href="${BASE}${p.path.slice(1)}"${p.path === current ? ' aria-current="page"' : ''}>${escape(title)}</a>`;
  const rubrics = RUBRICS.slice(1).map((rubric) => {
    const entries = pages.filter((p) => rubricOf(p) === rubric);
    return entries.length === 0
      ? ''
      : `<div class="railRubric"><p class="railHead"><span>${rubric}</span><span class="railCount">${entries.length}</span></p>` +
          `<ul class="railList">${entries.map((p) => `<li>${entry(p, p.title)}</li>`).join('')}</ul></div>`;
  });
  return `<nav class="rail" aria-label="Pages">${entry(pages.find((p) => p.path === '/'), 'Overview')}${rubrics.join('')}</nav>`;
}

/** #root as a reader without JavaScript sees it: the header, the navigation and the content. */
function rootHtml(pages, current, content) {
  return (
    '<div id="root"><div class="prerendered">' +
    `<header class="shellHead"><a class="shellMark" href="${BASE}">rbed</a></header>` +
    `<div class="shellBody">${railHtml(pages, current)}<main class="shellContent"><article class="page"><div class="flow">\n` +
    content +
    '\n</div></article></main></div></div></div>'
  );
}

// With JavaScript, main.tsx marks the page (.js) right before the app replaces #root, so the
// prerendered text stays for crawlers but is not seen; if the app does not start, it stays. Without
// JavaScript, it is the page, in the app's header and sidebar; on a narrow screen, where the app
// has a drawer, the sidebar goes under the content.
const STYLE =
  '<style>.js .prerendered{visibility:hidden}' +
  '.prerendered pre,.prerendered table{display:block;overflow-x:auto}' +
  '.prerendered pre{margin:0;padding:var(--u-space-4);border-radius:var(--u-radius-md);background:var(--u-color-surface-sunken)}' +
  '@media (max-width:900px){.prerendered .shellBody>.rail{display:block;order:1;position:static;height:auto;border-right:none;border-top:1px solid var(--u-edge-color)}}</style>';

// Only seen without JavaScript: with it, the app has replaced the prerendered page. A plain
// paragraph, not <noscript>, which Chromium with scripts turned off from outside (as the browser
// test does) still treats as text.
const NO_SCRIPT =
  '<p>The live chart and its controls need JavaScript. The tables show the Events at the default parameters.</p>';

function scenarioHtml(fact) {
  const html = tables(fact).map((t) =>
    typeof t === 'string'
      ? `<p>${escape(t)}</p>`
      : `<table class="eventTable"><caption>${escape(t.caption)}</caption>` +
        `<thead><tr>${t.rows[0].map((h) => `<th>${escape(h)}</th>`).join('')}</tr></thead><tbody>` +
        t.rows
          .slice(1)
          .map((r) => `<tr>${r.map((c) => `<td>${escape(c)}</td>`).join('')}</tr>`)
          .join('') +
        '</tbody></table>',
  );
  return `<pre><code>${escape(fact.source)}</code></pre>\n${NO_SCRIPT}\n${html.join('\n')}`;
}

function checkTemplate(template) {
  if (!template.includes('<title>rbed</title>') || !template.includes('<div id="root"></div>')) {
    throw new Error('The template has no <title>rbed</title> or no empty #root');
  }
}

/** The built index.html with the page's head, and its text inside #root until the app replaces it. */
export function pageHtml(template, page, markdown, facts, pages = PAGES) {
  checkTemplate(template);
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
    STYLE,
  ].join('\n    ');
  const body = parse(markdown)
    .filter((b) => !(b.kind === 'html' && b.live))
    .map((b) =>
      b.kind === 'html' ? b.html : b.kind === 'code' ? `<pre><code>${escape(b.source)}</code></pre>` : scenarioHtml(factOf(facts, b.id)),
    )
    .join('\n');
  return template.replace('<title>rbed</title>', () => head).replace('<div id="root"></div>', () => rootHtml(pages, page.path, body));
}

/** 404.html, which GitHub Pages serves for any other path: the app's "no page" view, and the
 *  navigation. */
export function notFoundHtml(template, pages = PAGES) {
  checkTemplate(template);
  const content =
    '<h1 id="no-page" tabindex="-1">No page at this address</h1>\n' +
    `<p>The page may have moved. Start again from the <a href="${BASE}">front page</a>, or choose a page from the list.</p>`;
  return template
    .replace('<title>rbed</title>', () => `<title>No page · rbed</title><meta name="robots" content="noindex" />\n    ${STYLE}`)
    .replace('<div id="root"></div>', () => rootHtml(pages, undefined, content));
}

/** The links of the pages into the site's Javadoc (below api/) that name no file or no member
 *  (anchor) of the generated Javadoc in `apidocs`, as "path links target". */
export function javadocLinkErrors(markdownByPage, apidocs) {
  const files = new Map();
  const read = (file) => {
    if (!files.has(file)) files.set(file, existsSync(file) ? readFileSync(file, 'utf8') : undefined);
    return files.get(file);
  };
  const errors = [];
  for (const [page, markdown] of markdownByPage) {
    const text = markdown.replace(/```[\s\S]*?```|`[^`\n]*`/g, '');
    for (const [, link] of text.matchAll(/\]\(([^)\s]+)\)/g)) {
      const url = new URL(link, `https://site.example${BASE}${page.path.slice(1)}`);
      if (!url.pathname.startsWith(`${BASE}api/`)) continue;
      const html = read(`${apidocs}/${decodeURIComponent(url.pathname.slice(BASE.length + 'api/'.length))}`);
      const anchor = decodeURIComponent(url.hash.slice(1));
      if (html === undefined || (anchor !== '' && !html.includes(`id="${anchor}"`))) errors.push(`${page.path} links ${link}`);
    }
  }
  return errors;
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
    `- [Javadoc](${SITE}api/): the contract of every public type and method, of the rbed this site runs\n` +
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
    `Source: ${REPOSITORY}/blob/main/README.md\n\n${absoluteLinks(readme).trim()}`,
    // CONTEXT.md starts with "# rbed" as the front page does; here it is the glossary.
    `Source: ${REPOSITORY}/blob/main/CONTEXT.md\n\n${absoluteLinks(context).trim().replace(/^# .*/, '# rbed glossary (CONTEXT.md)')}`,
    ...pages.map((p, i) => `Page: ${SITE}${p.path.slice(1)}\n\n${twins[i].trim()}`),
  ].join('\n\n---\n\n') + '\n';
}

/** No lastmod: the build date would mark every page changed on every deploy. */
export function sitemap(pages) {
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    pages.map((p) => `  <url><loc>${SITE}${p.path.slice(1)}</loc></url>\n`).join('') +
    '</urlset>\n'
  );
}

/** Writes every page's HTML and twin, llms.txt, llms-full.txt, sitemap.xml and 404.html into dist,
 *  and rbed's Javadoc of this commit (built by npm run install:rbed) into dist/api. */
export async function prerender(dist, version) {
  const apidocs = fileURLToPath(site('../target/reports/apidocs'));
  if (!existsSync(`${apidocs}/index.html`)) {
    throw new Error(`No Javadoc in ${apidocs}: npm run install:rbed builds it`);
  }
  const markdowns = PAGES.map((page) => [page, markdownOf(page, version)]);
  const broken = javadocLinkErrors(markdowns, apidocs);
  if (broken.length > 0) throw new Error(`Links to no page or member of the Javadoc:\n${broken.join('\n')}`);
  cpSync(apidocs, `${dist}/api`, { recursive: true });
  // the site's look and fonts on the Javadoc (api.css)
  appendFileSync(`${dist}/api/stylesheet.css`, readFileSync(site('api.css')));
  for (const font of ['geist-sans/files/geist-sans-latin-400', 'geist-sans/files/geist-sans-latin-600', 'geist-mono/files/geist-mono-latin-400']) {
    const file = fileURLToPath(site(`node_modules/@fontsource/${font}-normal.woff2`));
    cpSync(file, `${dist}/api/fonts/${file.split('/').pop()}`);
  }
  const bridge = await import('./scenarios/target/js/rbed.js');
  const facts = new Map(
    JSON.parse(bridge.scenarios()).map((s) => [
      s.id,
      {
        source: fillSource(s.source, s.parameters, defaultsOf(s.parameters)).trimEnd(),
        unit: s.timeUnit.name,
        millis: s.timeUnit.millis,
        parameters: s.parameters,
        result: forDisplay(JSON.parse(bridge.run(s.id, '{}', '{}'))),
      },
    ]),
  );
  const write = (path, text) => {
    mkdirSync(dirname(`${dist}/${path}`), { recursive: true });
    writeFileSync(`${dist}/${path}`, text);
  };
  const template = readFileSync(`${dist}/index.html`, 'utf8');
  write('404.html', notFoundHtml(template));
  const twins = markdowns.map(([page, markdown]) => {
    write(`${page.path.slice(1)}index.html`, pageHtml(template, page, markdown, facts));
    const text = twin(markdown, facts, page);
    write(markdownFile(page), text);
    return text;
  });
  write('llms.txt', llmsTxt(PAGES));
  const root = (file) => readFileSync(fileURLToPath(site(`../${file}`)), 'utf8');
  write('llms-full.txt', llmsFull(PAGES, twins, root('README.md'), root('CONTEXT.md')));
  write('sitemap.xml', sitemap(PAGES));
}
