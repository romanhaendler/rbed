// The JavaScript module TeaVM compiles from site/scenarios (see vite.config.mjs).
declare module 'rbed-scenarios' {
  export function scenarios(): string;
  export function run(
    scenarioId: string,
    parameters: string,
    drawnChannels: string,
    restoreFrom: string,
  ): string;
}

/** The rbed version of the build (vite.config.mjs). */
declare const __RBED_VERSION__: string;
