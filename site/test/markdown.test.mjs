// The Markdown subset the pages are written in, as the app and the build both read it.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parse } from '../src/markdown.ts';

test('headings, paragraphs and lists become HTML, with ids on headings', () => {
  assert.deepEqual(parse('# A page\n\nOne line\nand the next.\n\n## Two words\n\n- one\n- two\n\n1. first\n2. second\n'), [
    {
      kind: 'html',
      html:
        '<h1 id="a-page" tabindex="-1">A page</h1>\n<p>One line and the next.</p>\n<h2 id="two-words">Two words</h2>\n' +
        '<ul><li>one</li><li>two</li></ul>\n<ol><li>first</li><li>second</li></ol>',
    },
  ]);
});

test('inline code, emphasis and links; text is escaped', () => {
  assert.deepEqual(parse('**Bold** *em* `a < b && c*d*` [the `x` link](https://e.org/?a=1&b=2) 1 < 2'), [
    {
      kind: 'html',
      html:
        '<p><strong>Bold</strong> <em>em</em> <code>a &lt; b &amp;&amp; c*d*</code> ' +
        '<a href="https://e.org/?a=1&amp;b=2">the <code>x</code> link</a> 1 &lt; 2</p>',
    },
  ]);
});

test('a fenced code block keeps its text and names its language and label', () => {
  assert.deepEqual(parse('Intro\n\n```xml Maven\n<a>\n  b\n</a>\n```\n\nAfter'), [
    { kind: 'html', html: '<p>Intro</p>' },
    { kind: 'code', language: 'xml', label: 'Maven', source: '<a>\n  b\n</a>' },
    { kind: 'html', html: '<p>After</p>' },
  ]);
  assert.deepEqual(parse('```java\nx();\n```'), [{ kind: 'code', language: 'java', label: 'java', source: 'x();' }]);
});

test('a Scenario marker stands on its own line and splits the text', () => {
  assert.deepEqual(parse('Before\n\n<!-- scenario first-rule -->\n\nAfter'), [
    { kind: 'html', html: '<p>Before</p>' },
    { kind: 'scenario', id: 'first-rule' },
    { kind: 'html', html: '<p>After</p>' },
  ]);
});

test('a list of nothing but links is a list of tiles', () => {
  assert.deepEqual(parse('- [**A** b](https://a/)\n- [C](https://c/)'), [
    {
      kind: 'html',
      html: '<ul class="tiles"><li><a class="tile" href="https://a/"><strong>A</strong> b</a></li><li><a class="tile" href="https://c/">C</a></li></ul>',
    },
  ]);
});
