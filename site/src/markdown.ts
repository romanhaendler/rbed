// The Markdown subset the pages are written in (src/content/*.md). The app renders it, and the
// build (prerender.mjs) turns the same source into the prerendered HTML and the Markdown twin, so
// this module imports nothing and runs in Node as well.
//
// Blocks: # to ### headings, paragraphs, - and 1. lists (one level; a list of nothing but links is
// shown as tiles), ``` fenced code (```lang Label), and a Scenario marker on a line of its own:
// <!-- scenario first-rule -->, or <!-- scenario first-rule draw --> to let the reader draw its Channels. Inline: `code`, **strong**, *em*, [text](url). Nothing else, and
// no raw HTML: every character of the text is escaped.

export type Block =
  | { kind: 'html'; html: string }
  | { kind: 'code'; language: string; label: string; source: string }
  | { kind: 'scenario'; id: string; draw: boolean };

export const SCENARIO_MARKER = /^<!-- scenario (\S+)( draw)? -->$/;

const INLINE = /`([^`]+)`|\[((?:[^\]`]|`[^`]*`)+)\]\(([^)\s]+)\)|\*\*(.+?)\*\*|\*([^*\s][^*]*)\*/g;
const ITEM = /^(?:[-*]|\d+\.) +/;

export function escape(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

export function slug(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export function inline(text: string): string {
  let html = '';
  let last = 0;
  for (const m of text.matchAll(INLINE)) {
    html += escape(text.slice(last, m.index));
    if (m[1] !== undefined) html += `<code>${escape(m[1])}</code>`;
    else if (m[2] !== undefined) html += `<a href="${escape(m[3])}">${inline(m[2])}</a>`;
    else if (m[4] !== undefined) html += `<strong>${inline(m[4])}</strong>`;
    else html += `<em>${inline(m[5])}</em>`;
    last = m.index + m[0].length;
  }
  return html + escape(text.slice(last));
}

/** The text of a heading without its inline marks, as its id is made from. */
function plain(text: string): string {
  return text.replace(/[`*]/g, '').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');
}

function list(items: string[], ordered: boolean): string {
  const tiles = !ordered && items.every((i) => /^\[(?:[^\]`]|`[^`]*`)+\]\([^)\s]+\)$/.test(i));
  const html = items.map((i) => `<li>${tiles ? inline(i).replace('<a ', '<a class="tile" ') : inline(i)}</li>`).join('');
  return ordered ? `<ol>${html}</ol>` : `<ul${tiles ? ' class="tiles"' : ''}>${html}</ul>`;
}

export function parse(markdown: string): Block[] {
  const blocks: Block[] = [];
  const html: string[] = [];
  const flush = () => {
    if (html.length > 0) blocks.push({ kind: 'html', html: html.join('\n') });
    html.length = 0;
  };
  const lines = markdown.split('\n');
  for (let i = 0; i < lines.length; ) {
    const line = lines[i].trimEnd();
    let m: RegExpExecArray | null;
    if (line === '') {
      i++;
    } else if (line.startsWith('```')) {
      const [language, ...label] = line.slice(3).trim().split(/\s+/);
      const end = lines.indexOf('```', i + 1);
      const stop = end < 0 ? lines.length : end;
      flush();
      blocks.push({ kind: 'code', language, label: label.join(' ') || language, source: lines.slice(i + 1, stop).join('\n') });
      i = stop + 1;
    } else if ((m = SCENARIO_MARKER.exec(line))) {
      flush();
      blocks.push({ kind: 'scenario', id: m[1], draw: m[2] !== undefined });
      i++;
    } else if ((m = /^(#{1,3}) +(.*)$/.exec(line))) {
      const level = m[1].length;
      html.push(`<h${level} id="${slug(plain(m[2]))}"${level === 1 ? ' tabindex="-1"' : ''}>${inline(m[2])}</h${level}>`);
      i++;
    } else if (ITEM.test(line)) {
      const ordered = /^\d/.test(line);
      const items: string[] = [];
      for (; i < lines.length && lines[i].trim() !== ''; i++) {
        if (ITEM.test(lines[i])) items.push(lines[i].replace(ITEM, '').trim());
        else items[items.length - 1] += ' ' + lines[i].trim();
      }
      html.push(list(items, ordered));
    } else {
      const text = [line.trim()];
      for (i++; i < lines.length && lines[i].trim() !== '' && !/^(#|```|<!--)/.test(lines[i]) && !ITEM.test(lines[i]); i++) {
        text.push(lines[i].trim());
      }
      html.push(`<p>${inline(text.join(' '))}</p>`);
    }
  }
  flush();
  return blocks;
}
