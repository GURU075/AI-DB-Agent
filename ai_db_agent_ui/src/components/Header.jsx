import { Database, LogOut, Zap } from 'lucide-react';

export default function Header({ connected, onDisconnect, disconnecting }) {
  return (
    <header className="app-header glass-heavy" style={{
      height: 'var(--header-h)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 var(--sp-6)',
      borderRadius: 0,
      borderTop: 'none',
      borderLeft: 'none',
      borderRight: 'none',
      borderBottom: '1px solid var(--border)',
      position: 'relative',
      zIndex: 100,
      flexShrink: 0,
    }}>
      {/* Logo + Title */}
      <div className="header-brand" style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
        <div style={{
          width: 38, height: 38,
          background: 'linear-gradient(135deg, var(--accent) 0%, #7b5ea7 100%)',
          borderRadius: 'var(--r-md)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: '0 0 18px var(--accent-glow)',
          flexShrink: 0,
        }}>
          <Database size={19} color="#fff" strokeWidth={2} />
        </div>
        <div>
          <div style={{
            fontSize: '1rem', fontWeight: 800, letterSpacing: '-0.02em',
            background: 'linear-gradient(90deg, var(--text-primary) 0%, var(--accent) 100%)',
            WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
          }}>
            AI Database Agent
          </div>
          <div className="header-brand__subtitle" style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontWeight: 500, letterSpacing: '0.04em' }}>
            Natural Language → SQL
          </div>
        </div>
      </div>

      {/* Center tagline */}
      <div className="header-tagline" style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-2)', color: 'var(--text-muted)', fontSize: '0.78rem' }}>
        <Zap size={13} color="var(--warning)" />
        <span>Powered by Spring AI · OpenAI-compatible LLM</span>
      </div>

      {/* Status + Disconnect */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-3)' }}>
        <span className={connected ? 'badge badge-connected' : 'badge badge-disconnected'}>
          <span style={{
            width: 7, height: 7, borderRadius: '50%',
            background: connected ? 'var(--success)' : 'var(--danger)',
            animation: connected ? 'pulse-dot 2s ease-in-out infinite' : 'none',
            display: 'inline-block',
          }} />
          {connected ? 'Connected' : 'Disconnected'}
        </span>

        {connected && (
          <button
            id="disconnect-btn"
            className="btn btn-ghost btn-icon"
            onClick={onDisconnect}
            disabled={disconnecting}
            title="Disconnect"
            style={{ color: 'var(--danger)', borderColor: 'rgba(255,91,107,0.25)' }}
          >
            {disconnecting
              ? <span className="spinner" style={{ width: 15, height: 15 }} />
              : <LogOut size={16} />}
          </button>
        )}
      </div>
    </header>
  );
}
