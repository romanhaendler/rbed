// The JavaScript module TeaVM compiles from site/scenarios (see vite.config.ts).
declare module 'rbed-scenarios' {
  export function scenarios(): string;
  export function run(scenarioId: string, parameters: string, drawnChannels: string): string;
}
