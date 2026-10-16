// Every page of the site, in the README's order. The web app builds its navigation, routes and
// search from this list, and the build (prerender.mjs) reads it in Node, so it imports nothing.
//
// A page's text is the Markdown file src/content/<markdownFile(page)>; see site/README.md, "Adding
// a page". A page that is not in this list is not linked, built or searched anywhere.

/** The repository on GitHub, where the README, the glossary and the example tests are. */
export const REPOSITORY = 'https://github.com/romanhaendler/rbed';

export type Page = {
  /** Address below the site's base, starting and ending with a slash. */
  path: string;
  /** The name in the navigation, the search and the document title. */
  title: string;
  /** One sentence, for the search engines, the sharing card and llms.txt. */
  description: string;
  /** The navigation's heading the page stands under; the front page has none. */
  rubric?: 'Guide' | 'Examples';
};

export const PAGES: Page[] = [
  {
    path: '/',
    title: 'rbed',
    description:
      'Rule-based event detection in time series: a small Java library that finds Events in multi-channel data by evaluating declarative Rules.',
  },
  {
    path: '/samples-and-channels/',
    title: 'Samples and Channels',
    description:
      'A Sample is the values of some Channels at one timestamp; a Channel missing from a Sample keeps its last value, so sensors at different rates share one Detector.',
    rubric: 'Guide',
  },
  {
    path: '/event-lifecycle/',
    title: 'The Event lifecycle',
    description:
      'How an Event goes Pending, is Confirmed or Discarded, and Ends, and what forDuration has to do with it.',
    rubric: 'Guide',
  },
  {
    path: '/end-conditions/',
    title: 'End Conditions and hysteresis',
    description:
      'An End Condition with its own threshold and duration keeps a noisy Channel near a threshold from producing a flood of short Events.',
    rubric: 'Guide',
  },
  {
    path: '/data-gaps/',
    title: 'Data gaps',
    description:
      'maxGap bounds how far apart two Samples may be: a longer gap Ends a running Event, and both sides of the gap are marked Truncated.',
    rubric: 'Guide',
  },
  {
    path: '/conditions-and-expressions/',
    title: 'Conditions and Expressions',
    description:
      'What a Rule compares: rates, moving averages and arithmetic between Channels, plotted per Sample, and why no Event starts where an Expression is undefined.',
    rubric: 'Guide',
  },
  {
    path: '/during/',
    title: 'During another Event',
    description:
      "during(a) holds while an Event of Rule a is Confirmed, so one Rule's Events gate another's: a phase, or a combination of Events with their own timing.",
    rubric: 'Guide',
  },
  {
    path: '/after/',
    title: 'After another Event',
    description:
      'Sequences with after(a): within(...) limits how long after an Event of Rule a has Ended it still counts, unless(...) names what interrupts it.',
    rubric: 'Guide',
  },
  {
    path: '/count/',
    title: 'Counting Events',
    description:
      'count(a) is the number of Events of Rule a Confirmed so far, or within a sliding window, plotted here against its limit.',
    rubric: 'Guide',
  },
  {
    path: '/total-duration/',
    title: 'Time spent in Events',
    description:
      'totalDuration(a) sums the durations of the Events of Rule a and grows while one runs, so a cumulative limit is detected the moment it is crossed.',
    rubric: 'Guide',
  },
  {
    path: '/latest/',
    title: 'The latest Event',
    description:
      'latest(a) reads the Aggregates and the duration of the most recent Confirmed Event of Rule a, so a Rule can relate the present to it.',
    rubric: 'Guide',
  },
  {
    path: '/aggregates/',
    title: 'Aggregates',
    description:
      "Every Event carries the minimum, maximum, mean, count, time-weighted mean and integral of each Channel its Rule aggregates.",
    rubric: 'Guide',
  },
  {
    path: '/sampling-rate/',
    title: 'Batch processing and sampling rates',
    description:
      'detect runs a recorded series in one call; with a sampling rate, Samples are pushed without timestamps and time is their index divided by the rate.',
    rubric: 'Guide',
  },
  {
    path: '/silent-sensors/',
    title: 'Silent sensors: advancing time',
    description:
      'advanceTo tells the Detector that time has passed without Samples, so a silent sensor is noticed through age() and durations are reached.',
    rubric: 'Guide',
  },
  {
    path: '/pulse-channels/',
    title: 'Pulse Channels',
    description:
      'Pulse Channels report one-off occurrences such as deployments: their value exists only in the Sample that carries it.',
    rubric: 'Guide',
  },
  {
    path: '/history/',
    title: 'Restarts: History',
    description:
      'Events stored before a restart, passed to the new Detector as History, keep after, count and totalDuration right across the restart.',
    rubric: 'Guide',
  },
  {
    path: '/errors-and-validation/',
    title: 'Errors and validation',
    description:
      'rbed rejects a mistake as early as it can, mostly in build(), with a message that names it: try a few and read them.',
    rubric: 'Guide',
  },
  {
    path: '/memory/',
    title: 'Memory and the Retention Limit',
    description:
      'What a Detector keeps in memory follows from its Rules; the Retention Limit caps every window, dropping the oldest entries, and droppedValues() tells when it did.',
    rubric: 'Guide',
  },
  {
    path: '/cranes/',
    title: 'Cranes',
    description:
      'Lifts, overloads and partial set-downs of a crane from the weight on its hook: hysteresis, during, latest and classifying a lifted load.',
    rubric: 'Examples',
  },
  {
    path: '/manufacturing/',
    title: 'Manufacturing',
    description:
      "Overheating under load and fast heating of a machine tool's spindle, sampled without timestamps: sampling-rate mode, rate and combined durings.",
    rubric: 'Examples',
  },
  {
    path: '/building-services/',
    title: 'Building services',
    description:
      "Short cycling of an air conditioner's compressor: Runs, quick restarts with after().within() and frequent starts with count().within().",
    rubric: 'Examples',
  },
  {
    path: '/healthcare/',
    title: 'Healthcare',
    description:
      'Tachycardia, desaturation and a lost sensor on a bedside monitor: advanceTo and age() for a silent sensor, maxGap for a dropout.',
    rubric: 'Examples',
  },
  {
    path: '/automotive/',
    title: 'Automotive',
    description:
      "Trips, harsh braking and idling from a vehicle's speed and engine rpm: deceleration via rate, a Trip's distance via integral.",
    rubric: 'Examples',
  },
  {
    path: '/finance/',
    title: 'Finance',
    description:
      'Golden and death crosses of two moving averages of daily closing prices, and a start Truncated by the warm-up of the averages.',
    rubric: 'Examples',
  },
  {
    path: '/logistics/',
    title: 'Logistics',
    description:
      'Temperature excursions of a refrigerated truck: attributing one to an open door with after, and a total time limit with totalDuration.',
    rubric: 'Examples',
  },
  {
    path: '/environment/',
    title: 'Environment',
    description:
      'A flood warning from a river gauge: the smoothed rate of a moving average, hysteresis on the way down and a missing report.',
    rubric: 'Examples',
  },
  {
    path: '/sports/',
    title: 'Sports',
    description:
      "Sprints and recoveries of an interval session from a runner's speed and heart rate: after, a rep count via count, and a listener timing each recovery.",
    rubric: 'Examples',
  },
  {
    path: '/it-operations/',
    title: 'IT operations',
    description:
      'Error spikes of a web service attributed to a deployment reported on a Pulse Channel, and high CPU.',
    rubric: 'Examples',
  },
];

/** The page path an address names when the site is served below `base`, or undefined outside it.
 *  A page's index.html is the page. */
export function pathOf(pathname: string, base: string): string | undefined {
  if (!(pathname + '/').startsWith(base)) return undefined;
  return ('/' + pathname.slice(base.length)).replace(/index\.html$/, '').replace(/\/?$/, '/');
}

export function pageAt(pathname: string, base: string): Page | undefined {
  const path = pathOf(pathname, base);
  return PAGES.find((p) => p.path === path);
}

/** The page's Markdown below src/content/, which is also its twin's address below the base. */
export function markdownFile(page: Pick<Page, 'path'>): string {
  return page.path === '/' ? 'index.md' : page.path.slice(1, -1) + '.md';
}

/** The navigation's headings in order; the front page stands under Overview. */
export const RUBRICS = ['Overview', 'Guide', 'Examples'] as const;
export const rubricOf = (page: Pick<Page, 'rubric'>) => page.rubric ?? 'Overview';

export function titleOf(page: Pick<Page, 'path' | 'title'>): string {
  return page.path === '/' ? 'rbed: rule-based event detection' : `${page.title} · rbed`;
}
