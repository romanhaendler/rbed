# 14: Prerendering, Markdown twins, llms.txt and search

**What to build:** The build output a reader without JavaScript, a coding agent or a search engine needs, like the umriss-ui Pages: a prerendered HTML file per page with its text, its Java source and its Event table at default parameters (computed by running the same Scenarios on the JVM at build time); a Markdown twin per page announced in the page's head; an `llms.txt` listing every page; a sitemap; a search index with a search across all pages; and a sharing image. Pages added later get all of this without extra work. See spec: User Stories 39, 47 to 49; Implementation Decisions "Build-time output".

**Blocked by:** 02, 03

**Status:** ready-for-agent

- [ ] Each page's HTML contains its text, its Java source and its default Event table before any script runs
- [ ] The live page replaces the prerendered content without a visible jump
- [ ] Each page has a Markdown twin, linked from its head
- [ ] `llms.txt`, a sitemap and a sharing image are deployed
- [ ] A search finds a page by a method name it explains, such as `averageOverDuration`
- [ ] A newly added page appears in twins, `llms.txt`, sitemap and search without changes to this build step
