import { useEffect, useState } from 'react'
import { Server, ShieldAlert, Activity, Cpu, Monitor, AlertTriangle, Upload, FileJson, RefreshCw } from 'lucide-react'
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
  const [dataStatus, setDataStatus] = useState(null)
  const [managedInstancesFile, setManagedInstancesFile] = useState(null)
  const [fleetsFile, setFleetsFile] = useState(null)
  const [compartmentId, setCompartmentId] = useState('')
  const [fleetId, setFleetId] = useState('')
  const [fleetName, setFleetName] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isUploading, setIsUploading] = useState(false)
  const [isSyncing, setIsSyncing] = useState(false)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [aiAnalysis, setAiAnalysis] = useState(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const loadDashboard = async () => {
    setIsLoading(true)
    setError('')

    try {
      const [summaryResponse, instancesResponse, statusResponse] = await Promise.all([
        fetch('/api/risk-summary'),
        fetch('/api/managed-instances'),
        fetch('/api/jms-data/status')
      ])

      if (!summaryResponse.ok || !instancesResponse.ok || !statusResponse.ok) {
        throw new Error('Could not load JMS dashboard data.')
      }

      const nextSummary = await summaryResponse.json()
      const nextInstances = await instancesResponse.json()
      const nextStatus = await statusResponse.json()

      setSummary(nextSummary)
      setInstances(nextInstances)
      setDataStatus(nextStatus)
      setAiAnalysis(null)
      setSelected((current) => {
        if (nextInstances.length === 0) return null
        return nextInstances.find((item) => item.managedInstanceId === current?.managedInstanceId) ?? nextInstances[0]
      })
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    Promise.resolve().then(loadDashboard)
  }, [])

  const uploadJmsData = async (event) => {
    event.preventDefault()

    if (!managedInstancesFile && !fleetsFile) {
      setError('Select managed-instances.json, fleets.json, or both.')
      return
    }

    const formData = new FormData()
    if (managedInstancesFile) formData.append('managedInstances', managedInstancesFile)
    if (fleetsFile) formData.append('fleets', fleetsFile)

    setIsUploading(true)
    setError('')
    setMessage('')

    try {
      const response = await fetch('/api/jms-data/upload', {
        method: 'POST',
        body: formData
      })

      if (!response.ok) {
        const detail = await response.json().catch(() => null)
        throw new Error(detail?.message || 'Upload failed.')
      }

      const result = await response.json()
      setMessage(`${result.message} Managed instances: ${result.managedInstanceCount}`)
      await loadDashboard()
    } catch (uploadError) {
      setError(uploadError.message)
    } finally {
      setIsUploading(false)
    }
  }

  const loadAiAnalysis = async () => {
    setIsAnalyzing(true)
    setError('')

    try {
      const response = await fetch('/api/ai-analysis')
      if (!response.ok) {
        throw new Error('Could not generate AI analysis.')
      }

      setAiAnalysis(await response.json())
    } catch (analysisError) {
      setError(analysisError.message)
    } finally {
      setIsAnalyzing(false)
    }
  }

  const syncFromOci = async (event) => {
    event.preventDefault()

    if (!fleetId.trim()) {
      setError('Fleet OCID is required for OCI sync.')
      return
    }

    const formData = new FormData()
    if (compartmentId.trim()) formData.append('compartmentId', compartmentId.trim())
    formData.append('fleetId', fleetId.trim())
    if (fleetName.trim()) formData.append('fleetName', fleetName.trim())

    setIsSyncing(true)
    setError('')
    setMessage('')

    try {
      const response = await fetch('/api/oci/sync', {
        method: 'POST',
        body: formData
      })

      if (!response.ok) {
        const detail = await response.json().catch(() => null)
        throw new Error(detail?.message || 'OCI sync failed.')
      }

      const result = await response.json()
      setMessage(`${result.message} Managed instances: ${result.managedInstanceCount}`)
      await loadDashboard()
    } catch (syncError) {
      setError(syncError.message)
    } finally {
      setIsSyncing(false)
    }
  }

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

      <section className="card upload-card">
        <div className="upload-heading">
          <div className="section-title">
            <Upload size={20} />
            JMS JSON Upload
          </div>
          <button className="icon-button" type="button" onClick={loadDashboard} disabled={isLoading || isUploading}>
            <RefreshCw size={18} />
            Refresh
          </button>
        </div>

        <form className="upload-form" onSubmit={uploadJmsData}>
          <label>
            <span><FileJson size={16} /> managed-instances.json</span>
            <input
              type="file"
              accept="application/json,.json"
              onChange={(event) => setManagedInstancesFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <label>
            <span><FileJson size={16} /> fleets.json</span>
            <input
              type="file"
              accept="application/json,.json"
              onChange={(event) => setFleetsFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <button className="upload-button" type="submit" disabled={isUploading}>
            <Upload size={18} />
            {isUploading ? 'Uploading...' : 'Upload JSON'}
          </button>
        </form>

        <div className="data-status">
          <span>Source: {dataStatus?.source ?? 'unknown'}</span>
          <span>Fleet: {dataStatus?.fleetName ?? 'none'}</span>
          <span>Managed Instances: {dataStatus?.managedInstanceCount ?? 0}</span>
        </div>

        {message && <div className="message success">{message}</div>}
        {error && <div className="message error">{error}</div>}
      </section>

      <section className="card oci-sync-card">
        <div className="upload-heading">
          <div className="section-title">
            <RefreshCw size={20} />
            OCI JMS Direct Sync
          </div>
          <button className="icon-button" type="button" onClick={loadDashboard} disabled={isLoading || isSyncing}>
            <RefreshCw size={18} />
            Refresh
          </button>
        </div>

        <form className="oci-form" onSubmit={syncFromOci}>
          <label>
            <span>Compartment OCID</span>
            <input
              type="text"
              value={compartmentId}
              onChange={(event) => setCompartmentId(event.target.value)}
            />
          </label>
          <label>
            <span>Fleet OCID</span>
            <input
              type="text"
              value={fleetId}
              onChange={(event) => setFleetId(event.target.value)}
            />
          </label>
          <label>
            <span>Fleet Name</span>
            <input
              type="text"
              value={fleetName}
              onChange={(event) => setFleetName(event.target.value)}
            />
          </label>
          <button className="upload-button" type="submit" disabled={isSyncing}>
            <RefreshCw size={18} />
            {isSyncing ? 'Syncing...' : 'Sync from OCI'}
          </button>
        </form>
      </section>

      {isLoading && <section className="card empty-state">Loading JMS dashboard data...</section>}

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

      {!isLoading && instances.length > 0 && (
        <section className="card ai-card">
          <div className="ai-heading">
            <div className="section-title">
              <ShieldAlert size={20} />
              AI Fleet Analysis
            </div>
            <button className="icon-button" type="button" onClick={loadAiAnalysis} disabled={isAnalyzing}>
              <RefreshCw size={18} />
              {isAnalyzing ? 'Analyzing...' : 'Generate Analysis'}
            </button>
          </div>

          {aiAnalysis ? (
            <>
              <div className="data-status">
                <span>Provider: {aiAnalysis.provider}</span>
                <span>Model: {aiAnalysis.model || 'fallback'}</span>
                <span>Generated: {aiAnalysis.generatedAt}</span>
              </div>
              <div className="ai-analysis">
                {aiAnalysis.analysis}
              </div>
              {aiAnalysis.note && <div className="message error">{aiAnalysis.note}</div>}
            </>
          ) : (
            <div className="ai-placeholder">
              Generate a fleet-level remediation summary from the currently loaded JMS data.
            </div>
          )}
        </section>
      )}

      {!isLoading && instances.length === 0 && (
        <section className="card empty-state">
          Upload a JMS managed-instances JSON file to populate the dashboard.
        </section>
      )}

      {!isLoading && instances.length > 0 && (
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
                  left: `${22 + (index % 3) * 28}%`,
                  top: `${28 + Math.floor(index / 3) * 28}%`
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
      )}

      {!isLoading && instances.length > 0 && (
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
      )}
    </div>
  )
}

export default App
