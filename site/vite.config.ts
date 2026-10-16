import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

export default defineConfig({
  // served at https://romanhaendler.github.io/rbed/; npm run preview serves it at /rbed/ too
  base: '/rbed/',
  resolve: {
    alias: {
      // the Scenarios and rbed, compiled by TeaVM (npm run build:java)
      'rbed-scenarios': fileURLToPath(new URL('scenarios/target/js/rbed.js', import.meta.url)),
    },
  },
});
