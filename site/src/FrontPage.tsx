// The front page: what rbed is, how to install it, the README's first Rule live, the domain examples.
import { scenarios } from './bridge';
import { CodeBlock } from './CodeBlock';
import { ScenarioView } from './ScenarioView';

export const REPOSITORY = 'https://github.com/romanhaendler/rbed';

const FIRST_RULE = scenarios().find((s) => s.id === 'first-rule')!;

const MAVEN = `<dependency>
  <groupId>io.github.romanhaendler</groupId>
  <artifactId>rbed</artifactId>
  <version>${__RBED_VERSION__}</version>
</dependency>`;

const GRADLE = `implementation("io.github.romanhaendler:rbed:${__RBED_VERSION__}")`;

/** The README's Examples table, in its order. */
const EXAMPLES = [
  ['Cranes', 'CraneScenarioTest', 'hysteresis, during, classifying a load, partial set-down via latest'],
  ['Manufacturing', 'examples/MachineToolOverheatExample', 'sampling-rate mode, rate, combined durings'],
  ['Building services', 'examples/HvacShortCyclingExample', 'after().within(), count().within()'],
  ['Healthcare', 'examples/VitalSignsExample', 'advanceTo and age() for a silent sensor, maxGap'],
  ['Automotive', 'examples/FleetTelematicsExample', 'deceleration via rate, distance via integral'],
  ['Finance', 'examples/TradingSignalsExample', 'moving-average crosses, truncated start after warm-up'],
  ['Logistics', 'examples/ColdChainExample', 'attributing a cause with after, totalDuration limit'],
  ['Environment', 'examples/FloodWarningExample', 'smoothed rate of a moving average, hysteresis'],
  ['Sports', 'examples/IntervalTrainingExample', 'after, rep count via count, listener-side timing'],
  ['IT operations', 'examples/DeploymentRegressionExample', 'a Pulse Channel reporting deployments'],
];

export function FrontPage() {
  return (
    <div className="page">
      <header className="pageHead">
        <h1 className="pageName" tabIndex={-1}>
          rbed
        </h1>
        <p className="pagePromise">Rule-based event detection in time series, for Java.</p>
        <p className="pageSentence">
          A small Java 17 library that finds <strong>Events</strong> in multi-channel time series by evaluating
          declarative <strong>Rules</strong>, on a live stream or over a recorded series. Zero runtime dependencies.
        </p>
        <p className="pageSentence">
          You describe <em>what</em> an Event is ("the weight is above 500 kg for 2 seconds"), and rbed tells you{' '}
          <em>when</em> it happened: when it started, when it was confirmed, when it ended, and what the data looked
          like in between. Every chart on this site is the real rbed, compiled to JavaScript and running in your
          browser.
        </p>
      </header>

      <section className="section" aria-labelledby="install">
        <h2 className="sectionTitle" id="install">
          Install
        </h2>
        <p>
          rbed {__RBED_VERSION__} is on Maven Central and needs Java 17 or later.
        </p>
        <div className="installGrid">
          <div className="codeCard">
            <CodeBlock language="Maven" source={MAVEN} />
          </div>
          <div className="codeCard">
            <CodeBlock language="Gradle" source={GRADLE} />
          </div>
        </div>
      </section>

      <section className="section" aria-labelledby="first-rule">
        <h2 className="sectionTitle" id="first-rule">
          A first Rule
        </h2>
        <p>
          <code>HighTemperature</code> is an Event while the Channel <code>temp</code> is above a threshold. Move the
          threshold and the Detector runs again on the same three Samples.
        </p>
        <ScenarioView scenario={FIRST_RULE} />
        <p>Three steps are always the same:</p>
        <ol>
          <li>
            <strong>Build a Detector</strong> from the Rules and a listener.
          </li>
          <li>
            <strong>Feed it Samples</strong> with <code>push(timestamp, values)</code>, in time order.
          </li>
          <li>
            <strong>Close it</strong> with <code>close()</code> when the data ends, so that an Event still in progress
            is reported, too.
          </li>
        </ol>
      </section>

      <section className="section" aria-labelledby="examples">
        <h2 className="sectionTitle" id="examples">
          Domain examples
        </h2>
        <p>Runnable end-to-end examples from different domains, each a test with its expected Events.</p>
        <ul className="tiles">
          {EXAMPLES.map(([domain, test, shows]) => (
            <li key={test}>
              <a className="tile" href={`${REPOSITORY}/blob/main/src/test/java/io/github/romanhaendler/rbed/${test}.java`}>
                <strong>{domain}</strong>
                <code>{test.replace('examples/', '')}</code>
                <span>{shows}</span>
              </a>
            </li>
          ))}
        </ul>
      </section>

      <p className="pageFoot">
        <a href={REPOSITORY}>Source on GitHub</a> ·{' '}
        <a href="https://central.sonatype.com/artifact/io.github.romanhaendler/rbed">Maven Central</a> ·{' '}
        <a href={`${REPOSITORY}/blob/main/LICENSE`}>MIT licence</a>
      </p>
    </div>
  );
}
