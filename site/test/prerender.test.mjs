// The files the build writes for readers without JavaScript, coding agents and search engines,
// for any list of pages: a page added to the manifest gets all of them.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { javadocLinkErrors, llmsFull, llmsTxt, notFoundHtml, pageHtml, sitemap, twin } from '../prerender.mjs';

const pages = [
  { path: '/', title: 'rbed', description: 'Rule-based event detection.' },
  { path: '/data-gaps/', title: 'Data gaps', description: 'What maxGap does.', rubric: 'Guide' },
];
const markdown = '# Data gaps\n\nA gap <ends> an Event.\n\n<!-- scenario gaps -->\n\nThat is all.\n';
const facts = new Map([
  [
    'gaps',
    {
      source: 'Rule.named("Run").when(speed.above(5)).maxGap(Duration.ofSeconds(10));',
      unit: 's',
      millis: 1000,
      result: {
        samples: [],
        events: [
          {
            name: 'Run',
            start: 2000,
            confirmed: 2000,
            end: 7500,
            truncatedStart: false,
            truncatedEnd: true,
            aggregates: {
              speed: { min: 5.5, max: 9, mean: 7.123456, count: 3, timeWeightedMean: 7, integral: 38.5 },
              load: { min: null, max: null, mean: null, count: 0, timeWeightedMean: null, integral: null },
            },
          },
        ],
        discarded: [],
      },
    },
  ],
]);
const template =
  '<!doctype html>\n<html lang="en">\n  <head>\n    <title>rbed</title>\n  </head>\n  <body>\n    <div id="root"></div>\n  </body>\n</html>\n';

test('the twin is the Markdown with each Scenario as its Java source and its default Events', () => {
  assert.equal(
    twin(markdown, facts, pages[1]),
    '# Data gaps\n\nA gap <ends> an Event.\n\n' +
      '```java\nRule.named("Run").when(speed.above(5)).maxGap(Duration.ofSeconds(10));\n```\n\n' +
      'Events at the default parameters:\n\n' +
      '| Name | Start (s) | Confirmed (s) | End (s) | Truncated | Aggregates |\n| --- | --- | --- | --- | --- | --- |\n' +
      '| Run | 2 | 2 | 7.5 | end | speed: min 5.5, max 9, mean 7.12; load: no value |\n\n' +
      'That is all.\n',
  );
});

test('a Scenario that rbed rejects at its defaults, or that has no Event, says so', () => {
  const error = new Map([['gaps', { ...facts.get('gaps'), result: { error: 'Limit is NaN', by: 'rbed' } }]]);
  assert.match(twin(markdown, error, pages[1]), /rbed rejects the default parameters: Limit is NaN/);
  const site = new Map([['gaps', { ...facts.get('gaps'), result: { error: 'Invalid JSON' } }]]);
  assert.match(twin(markdown, site, pages[1]), /The site cannot run the default parameters: Invalid JSON/);
  const none = new Map([['gaps', { ...facts.get('gaps'), result: { samples: [], events: [], discarded: [] } }]]);
  assert.match(twin(markdown, none, pages[1]), /No Event at the default parameters\./);
  assert.throws(() => twin('<!-- scenario nope -->', facts, pages[1]), /nope/);
});

test('Discarded Pending Events at the defaults get a table of their own, after the Events', () => {
  const result = { ...facts.get('gaps').result, discarded: [{ name: 'Run', start: 9000, discarded: 9500 }] };
  const discarded = new Map([['gaps', { ...facts.get('gaps'), result }]]);
  assert.ok(
    twin(markdown, discarded, pages[1]).includes(
      '| Run | 2 | 2 | 7.5 | end | speed: min 5.5, max 9, mean 7.12; load: no value |\n\n' +
        'Discarded Pending Events at the default parameters:\n\n' +
        '| Name | Start (s) | Discarded (s) |\n| --- | --- | --- |\n| Run | 9 | 9.5 |\n\nThat is all.',
    ),
  );
  const html = pageHtml(template, pages[1], markdown, discarded);
  assert.ok(html.includes('<caption>Discarded Pending Events at the default parameters</caption>'));
  assert.ok(html.includes('<td>Run</td><td>9</td><td>9.5</td>'));
  assert.ok(!twin(markdown, facts, pages[1]).includes('Discarded'), 'no table while there is none');
});

test('the table has a Note column when the Scenario notes an Event, such as the load a crane lifted', () => {
  const noted = new Map([
    [
      'gaps',
      {
        ...facts.get('gaps'),
        result: {
          samples: [],
          events: [
            { name: 'Run', start: 2000, confirmed: 2000, end: 7500, truncatedStart: false, truncatedEnd: false, aggregates: {}, note: 'pallet' },
            { name: 'Stop', start: 8000, confirmed: 8000, end: 9000, truncatedStart: false, truncatedEnd: false, aggregates: {} },
          ],
          discarded: [],
        },
      },
    ],
  ]);
  const text = twin(markdown, noted, pages[1]);
  assert.ok(text.includes('| Name | Start (s) | Confirmed (s) | End (s) | Truncated | Note | Aggregates |\n'), text);
  const none = 'none (the Rule reads no Channel)';
  assert.ok(text.includes(`| Run | 2 | 2 | 7.5 |  | pallet | ${none} |\n| Stop | 8 | 8 | 9 |  |  | ${none} |`), text);
});

test("the twin's links to other pages are absolute, since it is read away from the page", () => {
  const links =
    'See [Pulse Channels](../pulse-channels/), [Cranes](cranes/), [below](#below), ' +
    '[README](https://github.com/romanhaendler/rbed#pulse-channels) and `[not](a link)`.\n';
  assert.equal(
    twin(links, facts, pages[1]),
    'See [Pulse Channels](https://romanhaendler.github.io/rbed/pulse-channels/), ' +
      '[Cranes](https://romanhaendler.github.io/rbed/data-gaps/cranes/), ' +
      '[below](https://romanhaendler.github.io/rbed/data-gaps/#below), ' +
      '[README](https://github.com/romanhaendler/rbed#pulse-channels) and `[not](a link)`.\n',
  );
  assert.equal(twin('[Cranes](cranes/)', facts, pages[0]), '[Cranes](https://romanhaendler.github.io/rbed/cranes/)');
});

test("a page's HTML holds its head, its text, its Java source and its default Events before any script", () => {
  const html = pageHtml(template, pages[1], markdown, facts);
  for (const part of [
    '<title>Data gaps · rbed</title>',
    '<meta name="description" content="What maxGap does." />',
    '<link rel="canonical" href="https://romanhaendler.github.io/rbed/data-gaps/" />',
    '<link rel="alternate" type="text/markdown" href="https://romanhaendler.github.io/rbed/data-gaps.md" />',
    '<meta property="og:image" content="https://romanhaendler.github.io/rbed/og-image.png" />',
    '<div id="root"><div class="prerendered">',
    '<p>A gap &lt;ends&gt; an Event.</p>',
    'Rule.named(&quot;Run&quot;).when(speed.above(5))',
    '<td>Run</td><td>2</td><td>2</td><td>7.5</td><td>end</td><td>speed: min 5.5, max 9, mean 7.12; load: no value</td>',
    '.js .prerendered{visibility:hidden}',
  ]) {
    assert.ok(html.includes(part), part);
  }
  assert.throws(() => pageHtml('<html></html>', pages[1], markdown, facts), /template/);
});

test('the twin and the prerendered page leave out what speaks of the live controls', () => {
  const live = 'Intro.\n\n<!-- live -->\nDrag the line.\n\nOutro.\n';
  assert.equal(twin(live, facts, pages[1]), 'Intro.\n\nOutro.\n');
  const html = pageHtml(template, pages[1], live, facts, pages);
  assert.ok(html.includes('<p>Intro.</p>') && html.includes('<p>Outro.</p>'));
  assert.ok(!html.includes('Drag'));
});

test("the twin lists a Scenario's parameters under its source, as the address names them", () => {
  const parameters = [
    { id: 'maxGap', label: 'Gap over', default: 10, min: 1, max: 60, step: 1, unit: 's' },
    { id: 'push', label: 'Push', default: 1, min: 0, max: 1, step: 1, unit: '', options: [{ label: 'Each Sample' }, { label: 'Values only' }] },
  ];
  const withParameters = new Map([['gaps', { ...facts.get('gaps'), parameters }]]);
  assert.ok(
    twin(markdown, withParameters, pages[1]).includes(
      '```\n\nParameters (in the page address as ?maxGap=…):\n\n' +
        '- Gap over (`maxGap`): 1 to 60 s in steps of 1, default 10\n' +
        '- Push (`push`): 0 for Each Sample, 1 for Values only (default)\n\nEvents at the default parameters:',
    ),
  );
});

test("a table cell's | does not end the cell in the twin", () => {
  const piped = new Map([['gaps', { ...facts.get('gaps'), result: { ...facts.get('gaps').result, events: [{ ...facts.get('gaps').result.events[0], name: 'A|B' }] } }]]);
  assert.ok(twin(markdown, piped, pages[1]).includes('| A\\|B | 2 |'));
});

test('without JavaScript, a page has the header and the list of pages, and says what the live page adds', () => {
  const html = pageHtml(template, pages[1], markdown, facts, pages);
  for (const part of [
    '<header class="shellHead">',
    '<a class="shellMark" href="/rbed/">rbed</a>',
    '<a class="railEntry" href="/rbed/">Overview</a>',
    '<a class="railEntry" href="/rbed/data-gaps/" aria-current="page">Data gaps</a>',
    '<p>The live chart and its controls need JavaScript.',
  ]) {
    assert.ok(html.includes(part), part);
  }
  assert.ok(!html.includes("classList.add('js')"), 'the app marks the page when it runs, not an inline script');
});

test('404.html says there is no page and lists the pages', () => {
  const html = notFoundHtml(template, pages);
  assert.ok(html.includes('<title>No page · rbed</title><meta name="robots" content="noindex" />'));
  assert.ok(html.includes('No page at this address'));
  assert.ok(html.includes('<a class="railEntry" href="/rbed/data-gaps/">Data gaps</a>'));
});

test('a link into the Javadoc must name a type and member the generated Javadoc has', () => {
  const apidocs = mkdtempSync(`${tmpdir()}/apidocs-`);
  mkdirSync(`${apidocs}/m/p`, { recursive: true });
  writeFileSync(`${apidocs}/m/p/Rule.html`, '<section id="when(m.Condition)"></section><a id="named(java.lang.String)"></a>');
  const links =
    '[a](../api/m/p/Rule.html#when%28m.Condition%29) [b](../api/m/p/Rule.html) [c](../api/m/p/Nope.html) ' +
    '[d](../api/m/p/Rule.html#nope%28%29) [e](../cranes/) `[f](../api/x.html)`';
  assert.deepEqual(javadocLinkErrors([[pages[1], links]], apidocs), [
    '/data-gaps/ links ../api/m/p/Nope.html',
    '/data-gaps/ links ../api/m/p/Rule.html#nope%28%29',
  ]);
  assert.deepEqual(javadocLinkErrors([[pages[0], '[x](api/m/p/Rule.html#named%28java.lang.String%29)']], apidocs), []);
});

test('llms.txt lists every twin by rubric, the reference material and llms-full.txt', () => {
  const text = llmsTxt(pages);
  assert.ok(text.startsWith('# rbed\n\n> Rule-based event detection.\n'));
  for (const line of [
    '## Overview\n\n- [rbed](https://romanhaendler.github.io/rbed/index.md): Rule-based event detection.',
    '## Guide\n\n- [Data gaps](https://romanhaendler.github.io/rbed/data-gaps.md): What maxGap does.',
    '- [README](https://github.com/romanhaendler/rbed/blob/main/README.md)',
    '- [Glossary](https://github.com/romanhaendler/rbed/blob/main/CONTEXT.md)',
    '- [Javadoc](https://romanhaendler.github.io/rbed/api/)',
    '[llms-full.txt](https://romanhaendler.github.io/rbed/llms-full.txt)',
  ]) {
    assert.ok(text.includes(line), line);
  }
  assert.ok(!text.includes('## Examples'), 'a rubric without pages has no section');
});

test('llms-full.txt is the README with absolute links, the glossary and every twin in order', () => {
  const readme = '# rbed\n\nSee [the glossary](CONTEXT.md), [Install](#install) and [Central](https://central.sonatype.com/).';
  const text = llmsFull(pages, ['# rbed twin', '# Data gaps twin'], readme, '# rbed\n\n**Sample**: values.');
  assert.ok(
    text.includes(
      'See [the glossary](https://github.com/romanhaendler/rbed/blob/main/CONTEXT.md), ' +
        '[Install](https://github.com/romanhaendler/rbed#install) and [Central](https://central.sonatype.com/).',
    ),
  );
  const order = ['See [the glossary]', '**Sample**: values.', '# rbed twin', '# Data gaps twin'].map((s) => text.indexOf(s));
  assert.ok(order.every((at, i) => at >= 0 && (i === 0 || at > order[i - 1])), `order ${order}`);
  assert.ok(text.includes('Source: https://github.com/romanhaendler/rbed/blob/main/README.md\n\n# rbed\n'));
  assert.ok(
    text.includes('Source: https://github.com/romanhaendler/rbed/blob/main/CONTEXT.md\n\n# rbed glossary (CONTEXT.md)\n\n**Sample**'),
    'the glossary is not a second "# rbed"',
  );
});

test('the sitemap lists every page, without a date that would change on every build', () => {
  assert.equal(
    sitemap(pages),
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url><loc>https://romanhaendler.github.io/rbed/</loc></url>\n' +
      '  <url><loc>https://romanhaendler.github.io/rbed/data-gaps/</loc></url>\n' +
      '</urlset>\n',
  );
});
