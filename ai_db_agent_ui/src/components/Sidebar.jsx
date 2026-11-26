import { Server, Hash, Layers, TableProperties, ChevronRight, Info, RefreshCw } from 'lucide-react';

function InfoRow({ icon: Icon, label, value, mono }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 3,
      padding: 'var(--sp-3)',
      background: 'rgba(255,255,255,0.02)',
      borderRadius: 'var(--r-md)',
      border: '1px solid var(--border-subtle)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Icon size={12} color="var(--text-muted)" />
        <span style={{ fontSize: '0.69rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.07em', fontWeight: 600 }}>
          {label}
        </span>
      </div>
      <span style={{
        fontSize: '0.84rem',
        color: 'var(--text-primary)',
        fontFamily: mono ? "'JetBrains Mono', monospace" : 'inherit',
        fontWeight: 500,
        wordBreak: 'break-all',
      }}>
        {value || <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>—</span>}
      </span>
    </div>
  );
}

function Section({ title, action, children }) {
  return (
    <div>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: 'var(--sp-2)', paddingLeft: 2,
      }}>
        <span style={{
          fontSize: '0.69rem', fontWeight: 700, letterSpacing: '0.1em',
          color: 'var(--text-muted)', textTransform: 'uppercase',
        }}>
          {title}
        </span>
        {action}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-2)' }}>
        {children}
      </div>
    </div>
  );
}

const DB_COLOR = {
  POSTGRES: '#4169e1',
  MYSQL:    '#e68a00',
  ORACLE:   '#c0392b',
};

export default function Sidebar({ connected, connectionInfo, sessionId, schema: schemaProp, onRefreshSchema, refreshingSchema }) {
  // Always work with an array
  const schema = Array.isArray(schemaProp) ? schemaProp : null;
  const dbColor = DB_COLOR[connectionInfo?.dbType] || 'var(--accent)';

  return (
    <aside className={`session-sidebar${connected ? '' : ' session-sidebar--disconnected'}`} style={{
      width: 'var(--sidebar-w)',
      flexShrink: 0,
      background: 'var(--bg-surface)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Header */}
      <div style={{
        padding: 'var(--sp-4) var(--sp-4) var(--sp-3)',
        borderBottom: '1px solid var(--border-subtle)',
        flexShrink: 0,
      }}>
        <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
          Session Info
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 'var(--sp-4)', display: 'flex', flexDirection: 'column', gap: 'var(--sp-5)' }}>
        {!connected ? (
          /* Empty state */
          <div style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', gap: 'var(--sp-3)', paddingTop: 'var(--sp-8)',
            color: 'var(--text-muted)', textAlign: 'center',
          }}>
            <div style={{
              width: 48, height: 48, borderRadius: 'var(--r-lg)',
              background: 'var(--accent-subtle)', border: '1px solid var(--border)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Server size={22} color="var(--text-muted)" />
            </div>
            <div style={{ fontSize: '0.82rem', lineHeight: 1.5 }}>
              Connect to a database<br />to see session details
            </div>
          </div>
        ) : (
          <>
            {/* DB Type pill */}
            {connectionInfo?.dbType && (
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                <div style={{
                  display: 'flex', alignItems: 'center', gap: 'var(--sp-2)',
                  padding: '6px 18px', borderRadius: 'var(--r-full)',
                  background: `${dbColor}18`, border: `1px solid ${dbColor}40`,
                  color: dbColor, fontWeight: 700, fontSize: '0.82rem', letterSpacing: '0.06em',
                }}>
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: dbColor, display: 'inline-block' }} />
                  {connectionInfo.dbType}
                </div>
              </div>
            )}

            {/* Connection info */}
            <Section title="Connection">
              <InfoRow
                icon={Server} label="Host"
                value={connectionInfo?.host && connectionInfo?.port
                  ? `${connectionInfo.host}:${connectionInfo.port}`
                  : connectionInfo?.host}
              />
              <InfoRow icon={Layers}          label="Database" value={connectionInfo?.database} mono />
              {connectionInfo?.schema && (
                <InfoRow icon={TableProperties} label="Schema"   value={connectionInfo.schema}   mono />
              )}
            </Section>

            {/* Session */}
            <Section title="Session">
              <InfoRow icon={Hash} label="Session ID" value={sessionId ? sessionId.slice(0, 16) + '…' : null} mono />
            </Section>

            {/* Schema tables */}
            <Section
              title={`Tables${schema?.length ? ` (${schema.length})` : ''}`}
              action={
                onRefreshSchema && (
                  <button
                    id="refresh-schema-btn"
                    onClick={onRefreshSchema}
                    disabled={refreshingSchema}
                    title="Refresh schema"
                    style={{
                      background: 'none', border: 'none', cursor: 'pointer',
                      color: refreshingSchema ? 'var(--accent)' : 'var(--text-muted)',
                      padding: 2, display: 'flex', alignItems: 'center',
                      transition: 'color 0.2s',
                    }}
                  >
                    <RefreshCw
                      size={12}
                      style={{ animation: refreshingSchema ? 'spin 0.7s linear infinite' : 'none' }}
                    />
                  </button>
                )
              }
            >
              {!schema || schema.length === 0 ? (
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontStyle: 'italic', padding: '4px 2px' }}>
                  No schema info available
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  {schema.slice(0, 25).map((tbl, i) => {
                    const name = typeof tbl === 'string' ? tbl : (tbl.tableName || tbl.name || JSON.stringify(tbl));
                    return (
                      <div
                        key={i}
                        style={{
                          display: 'flex', alignItems: 'center', gap: 6,
                          padding: '5px 8px', borderRadius: 'var(--r-sm)',
                          background: 'rgba(255,255,255,0.02)',
                          transition: 'background 0.15s', cursor: 'default',
                        }}
                        onMouseEnter={e => e.currentTarget.style.background = 'var(--accent-subtle)'}
                        onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.02)'}
                      >
                        <TableProperties size={11} color="var(--accent)" />
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontFamily: "'JetBrains Mono', monospace", flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {name}
                        </span>
                        <ChevronRight size={10} color="var(--text-muted)" />
                      </div>
                    );
                  })}
                  {schema.length > 25 && (
                    <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)', textAlign: 'center', paddingTop: 4 }}>
                      + {schema.length - 25} more tables
                    </div>
                  )}
                </div>
              )}
            </Section>

            {/* Tip */}
            <div style={{
              marginTop: 'auto', padding: 'var(--sp-3)',
              background: 'var(--accent-subtle)', border: '1px solid var(--border)',
              borderRadius: 'var(--r-md)',
            }}>
              <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
                <Info size={12} color="var(--accent)" style={{ marginTop: 2, flexShrink: 0 }} />
                <p style={{ fontSize: '0.74rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                  Ask in plain English. The AI generates and runs SQL on your behalf.
                </p>
              </div>
            </div>
          </>
        )}
      </div>
    </aside>
  );
}
