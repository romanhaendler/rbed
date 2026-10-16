// Every page of the site, in the README's order. The web app builds its navigation and routes
// from this list, and build steps read it in Node, so it imports nothing.
//
// To add a page: insert it here at its README position, with rubric "Guide" for a README chapter
// or "Examples" for a domain example, and give its path a view in Shell.tsx. A page that is not
// in this list is not linked anywhere.

export type Page = {
  /** Address below the site's base, starting and ending with a slash. */
  path: string;
  title: string;
  /** One sentence, for the page head and the search engines. */
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
