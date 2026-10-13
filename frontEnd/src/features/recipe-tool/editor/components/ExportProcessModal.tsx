/** Shows a process's export JSON, with download and copy. */
export default function ExportProcessModal({ json, nodeCount, edgeCount, fileName, onClose }: {
  json: string
  nodeCount: number
  edgeCount: number
  fileName: string
  onClose: () => void
}) {
  const download = () => {
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="flow-canvas-export-modal-overlay" onClick={onClose}>
      <div className="flow-canvas-export-modal" onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', borderBottom: '1px solid var(--flow-border)' }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--flow-text)' }}>📤 Export Process</div>
            <div style={{ fontSize: 11, color: 'var(--flow-text-subtle)', marginTop: 2 }}>{nodeCount} nodes · {edgeCount} edges</div>
          </div>
          <div style={{ display: 'flex', gap: 7 }}>
            <button onClick={download} style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-success-border)', background: 'var(--flow-success-soft)', color: 'var(--flow-success)', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
              ⬇ Download .json
            </button>
            <button onClick={() => void navigator.clipboard.writeText(json)} style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-info-border)', background: 'var(--flow-info-soft)', color: 'var(--flow-info)', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
              📋 Copy
            </button>
            <button onClick={onClose} style={{ width: 30, height: 30, borderRadius: 7, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-subtle)', cursor: 'pointer', fontSize: 15 }}>✕</button>
          </div>
        </div>
        <pre style={{ flex: 1, overflow: 'auto', margin: 0, padding: '14px 18px', fontSize: 11, lineHeight: 1.65, color: 'var(--flow-text)', background: 'var(--flow-surface-muted)', fontFamily: "'Fira Code', 'Cascadia Code', monospace" }}>
          {json}
        </pre>
      </div>
    </div>
  )
}
