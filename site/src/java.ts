// Light syntax highlighting for the Java the site shows: comments, strings, annotations,
// numbers, keywords and capitalised names (types). Imports nothing, so Node can test it.

type JavaKind = '' | 'comment' | 'string' | 'annotation' | 'number' | 'keyword' | 'type';

const KEYWORDS =
  'abstract|assert|boolean|break|byte|case|catch|char|class|continue|default|do|double|else|enum|extends|false|final|finally|float|for|if|implements|import|instanceof|int|interface|long|new|null|package|private|protected|public|record|return|short|static|super|switch|this|throw|throws|true|try|var|void|while';

// One group per kind, in the order of JavaKind after ''.
const TOKEN = new RegExp(
  [
    String.raw`(\/\/[^\n]*|\/\*[\s\S]*?\*\/)`,
    String.raw`("""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')`,
    String.raw`(@\w+)`,
    String.raw`\b(\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[lLdDfF]?)\b`,
    String.raw`\b(${KEYWORDS})\b`,
    String.raw`\b([A-Z]\w*)\b`,
  ].join('|'),
  'g',
);
const KINDS: JavaKind[] = ['comment', 'string', 'annotation', 'number', 'keyword', 'type'];

/** The source as consecutive pieces, each with its kind ('' for plain text). */
export function javaTokens(source: string): [JavaKind, string][] {
  const pieces: [JavaKind, string][] = [];
  let at = 0;
  for (const match of source.matchAll(TOKEN)) {
    if (match.index > at) pieces.push(['', source.slice(at, match.index)]);
    pieces.push([KINDS[match.slice(1).findIndex((group) => group !== undefined)], match[0]]);
    at = match.index + match[0].length;
  }
  if (at < source.length) pieces.push(['', source.slice(at)]);
  return pieces;
}
