// The files the build writes for readers without JavaScript, coding agents and search engines,
// for any list of pages: a page added to the manifest gets all of them.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { llmsFull, llmsTxt, pageHtml, sitemap, twin } from '../prerender.mjs';

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
        events: [{ name: 'Run', start: 2000, confirmed: 2000, end: 7500, truncatedStart: false, truncatedEnd: true }],
        discarded: [],
      },
    },
  ],
]);
const template =
  '<!doctype html>\n<html lang="en">\n  <head>\n    <title>rbed</title>\n  </head>\n  <body>\n    <div id="root"></div>\n  </body>\n</html>\n';

test('the twin is the Markdown with each Scenario as its Java source and its default Events', () => {
  assert.equal(
    twin(markdown, facts),
    '# Data gaps\n\nA gap <ends> an Event.\n\n' +
      '```java\nRule.named("Run").when(speed.above(5)).maxGap(Duration.ofSeconds(10));\n```\n\n' +
      'Events at the default parameters:\n\n' +
      '| Name | Start (s) | Confirmed (s) | End (s) | Truncated |\n| --- | --- | --- | --- | --- |\n' +
      '| Run | 2 | 2 | 7.5 | end |\n\n' +
      'That is all.\n',
  );
});

test('a Scenario that rbed rejects at its defaults, or that has no Event, says so', () => {
  const error = new Map([['gaps', { ...facts.get('gaps'), result: { error: 'Limit is NaN' } }]]);
  assert.match(twin(markdown, error), /rbed rejects the default parameters: Limit is NaN/);
  const none = new Map([['gaps', { ...facts.get('gaps'), result: { samples: [], events: [], discarded: [] } }]]);
  assert.match(twin(markdown, none), /No Event at the default parameters\./);
  assert.throws(() => twin('<!-- scenario nope -->', facts), /nope/);
});

test('Discarded Pending Events at the defaults get a table of their own, after the Events', () => {
  const result = { ...facts.get('gaps').result, discarded: [{ name: 'Run', start: 9000, discarded: 9500 }] };
  const discarded = new Map([['gaps', { ...facts.get('gaps'), result }]]);
  assert.ok(
    twin(markdown, discarded).includes(
      '| Run | 2 | 2 | 7.5 | end |\n\n' +
        'Discarded Pending Events at the default parameters:\n\n' +
        '| Name | Start (s) | Discarded (s) |\n| --- | --- | --- |\n| Run | 9 | 9.5 |\n\nThat is all.',
    ),
  );
  const html = pageHtml(template, pages[1], markdown, discarded);
  assert.ok(html.includes('<caption>Discarded Pending Events at the default parameters</caption>'));
  assert.ok(html.includes('<td>Run</td><td>9</td><td>9.5</td>'));
  assert.ok(!twin(markdown, facts).includes('Discarded'), 'no table while there is none');
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
    '<td>Run</td><td>2</td><td>2</td><td>7.5</td><td>end</td>',
    '.js .prerendered{visibility:hidden}',
  ]) {
    assert.ok(html.includes(part), part);
  }
  assert.throws(() => pageHtml('<html></html>', pages[1], markdown, facts), /template/);
});

test('llms.txt lists every twin by rubric, the reference material and llms-full.txt', () => {
  const text = llmsTxt(pages);
  assert.ok(text.startsWith('# rbed\n\n> Rule-based event detection.\n'));
  for (const line of [
    '## Overview\n\n- [rbed](https://romanhaendler.github.io/rbed/index.md): Rule-based event detection.',
    '## Guide\n\n- [Data gaps](https://romanhaendler.github.io/rbed/data-gaps.md): What maxGap does.',
    '- [README](https://github.com/romanhaendler/rbed/blob/main/README.md)',
    '- [Glossary](https://github.com/romanhaendler/rbed/blob/main/CONTEXT.md)',
    '- [Javadoc](https://javadoc.io/doc/io.github.romanhaendler/rbed)',
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
});

test('the sitemap lists every page', () => {
  assert.equal(
    sitemap(pages, '2026-10-16'),
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
      '  <url><loc>https://romanhaendler.github.io/rbed/</loc><lastmod>2026-10-16</lastmod></url>\n' +
      '  <url><loc>https://romanhaendler.github.io/rbed/data-gaps/</loc><lastmod>2026-10-16</lastmod></url>\n' +
      '</urlset>\n',
  );
});
