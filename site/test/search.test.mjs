// What the search palette can find a page by.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { searchItems } from '../src/search.ts';

const pages = [
  { path: '/', title: 'rbed', description: 'Front.' },
  { path: '/moving-averages/', title: 'Moving averages', description: 'Smoothing.', rubric: 'Guide' },
];
const markdown = {
  '/': '# rbed\n\nNothing to see.',
  '/moving-averages/':
    '# Moving averages\n\nUse `forDuration` and\n`level.rate()` here.\n\n## Warm-up\n\n<!-- scenario smoothing -->\n\n```java\nDetector.builder().maxGap(Duration.ofSeconds(5));\n```',
};
const sources = { smoothing: 'Rule.named("High").when(level.averageOverDuration(Duration.ofSeconds(30)).above(3))' };

const items = searchItems(pages, (p) => markdown[p.path], (id) => sources[id]);

test('every page is an item, under its rubric, and each of its sections one under the page', () => {
  assert.deepEqual(
    items.map(({ id, label, group }) => ({ id, label, group })),
    [
      { id: '/', label: 'rbed', group: 'Overview' },
      { id: '/moving-averages/', label: 'Moving averages', group: 'Guide' },
      { id: '/moving-averages/#warm-up', label: 'Warm-up', group: 'Moving averages' },
    ],
  );
});

test('a page is found by any word of its description and its text', () => {
  const found = (word) => items[1].keywords.some((k) => k.toLowerCase().includes(word));
  assert.ok(found('smoothing'));
  assert.ok(found('here'));
});

test('a page is found by the methods its Java source calls, its inline code and its headings', () => {
  const keywords = items[1].keywords;
  for (const word of ['averageOverDuration', 'named', 'maxGap', 'forDuration', 'rate', 'Warm-up']) {
    assert.ok(keywords.includes(word), `${word} in ${keywords}`);
  }
  assert.equal(new Set(keywords).size, keywords.length, 'no duplicates');
});
