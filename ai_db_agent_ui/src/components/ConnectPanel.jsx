import { useState } from 'react';
import {
  ChevronDown,
  Database,
  Layers,
  Lock,
  Plug,
  Server,
  Shield,
  ShieldOff,
  Sparkles,
  TableProperties,
  User,
} from 'lucide-react';

const DB_DEFAULTS = {
  POSTGRES: { port: '5432',  placeholder: 'PostgreSQL database name' },
  MYSQL:    { port: '3306',  placeholder: 'MySQL database name' },
  ORACLE:   { port: '1521',  placeholder: 'Oracle service name (e.g. ORCL)' },
};

function Field({ id, label, icon: Icon, type = 'text', value, onChange, placeholder, autoComplete, required = true }) {
  return (
    <div className="form-group">
      <label htmlFor={id} className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        {Icon && <Icon size={11} />} {label}
      </label>
      <input
        id={id}
        className="form-input"
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete={autoComplete}
        required={required}
      />
    </div>
  );
}

export default function ConnectPanel({ onConnect, loading }) {
  const [form, setForm] = useState({
    dbType: 'POSTGRES',
    host: 'localhost',
    port: '5432',
    database: '',
    username: '',
    password: '',
    schema: '',
    ssl: false,
  });
  const [error, setError] = useState('');

  const set = field => val => {
    setForm(f => {
      const next = { ...f, [field]: val };
      if (field === 'dbType') next.port = DB_DEFAULTS[val]?.port || f.port;
      return next;
    });
    setError('');
  };

  const handleSubmit = async e => {
    e.preventDefault();
    if (!form.host || !form.database || !form.username || !form.password) {
      setError('Please fill in all required fields.');
      return;
    }
    try {
      await onConnect(form);
    } catch (err) {
      setError(err.message || 'Connection failed.');
    }
  };

  const dbInfo = DB_DEFAULTS[form.dbType];

  return (
    <section className="connect-page" aria-labelledby="connect-title">
      <div className="connect-card glass animate-fade-in-up">
        {/* Panel Header */}
        <div className="connect-card__header">
          <div className="connect-eyebrow">
            <Sparkles size={12} aria-hidden="true" />
            AI-powered data workspace
          </div>
          <div className="connect-card__icon" aria-hidden="true">
            <Database size={24} color="#fff" />
          </div>
          <h2 id="connect-title">Connect to Database</h2>
          <p>
            Enter your database credentials to start querying with AI
          </p>
        </div>

        <form className="connect-form" onSubmit={handleSubmit}>
          <div className="connect-form__section-label">Connection details</div>

          {/* DB Type */}
          <div className="form-group">
            <label htmlFor="dbType" className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <Database size={11} /> Database Type
            </label>
            <div className="select-shell">
              <select
                id="dbType"
                className="form-select"
                value={form.dbType}
                onChange={e => set('dbType')(e.target.value)}
                style={{ paddingRight: 40 }}
              >
                <option value="POSTGRES">PostgreSQL</option>
                <option value="MYSQL">MySQL</option>
                <option value="ORACLE">Oracle</option>
              </select>
              <ChevronDown size={15} color="var(--text-muted)" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }} />
            </div>
          </div>

          {/* Host + Port */}
          <div className="connect-field-grid connect-field-grid--host">
            <Field id="host"     label="Host"     icon={Server} value={form.host}     onChange={set('host')}     placeholder="localhost" />
            <div className="form-group">
              <label htmlFor="port" className="form-label">Port</label>
              <input
                id="port"
                className="form-input"
                inputMode="numeric"
                value={form.port}
                onChange={e => set('port')(e.target.value)}
                placeholder={dbInfo?.port}
                required
              />
            </div>
          </div>

          <div className="connect-field-grid">
            <Field
              id="database" label={form.dbType === 'ORACLE' ? 'Service Name' : 'Database'}
              icon={Layers} value={form.database} onChange={set('database')}
              placeholder={dbInfo?.placeholder}
            />
            <Field
              id="schema" label="Schema (optional)" icon={TableProperties}
              value={form.schema} onChange={set('schema')}
              placeholder="public, dbo, ..." required={false}
            />
          </div>

          <div className="connect-form__section-label connect-form__section-label--credentials">Credentials</div>

          <div className="connect-field-grid">
            <Field id="username" label="Username" icon={User} value={form.username} onChange={set('username')} placeholder="db username" autoComplete="username" />
            <Field id="password" label="Password" icon={Lock} type="password" value={form.password} onChange={set('password')} placeholder="••••••••" autoComplete="current-password" />
          </div>

          {/* SSL Toggle */}
          <button
            id="ssl-toggle"
            type="button"
            className={`ssl-toggle${form.ssl ? ' ssl-toggle--enabled' : ''}`}
            onClick={() => set('ssl')(!form.ssl)}
            aria-pressed={form.ssl}
          >
            {form.ssl ? <Shield size={15} /> : <ShieldOff size={15} color="var(--text-muted)" />}
            <div className="ssl-toggle__copy">
              <div className="ssl-toggle__title">
                SSL / TLS: <span className={form.ssl ? 'ssl-status ssl-status--enabled' : 'ssl-status'}>
                  {form.ssl ? 'ENABLED' : 'DISABLED'}
                </span>
              </div>
              <div className="ssl-toggle__description">
                {form.ssl ? 'Encrypted connection (require SSL)' : 'No SSL — recommended for local databases'}
              </div>
            </div>
            {/* pill indicator */}
            <span className="ssl-toggle__switch" aria-hidden="true"><span /></span>
          </button>

          {error && (
            <div className="connect-error" role="alert">
              {error}
            </div>
          )}

          <div className="connect-security-note">
            <Shield size={14} aria-hidden="true" />
            <span>Your credentials are used only to create this database session.</span>
          </div>

          <button
            id="connect-submit-btn"
            className="btn btn-primary"
            type="submit"
            disabled={loading}
          >
            {loading ? <><span className="spinner" />Connecting…</> : <><Plug size={16} />Connect</>}
          </button>
        </form>
      </div>
    </section>
  );
}
