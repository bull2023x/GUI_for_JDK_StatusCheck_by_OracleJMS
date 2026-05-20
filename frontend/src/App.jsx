import { useEffect, useState } from 'react'
import { Server, ShieldAlert, Activity, Cpu, Monitor, AlertTriangle } from 'lucide-react'
import './App.css'

function riskColor(level) {
  if (level === 'CRITICAL') return '#ef4444'
  if (level === 'HIGH') return '#f97316'
  if (level === 'MEDIUM') return '#eab308'
  return '#22c55e'
}

function App() {
  const [summary, setSummary] = useState(null)
  const [instances, setInstances] = useState([])
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    fetch('/api/risk-summary')
      .then((res) => res.json())
      .then(setSummary)

    fetch('/api/managed-instances')
      .then((res) => res.json())
      .then((data) => {
        setInstances(data)
        if (data.length > 0) setSelected(data[0])
      })
  }, [])

  return (
    <div className="app">
      <header className="hero">
        <div>
          <div className="eyebrow">JMS Visual Intelligence Platform</div>
          <h1>Java Fleet Commander AI</h1>
          <p>Real JMS data transformed into enterprise Java runtime risk intelligence.</p>
        </div>
        <div className="hero-badge">
          <ShieldAlert size={28} />
          <span>Fleet Risk Radar</span>
        </div>
      </header>

      {summary && (
        <section className="summary-grid">
          <div className="card score-card">
            <div className="label">Overall Risk Score</div>
            <div className="score">{summary.overallRiskScore}</div>
            <div className="sub">Fleet: {summary.fleetName}</div>
          </div>
          <div className="card">
            <div className="label">Managed Instances</div>
            <div className="metric">{summary.totalManagedInstances}</div>
            <div className="sub">Discovered by JMS</div>
          </div>
          <div className="card">
            <div className="label">Critical</div>
            <div className="metric critical">{summary.criticalCount}</div>
            <div className="sub">Immediate attention</div>
          </div>
          <div className="card">
            <div className="label">Top Risk Host</div>
            <div className="host">{summary.topRiskHost}</div>
            <div className="sub">Highest calculated score</div>
          </div>
        </section>
      )}

      <main className="main-grid">
        <section className="card map-card">
          <div className="section-title">
            <Activity size={20} />
            Runtime Risk Map
          </div>

          <div className="risk-map">
            {instances.map((item, index) => (
              <button
                key={item.managedInstanceId}
                className={`node ${selected?.managedInstanceId === item.managedInstanceId ? 'selected' : ''}`}
                style={{
                  borderColor: riskColor(item.riskLevel),
                  boxShadow: `0 0 30px ${riskColor(item.riskLevel)}55`,
                  left: `${25 + index * 38}%`,
                  top: `${35 + (index % 2) * 28}%`
                }}
                onClick={() => setSelected(item)}
              >
                <span className="pulse" style={{ background: riskColor(item.riskLevel) }} />
                <Server size={26} />
                <strong>{item.hostname}</strong>
                <small>{item.riskLevel} · {item.riskScore}</small>
              </button>
            ))}
            <div className="map-line" />
          </div>
        </section>

        <section className="card detail-card">
          <div className="section-title">
            <Monitor size={20} />
            Selected Instance
          </div>

          {selected ? (
            <>
              <div className="detail-header">
                <div>
                  <h2>{selected.hostname}</h2>
                  <p>{selected.osName} · {selected.osArchitecture}</p>
                </div>
                <div className="risk-pill" style={{ background: riskColor(selected.riskLevel) }}>
                  {selected.riskLevel}
                </div>
              </div>

              <div className="detail-grid">
                <div>
                  <span>Java Version</span>
                  <strong>{selected.javaVersion}</strong>
                </div>
                <div>
                  <span>Security Status</span>
                  <strong>{selected.javaSecurityStatus}</strong>
                </div>
                <div>
                  <span>Applications</span>
                  <strong>{selected.applicationCount}</strong>
                </div>
                <div>
                  <span>JRE Count</span>
                  <strong>{selected.jreCount}</strong>
                </div>
                <div>
                  <span>Installations</span>
                  <strong>{selected.installationCount}</strong>
                </div>
                <div>
                  <span>Risk Score</span>
                  <strong>{selected.riskScore}</strong>
                </div>
              </div>

              <div className="insight">
                <div className="section-title">
                  <AlertTriangle size={18} />
                  AI-style Recommendation
                </div>
                <p>{selected.recommendation}</p>
              </div>
            </>
          ) : (
            <p>No instance selected.</p>
          )}
        </section>
      </main>

      <section className="card table-card">
        <div className="section-title">
          <Cpu size={20} />
          Runtime Inventory
        </div>
        <table>
          <thead>
            <tr>
              <th>Host</th>
              <th>OS</th>
              <th>Java</th>
              <th>Security</th>
              <th>Apps</th>
              <th>Risk</th>
            </tr>
          </thead>
          <tbody>
            {instances.map((item) => (
              <tr key={item.managedInstanceId} onClick={() => setSelected(item)}>
                <td>{item.hostname}</td>
                <td>{item.osName}</td>
                <td>{item.javaVersion}</td>
                <td>{item.javaSecurityStatus}</td>
                <td>{item.applicationCount}</td>
                <td>
                  <span className="mini-pill" style={{ background: riskColor(item.riskLevel) }}>
                    {item.riskLevel} {item.riskScore}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}

export default App
