import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

export default defineConfig({
  resolve: {
    alias: {
      // the Scenarios and rbed, compiled by TeaVM (npm run build:java)
      'rbed-scenarios': fileURLToPath(new URL('scenarios/target/js/rbed.js', import.meta.url)),
    },
  },
});
