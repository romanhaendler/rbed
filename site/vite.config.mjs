import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import { prerender } from './prerender.mjs';
import { rbedVersion } from './version.mjs';

const root = (path) => fileURLToPath(new URL(path, import.meta.url));

const version = rbedVersion();

export default defineConfig({
  // served at https://romanhaendler.github.io/rbed/; npm run preview serves it at /rbed/ too
  base: '/rbed/',
  define: {
    __RBED_VERSION__: JSON.stringify(version),
  },
  build: {
    rolldownOptions: {
      // umriss-ui's modules start with "use client", which only means something to server rendering
      onLog: (level, log, handler) => log.code !== 'MODULE_LEVEL_DIRECTIVE' && handler(level, log),
      // React, umriss-ui and the compiled Scenarios each in a file of their own, loaded in
      // parallel and cached apart from the app, which changes most often.
      output: {
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|scheduler)[\\/]/ },
            { name: 'umriss', test: /node_modules[\\/]@umriss-ui[\\/]/ },
            { name: 'rbed', test: /scenarios[\\/]target[\\/]js[\\/]rbed\.js$/ },
          ],
        },
      },
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
      // Every page of src/pages.ts gets its own index.html with its text prerendered, its Markdown
      // twin, and llms.txt, llms-full.txt, sitemap.xml and 404.html (prerender.mjs).
      name: 'rbed-prerender',
      apply: 'build',
      closeBundle: () => prerender(root('dist'), version),
    },
  ],
});
