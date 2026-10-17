// Java source split into the pieces the code block colors.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { javaTokens } from '../src/java.ts';

const marked = (source) => javaTokens(source).filter(([kind]) => kind !== '');

test('the pieces put together are the source', () => {
  const source = 'Rule r = Rule.named("A").when(temp.above(80)); // x\n';
  assert.equal(javaTokens(source).map(([, text]) => text).join(''), source);
});

test('keywords, strings, numbers, comments and annotations are marked', () => {
  assert.deepEqual(
    marked('@Override public void on(Event e) { new X("a // b", 1_000.5); } // done'),
    [
      ['annotation', '@Override'],
      ['keyword', 'public'],
      ['keyword', 'void'],
      ['keyword', 'new'],
      ['string', '"a // b"'],
      ['number', '1_000.5'],
      ['comment', '// done'],
    ],
  );
});

test('a number inside a name is no number', () => {
  assert.deepEqual(marked('int x2 = 3;'), [
    ['keyword', 'int'],
    ['number', '3'],
  ]);
});
