import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { run, scenarios, source } from './bridge';

const scenario = scenarios()[0];
const time = (millis: number) => millis / scenario.timeUnit.millis;

function App() {
  const [parameters, setParameters] = useState(() =>
    Object.fromEntries(scenario.parameters.map((p) => [p.id, p.default])),
  );
  const result = run(scenario.id, parameters);

  return (
    <main>
      <h1>{scenario.title}</h1>
      {scenario.parameters.map((p) => (
        <label key={p.id}>
          {p.label}{' '}
          <input
            type="range"
            name={p.id}
            min={p.min}
            max={p.max}
            step={p.step}
            value={parameters[p.id]}
            onChange={(e) => setParameters({ ...parameters, [p.id]: Number(e.target.value) })}
          />{' '}
          <output>
            {parameters[p.id]} {p.unit}
          </output>
        </label>
      ))}
      <pre>
        <code>{source(scenario, parameters)}</code>
      </pre>
      {'error' in result ? (
        <p role="alert">{result.error}</p>
      ) : (
        <>
          <h2>Events</h2>
          <table id="events">
            <thead>
              <tr>
                <th>Name</th>
                <th>Start ({scenario.timeUnit.name})</th>
                <th>Confirmed ({scenario.timeUnit.name})</th>
                <th>End ({scenario.timeUnit.name})</th>
                <th>Truncated</th>
              </tr>
            </thead>
            <tbody>
              {result.events.map((e) => (
                <tr key={`${e.name} ${e.start}`}>
                  <td>{e.name}</td>
                  <td>{time(e.start)}</td>
                  <td>{time(e.confirmed)}</td>
                  <td>{time(e.end)}</td>
                  <td>
                    {[e.truncatedStart && 'start', e.truncatedEnd && 'end'].filter(Boolean).join(', ')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <h2>Samples</h2>
          <table>
            <thead>
              <tr>
                <th>Timestamp ({scenario.timeUnit.name})</th>
                {scenario.channels.map((c) => (
                  <th key={c}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.samples.map((s) => (
                <tr key={s.timestamp}>
                  <td>{time(s.timestamp)}</td>
                  {scenario.channels.map((c) => (
                    <td key={c}>{s.values[c] ?? ''}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
