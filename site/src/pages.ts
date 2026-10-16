// Every page of the site, in the README's order. The web app builds its navigation, routes and
// search from this list, and the build (prerender.mjs) reads it in Node, so it imports nothing.
//
// A page's text is the Markdown file src/content/<markdownFile(page)>; see site/README.md, "Adding
// a page". A page that is not in this list is not linked, built or searched anywhere.

export type Page = {
  /** Address below the site's base, starting and ending with a slash. */
  path: string;
  /** The name in the navigation, the search and the document title. */
  title: string;
  /** One sentence, for the search engines, the sharing card and llms.txt. */
  description: string;
  /** The navigation's heading the page stands under; the front page has none. */
  rubric?: 'Guide' | 'Examples';
};

export const PAGES: Page[] = [
  {
    path: '/',
    title: 'rbed',
    description:
      'Rule-based event detection in time series: a small Java library that finds Events in multi-channel data by evaluating declarative Rules.',
  },
  {
    path: '/event-lifecycle/',
    title: 'The Event lifecycle',
    description:
      'How an Event goes Pending, is Confirmed or Discarded, and Ends, and what forDuration has to do with it.',
    rubric: 'Guide',
  },
  {
    path: '/aggregates/',
    title: 'Aggregates',
    description:
      "Every Event carries each Channel's minimum, maximum, mean, count, time-weighted mean and integral over its Samples.",
    rubric: 'Guide',
  },
  {
    path: '/pulse-channels/',
    title: 'Pulse Channels',
    description:
      'Pulse Channels report one-off occurrences such as deployments: their value exists only in the Sample that carries it.',
    rubric: 'Guide',
  },
];

/** The page path an address names when the site is served below `base`, or undefined outside it. */
export function pathOf(pathname: string, base: string): string | undefined {
  if (!(pathname + '/').startsWith(base)) return undefined;
  return ('/' + pathname.slice(base.length)).replace(/\/?$/, '/');
}

export function pageAt(pathname: string, base: string): Page | undefined {
  const path = pathOf(pathname, base);
  return PAGES.find((p) => p.path === path);
}

/** The page's Markdown below src/content/, which is also its twin's address below the base. */
export function markdownFile(page: Pick<Page, 'path'>): string {
  return page.path === '/' ? 'index.md' : page.path.slice(1, -1) + '.md';
}

export function titleOf(page: Pick<Page, 'path' | 'title'>): string {
  return page.path === '/' ? 'rbed: rule-based event detection' : `${page.title} · rbed`;
}
