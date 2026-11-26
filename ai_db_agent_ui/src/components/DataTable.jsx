export default function DataTable({ columns, rows }) {
  if (!columns?.length && !rows?.length) {
    return (
      <div style={{
        textAlign: 'center', padding: 'var(--sp-6)',
        color: 'var(--text-muted)', fontSize: '0.84rem', fontStyle: 'italic',
      }}>
        No data returned.
      </div>
    );
  }

  const cols = columns?.length ? columns : Object.keys(rows?.[0] || {});

  return (
    <div style={{
      overflowX: 'auto',
      borderRadius: 'var(--r-md)',
      border: '1px solid var(--border)',
      background: 'rgba(0,0,0,0.25)',
    }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
        <thead>
          <tr style={{ background: 'rgba(79,140,255,0.08)', borderBottom: '1px solid var(--border)' }}>
            {cols.map((col, i) => (
              <th key={i} style={{
                padding: '9px 14px',
                textAlign: 'left',
                color: 'var(--accent)',
                fontWeight: 700,
                letterSpacing: '0.04em',
                fontSize: '0.74rem',
                textTransform: 'uppercase',
                whiteSpace: 'nowrap',
              }}>
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows?.length === 0 ? (
            <tr>
              <td colSpan={cols.length} style={{
                padding: 'var(--sp-5)', textAlign: 'center',
                color: 'var(--text-muted)', fontStyle: 'italic',
              }}>
                Query returned 0 rows
              </td>
            </tr>
          ) : (
            rows?.map((row, ri) => (
              <tr key={ri} style={{
                borderBottom: ri < rows.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                background: ri % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.015)',
                transition: 'background 0.15s',
              }}
              onMouseEnter={e => e.currentTarget.style.background = 'rgba(79,140,255,0.06)'}
              onMouseLeave={e => e.currentTarget.style.background = ri % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.015)'}
              >
                {cols.map((col, ci) => {
                  const val = Array.isArray(row) ? row[ci] : row[col];
                  return (
                    <td key={ci} style={{
                      padding: '8px 14px',
                      color: val === null || val === undefined ? 'var(--text-muted)' : 'var(--text-primary)',
                      fontStyle: val === null || val === undefined ? 'italic' : 'normal',
                      fontFamily: typeof val === 'number' ? "'JetBrains Mono', monospace" : 'inherit',
                      whiteSpace: 'nowrap',
                      maxWidth: 260,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}>
                      {val === null || val === undefined ? 'NULL' : String(val)}
                    </td>
                  );
                })}
              </tr>
            ))
          )}
        </tbody>
      </table>
      {rows?.length > 0 && (
        <div style={{
          padding: '6px 14px',
          borderTop: '1px solid var(--border-subtle)',
          fontSize: '0.72rem', color: 'var(--text-muted)',
          display: 'flex', justifyContent: 'flex-end',
        }}>
          {rows.length} {rows.length === 1 ? 'row' : 'rows'} · {cols.length} {cols.length === 1 ? 'column' : 'columns'}
        </div>
      )}
    </div>
  );
}
