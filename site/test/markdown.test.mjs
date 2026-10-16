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

test('a link with a scheme other than http(s) or mailto is its text only', () => {
  assert.deepEqual(parse('[a](javascript:x) [b](../b/) [c](#c) [d](mailto:x@y.z) [e](data:text/html,x)'), [
    { kind: 'html', html: '<p>a <a href="../b/">b</a> <a href="#c">c</a> <a href="mailto:x@y.z">d</a> e</p>' },
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

test('a link is only a link for http(s), mailto, an anchor or a relative address', () => {
  const html = (text) => parse(text)[0].html;
  assert.equal(
    html('[a](https://e.org/) [b](mailto:x@e.org) [c](#c) [d](../cranes/) [e](api/X.html)'),
    '<p><a href="https://e.org/">a</a> <a href="mailto:x@e.org">b</a> <a href="#c">c</a> ' +
      '<a href="../cranes/">d</a> <a href="api/X.html">e</a></p>',
  );
  assert.equal(html('[x](javascript:void0) [y](JavaScript:a) [z](data:text/html,a)'), '<p>x y z</p>');
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
    { kind: 'scenario', id: 'first-rule', draw: false },
    { kind: 'html', html: '<p>After</p>' },
  ]);
});

test('a Scenario marker ending in draw lets the reader draw its Channels', () => {
  assert.deepEqual(parse('<!-- scenario first-rule draw -->'), [{ kind: 'scenario', id: 'first-rule', draw: true }]);
});

test('a paragraph or list after a live marker is only for the live page', () => {
  assert.deepEqual(parse('Before\n\n<!-- live -->\nMove the slider\nnow.\n\nAfter\n\n<!-- live -->\n- drag\n\nEnd'), [
    { kind: 'html', html: '<p>Before</p>' },
    { kind: 'html', html: '<p>Move the slider now.</p>', live: true },
    { kind: 'html', html: '<p>After</p>' },
    { kind: 'html', html: '<ul><li>drag</li></ul>', live: true },
    { kind: 'html', html: '<p>End</p>' },
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
