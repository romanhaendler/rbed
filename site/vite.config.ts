import { copyFileSync, mkdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import { PAGES } from './src/pages.ts';

const root = (path: string) => fileURLToPath(new URL(path, import.meta.url));

// The rbed version of this commit, as the root pom states it.
const pom = readFileSync(root('../pom.xml'), 'utf8');
const version = /<artifactId>rbed<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom)![1];

export default defineConfig({
  define: {
    __RBED_VERSION__: JSON.stringify(version),
  },
  build: {
    rolldownOptions: {
      // umriss-ui's modules start with "use client", which only means something to server rendering
      onLog: (level, log, handler) => log.code !== 'MODULE_LEVEL_DIRECTIVE' && handler(level, log),
    },
  },
  resolve: {
    alias: {
      // the Scenarios and rbed, compiled by TeaVM (npm run build:java)
      'rbed-scenarios': root('scenarios/target/js/rbed.js'),
    },
  },
  plugins: [
    {
      // Every page gets its own index.html, so reloading its path shows it; 404.html shows the
      // app's "no page" view for any other path.
      name: 'rbed-pages',
      apply: 'build',
      closeBundle() {
        const index = root('dist/index.html');
        for (const page of PAGES) {
          if (page.path === '/') continue;
          mkdirSync(root(`dist${page.path}`), { recursive: true });
          copyFileSync(index, root(`dist${page.path}index.html`));
        }
        copyFileSync(index, root('dist/404.html'));
      },
    },
  ],
});
