// The shell after the umriss-ui demo's (MIT): header bar, sidebar from the page list, content
// column, and the routing. Every page is a path of its own below Vite's base; a link to one of
// them moves without a reload, anything else is the browser's.
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { FrontPage, REPOSITORY } from './FrontPage';
import { PAGES, pageAt } from './pages';

const BASE = import.meta.env.BASE_URL;

/** The view of each page in pages.ts, by its path. */
const VIEWS: Record<string, () => ReactNode> = {
  '/': FrontPage,
};

const href = (path: string) => BASE + path.slice(1);

function useCurrentPage() {
  const [page, setPage] = useState(() => pageAt(window.location.pathname, BASE));
  useEffect(() => {
    const follow = () => setPage(pageAt(window.location.pathname, BASE));
    const onClick = (event: MouseEvent) => {
      const link = (event.target as Element | null)?.closest?.('a');
      if (!link || link.target !== '' || link.hasAttribute('download')) return;
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin || pageAt(url.pathname, BASE) === undefined) return;
      if (url.pathname === window.location.pathname && url.hash !== '') return;
      event.preventDefault();
      window.history.pushState(null, '', url.pathname + url.search + url.hash);
      follow();
      document.documentElement.scrollTop = 0;
    };
    window.addEventListener('popstate', follow);
    document.addEventListener('click', onClick);
    return () => {
      window.removeEventListener('popstate', follow);
      document.removeEventListener('click', onClick);
    };
  }, []);
  return page;
}

function Icon({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 10 10" width="16" height="16" aria-hidden="true">
      <path d={d} fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Rail({ current }: { current?: string }) {
  const entry = (path: string, title: string) => (
    <a className="railEntry" href={href(path)} aria-current={current === path ? 'page' : undefined}>
      {title}
    </a>
  );
  return (
    <nav className="rail" aria-label="Pages">
      {entry('/', 'Overview')}
      {(['Guide', 'Examples'] as const).map((rubric) => {
        const pages = PAGES.filter((p) => p.rubric === rubric);
        return (
          pages.length > 0 && (
            <div className="railRubric" key={rubric}>
              <h2 className="railHead">
                <span>{rubric}</span>
                <span className="railCount">{pages.length}</span>
              </h2>
              <ul className="railList">
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

function NotFound() {
  return (
    <div className="page">
      <header className="pageHead">
        <h1 className="pageName" tabIndex={-1}>
          No page at this address
        </h1>
        <p className="pageSentence">
          The page may have moved. Start again from the <a href={BASE}>front page</a>.
        </p>
      </header>
    </div>
  );
}

export function Shell() {
  const page = useCurrentPage();
  const drawer = useRef<HTMLDialogElement>(null);
  const View = page === undefined ? NotFound : VIEWS[page.path];

  useEffect(() => {
    document.title = page === undefined ? 'No page · rbed' : page.path === '/' ? 'rbed: rule-based event detection' : `${page.title} · rbed`;
    // A page chosen in the drawer closes it and takes the focus, so a screen reader announces it.
    if (drawer.current?.open) {
      drawer.current.close();
      document.querySelector<HTMLElement>('.shellContent h1')?.focus({ preventScroll: true });
    }
  }, [page]);

  return (
    <>
      <header className="shellHead">
        <button
          type="button"
          className="shellIcon shellMenu"
          aria-label="Menu"
          aria-haspopup="dialog"
          onClick={() => drawer.current?.showModal()}
        >
          <Icon d="M1.5 2.5h7M1.5 5h7M1.5 7.5h7" />
        </button>
        <a className="shellMark" href={BASE}>
          rbed <span className="shellVersion">{__RBED_VERSION__}</span>
        </a>
        <div className="shellOut">
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
        <main className="shellContent">
          <View />
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
        <Rail current={page?.path} />
      </dialog>
    </>
  );
}
