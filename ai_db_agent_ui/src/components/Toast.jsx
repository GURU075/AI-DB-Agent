import { useState, useEffect, useCallback } from 'react';
import { CheckCircle2, XCircle, AlertTriangle, Info, X } from 'lucide-react';
import { registerToastHandler } from '../services/toast';

const TOAST_ICONS = {
  success: <CheckCircle2 size={16} color="var(--success)" />,
  error:   <XCircle     size={16} color="var(--danger)"  />,
  warning: <AlertTriangle size={15} color="var(--warning)" />,
  info:    <Info        size={16} color="var(--accent)"  />,
};
const TOAST_COLORS = {
  success: { bg: 'var(--success-subtle)', border: 'rgba(34,211,160,0.25)',  text: 'var(--success)' },
  error:   { bg: 'var(--danger-subtle)',  border: 'rgba(255,91,107,0.25)', text: 'var(--danger)'  },
  warning: { bg: 'var(--warning-subtle)', border: 'rgba(245,166,35,0.3)',  text: 'var(--warning)' },
  info:    { bg: 'var(--accent-subtle)',  border: 'var(--border-bright)',   text: 'var(--accent)'  },
};

function ToastItem({ id, type = 'info', title, message, onRemove }) {
  const c = TOAST_COLORS[type];
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // mount animation
    const t = setTimeout(() => setVisible(true), 10);
    return () => clearTimeout(t);
  }, []);

  const dismiss = () => {
    setVisible(false);
    setTimeout(() => onRemove(id), 300);
  };

  useEffect(() => {
    const t = setTimeout(dismiss, 4500);
    return () => clearTimeout(t);
  }, []); // eslint-disable-line

  return (
    <div style={{
      display: 'flex', gap: 10, alignItems: 'flex-start',
      padding: '12px 14px',
      background: c.bg,
      border: `1px solid ${c.border}`,
      borderRadius: 'var(--r-md)',
      boxShadow: 'var(--shadow-md)',
      backdropFilter: 'blur(16px)',
      minWidth: 280, maxWidth: 360,
      transform: visible ? 'translateX(0)' : 'translateX(120%)',
      opacity: visible ? 1 : 0,
      transition: 'all 0.3s cubic-bezier(0.4,0,0.2,1)',
    }}>
      <span style={{ flexShrink: 0, marginTop: 1 }}>{TOAST_ICONS[type]}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontWeight: 700, fontSize: '0.84rem', color: c.text, marginBottom: message ? 2 : 0 }}>{title}</div>}
        {message && <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', lineHeight: 1.5, wordBreak: 'break-word' }}>{message}</div>}
      </div>
      <button onClick={dismiss} style={{ background:'none', border:'none', cursor:'pointer', padding:0, color:'var(--text-muted)', flexShrink:0 }}>
        <X size={14} />
      </button>
    </div>
  );
}

export function ToastContainer() {
  const [toasts, setToasts] = useState([]);
  const addToast = useCallback((t) => setToasts(p => [...p.slice(-4), t]), []);
  const removeToast = useCallback((id) => setToasts(p => p.filter(t => t.id !== id)), []);

  useEffect(() => {
    return registerToastHandler(addToast);
  }, [addToast]);

  return (
    <div style={{
      position: 'fixed', bottom: 24, right: 24,
      display: 'flex', flexDirection: 'column', gap: 10,
      zIndex: 9999, pointerEvents: 'none',
    }}>
      {toasts.map(t => (
        <div key={t.id} style={{ pointerEvents: 'all' }}>
          <ToastItem {...t} onRemove={removeToast} />
        </div>
      ))}
    </div>
  );
}
