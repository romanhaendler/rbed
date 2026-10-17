// The shell after the umriss-ui demo's (MIT): header bar, sidebar from the page list, content
// column, and the routing. Every page is a path of its own below Vite's base; a link to one of
// them moves without a reload, anything else is the browser's.
import { CommandPalette, useCommandPaletteShortcut } from '@umriss-ui/core';
import { useEffect, useId, useRef, useState } from 'react';
import { CATALOGUE, MarkdownPage, markdownOf } from './MarkdownPage';
import { markdownFile, PAGES, pageAt, REPOSITORY, titleOf } from './pages.ts';
import { searchItems } from './search.ts';

const BASE = import.meta.env.BASE_URL;
const SHORTCUT = /Mac|iP/.test(navigator.platform) ? '⌘K' : 'Ctrl K';

// Pages and their sections; with nothing typed, the pages.
const SEARCH = searchItems(PAGES, markdownOf, (id) => CATALOGUE.find((s) => s.id === id)?.source ?? '');
const SEARCH_PAGES = SEARCH.filter((item) => !item.id.includes('#'));

const href = (path: string) => BASE + path.slice(1);

/** Moves to an address of the site without a reload. */
function go(address: string) {
  window.history.pushState(null, '', address);
  window.dispatchEvent(new PopStateEvent('popstate'));
  document.documentElement.scrollTop = 0;
}

/** Scrolls to the element the address' fragment names, once the page has been drawn: the
    browser scrolled to it in the prerendered page, which the app has since replaced. */
function scrollToFragment() {
  const id = decodeURIComponent(window.location.hash.slice(1));
  if (id === '') return;
  requestAnimationFrame(() => document.getElementById(id)?.scrollIntoView());
}

function useCurrentPage() {
  const [address, setAddress] = useState(() => window.location.pathname + window.location.hash);
  useEffect(() => {
    const follow = () => setAddress(window.location.pathname + window.location.hash);
    const onClick = (event: MouseEvent) => {
      const link = (event.target as Element | null)?.closest?.('a');
      if (!link || link.target !== '' || link.hasAttribute('download')) return;
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin || pageAt(url.pathname, BASE) === undefined) return;
      if (url.pathname === window.location.pathname && url.hash !== '') return;
      event.preventDefault();
      go(url.pathname + url.search + url.hash);
    };
    window.addEventListener('popstate', follow);
    document.addEventListener('click', onClick);
    return () => {
      window.removeEventListener('popstate', follow);
      document.removeEventListener('click', onClick);
    };
  }, []);
  // A new address scrolls to its fragment, if it has one, after the page is drawn.
  useEffect(scrollToFragment, [address]);
  return pageAt(window.location.pathname, BASE);
}

function Icon({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 10 10" width="16" height="16" aria-hidden="true">
      <path d={d} fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Rail({ current }: { current?: string }) {
  const nav = useRef<HTMLElement>(null);
  const id = useId();
  // The current page's entry in view, where the rail scrolls; the page itself stays where it is.
  useEffect(() => {
    const rail = nav.current!;
    const entry = rail.querySelector('[aria-current]');
    if (!entry) return;
    const top = entry.getBoundingClientRect().top - rail.getBoundingClientRect().top + rail.scrollTop;
    if (top < rail.scrollTop || top + entry.clientHeight > rail.scrollTop + rail.clientHeight) {
      rail.scrollTop = top - rail.clientHeight / 2;
    }
  }, [current]);
  const entry = (path: string, title: string) => (
    <a className="railEntry" href={href(path)} aria-current={current === path ? 'page' : undefined}>
      {title}
    </a>
  );
  return (
    <nav className="rail" aria-label="Pages" ref={nav}>
      {entry('/', 'Overview')}
      {(['Guide', 'Examples'] as const).map((rubric) => {
        const pages = PAGES.filter((p) => p.rubric === rubric);
        return (
          pages.length > 0 && (
            <div className="railRubric" key={rubric}>
              {/* Not a heading: the page's h1 is the first heading a screen reader meets. */}
              <p className="railHead" id={`${id}-${rubric}`}>
                <span>{rubric}</span>
                <span className="railCount">{pages.length}</span>
              </p>
              <ul className="railList" aria-labelledby={`${id}-${rubric}`}>
                {pages.map((p) => (
                  <li key={p.path}>{entry(p.path, p.title)}</li>
                ))}
              </ul>
            </div>
          )
        );
      })}
    </nav>
  );
}

function NotFound({ search }: { search: () => void }) {
  return (
    <article className="page">
      <div className="flow">
        <h1 tabIndex={-1}>No page at this address</h1>
        <p>
          The page may have moved. Search for it, or start again from the <a href={BASE}>front page</a>.
        </p>
      </div>
      <p>
        <button type="button" className="pageAction" onClick={search}>
          Search the documentation
        </button>
      </p>
    </article>
  );
}

/** The words of an address the site has no page at, to search for: "/rbed/data-gap/" gives "data gap". */
const wordsOf = (pathname: string) => decodeURIComponent(pathname.slice(BASE.length)).replace(/[^A-Za-z0-9]+/g, ' ').trim();

export function Shell() {
  const page = useCurrentPage();
  const drawer = useRef<HTMLDialogElement>(null);
  // Each opening is a new palette (its key), so it starts with an empty query.
  const [searches, setSearches] = useState(0);
  const [searching, setSearching] = useState(false);
  const [resting, setResting] = useState(SEARCH_PAGES);
  const search = (rest = SEARCH_PAGES) => {
    setResting(rest);
    setSearches((n) => n + 1);
    setSearching(true);
  };
  useCommandPaletteShortcut(() => search());

  useEffect(() => {
    // The same head the build writes into each page's index.html (prerender.mjs).
    document.title = page === undefined ? 'No page · rbed' : titleOf(page);
    if (page !== undefined) {
      const address = (path: string) => new URL(BASE + path, window.location.href).href;
      document.querySelector('link[rel=canonical]')?.setAttribute('href', address(page.path.slice(1)));
      document.querySelector('link[rel=alternate][type="text/markdown"]')?.setAttribute('href', address(markdownFile(page)));
    }
    // A page chosen in the drawer closes it and takes the focus, so a screen reader announces it.
    if (drawer.current?.open) {
      drawer.current.close();
      document.querySelector<HTMLElement>('.shellContent h1')?.focus({ preventScroll: true });
    }
  }, [page]);

  // On an address with no page, the search starts with the pages its words find.
  const searchFromAddress = () => {
    const words = wordsOf(window.location.pathname).toLowerCase().split(' ').filter((w) => w.length >= 3);
    const finds = (texts: (item: (typeof SEARCH_PAGES)[number]) => string[]) =>
      SEARCH_PAGES.filter((item) => words.some((w) => texts(item).some((k) => k.toLowerCase().includes(w))));
    const byTitle = finds((item) => [item.label]);
    const byText = finds((item) => item.keywords);
    search(byTitle.length > 0 ? byTitle : byText.length > 0 ? byText : SEARCH_PAGES);
  };

  return (
    <>
      <header className="shellHead">
        <a className="skipLink" href="#main">
          Skip to content
        </a>
        <button
          type="button"
          className="shellIcon shellMenu"
          aria-label="Menu"
          aria-haspopup="dialog"
          onClick={() => {
            drawer.current?.showModal();
            drawer.current?.querySelector<HTMLElement>('[aria-current]')?.focus();
          }}
        >
          <Icon d="M1.5 2.5h7M1.5 5h7M1.5 7.5h7" />
        </button>
        <a className="shellMark" href={BASE}>
          rbed <span className="shellVersion">{__RBED_VERSION__}</span>
        </a>
        <button type="button" className="shellSearch" onClick={() => search()} aria-keyshortcuts="Control+K Meta+K /">
          <Icon d="M6.4 6.4 9 9M4 7.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" />
          <span>Search</span>
          <kbd>{SHORTCUT}</kbd>
        </button>
        <div className="shellOut">
          <a className="shellIcon" href={`${BASE}llms.txt`} aria-label="llms.txt, the documentation for coding agents" title="llms.txt">
            <Icon d="M2.5 1h3.5L8 3v6H2.5ZM6 1v2h2M4 5h2.5M4 7h2.5" />
          </a>
          <a className="shellIcon" href={REPOSITORY} aria-label="Source on GitHub" title="Source on GitHub">
            <Icon d="M3.5 2.5 1 5l2.5 2.5M6.5 2.5 9 5 6.5 7.5" />
          </a>
          <a
            className="shellIcon"
            href="https://central.sonatype.com/artifact/io.github.romanhaendler/rbed"
            aria-label="rbed on Maven Central"
            title="rbed on Maven Central"
          >
            <Icon d="M5 1 9 3v4L5 9 1 7V3ZM1 3l4 2 4-2M5 5v4" />
          </a>
        </div>
      </header>
      <div className="shellBody">
        <Rail current={page?.path} />
        <main className="shellContent" id="main" tabIndex={-1}>
          {page === undefined ? <NotFound search={searchFromAddress} /> : <MarkdownPage key={page.path} page={page} />}
        </main>
      </div>
      {/* The sidebar again, as a drawer for narrow screens. A click on the dialog itself is one on
          its backdrop, since the sidebar fills the dialog. */}
      <dialog
        ref={drawer}
        className="shellDrawer"
        aria-label="Menu"
        onClick={(event) => {
          if (event.target === event.currentTarget) event.currentTarget.close();
        }}
      >
        <div className="drawerHead">
          <span className="shellMark">rbed</span>
          <button type="button" className="shellIcon" aria-label="Close menu" onClick={() => drawer.current?.close()}>
            <Icon d="M2 2l6 6M8 2 2 8" />
          </button>
        </div>
        <Rail current={page?.path} />
      </dialog>
      <CommandPalette
        key={searches}
        open={searching}
        onClose={() => setSearching(false)}
        items={SEARCH}
        restingItems={resting}
        onChoose={(path) => {
          // The palette closes, and the chosen page's heading takes the focus, so a screen
          // reader announces it.
          setSearching(false);
          go(href(path));
          requestAnimationFrame(() => document.querySelector<HTMLElement>('.shellContent h1')?.focus({ preventScroll: true }));
        }}
        aria-label="Search the documentation"
      />
    </>
  );
}
