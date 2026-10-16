// The rbed version of this commit, as the root pom states it: for the Maven calls of package.json
// (`node version.mjs` prints it), the build (vite.config.mjs) and prerender.mjs.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function rbedVersion() {
  const pom = readFileSync(new URL('../pom.xml', import.meta.url), 'utf8');
  return /<artifactId>rbed<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom)[1];
}

if (process.argv[1] === fileURLToPath(import.meta.url)) console.log(rbedVersion());
