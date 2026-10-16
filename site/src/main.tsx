import '@fontsource/geist-sans/400.css';
import '@fontsource/geist-sans/500.css';
import '@fontsource/geist-sans/600.css';
import '@fontsource/geist-mono/400.css';
import './site.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Shell } from './Shell';

// Hides the prerendered page (prerender.mjs) only now that the app runs: if a chunk fails to load
// or a module throws, the reader keeps the prerendered text.
document.documentElement.classList.add('js');
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Shell />
  </StrictMode>,
);
