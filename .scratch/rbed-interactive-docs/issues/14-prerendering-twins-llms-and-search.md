# 14: Prerendering, Markdown twins, llms.txt and search

**What to build:** The build output a reader without JavaScript, a coding agent or a search engine needs, like the umriss-ui Pages: a prerendered HTML file per page with its text, its Java source and its Event table at default parameters (computed by running the same Scenarios on the JVM at build time); a Markdown twin per page announced in the page's head; an `llms.txt` in the format umriss-ui uses (`# rbed`, a `>` summary, then `##` sections of `- [Title](url): description` entries) listing every page's twin and the reference material (README and CONTEXT.md on GitHub, Javadoc), and pointing at an `llms-full.txt` that holds the complete documentation as one plain-text file: the README with its links made absolute, the glossary from CONTEXT.md, and every page's Markdown twin in navigation order; a link to `llms.txt` in the site header, as on the umriss-ui Pages; a sitemap; a search index with a search across all pages; and a sharing image. Pages added later get all of this without extra work. See spec: User Stories 39, 47 to 49; Implementation Decisions "Build-time output".

**Blocked by:** 02, 03

**Status:** ready-for-agent

- [ ] Each page's HTML contains its text, its Java source and its default Event table before any script runs
- [ ] The live page replaces the prerendered content without a visible jump
- [ ] Each page has a Markdown twin, linked from its head
- [ ] `llms.txt` lists every page's twin and the reference material, and links to `llms-full.txt`
- [ ] `llms-full.txt` contains the README (absolute links), the glossary and every page's twin, in navigation order
- [ ] The site header links to `llms.txt`
- [ ] A sitemap and a sharing image are deployed
- [ ] A search finds a page by a method name it explains, such as `averageOverDuration`
- [ ] A newly added page appears in twins, `llms.txt`, `llms-full.txt`, sitemap and search without changes to this build step
