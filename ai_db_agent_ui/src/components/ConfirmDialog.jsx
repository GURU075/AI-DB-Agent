import { CheckCircle2, XCircle, AlertTriangle } from 'lucide-react';

export default function ConfirmDialog({ sqlPlan, onConfirm, onCancel, confirming }) {
  const sql = typeof sqlPlan === 'string' ? sqlPlan : sqlPlan?.sql;
  const intent = typeof sqlPlan === 'object' ? sqlPlan?.intent : null;
  const reason = typeof sqlPlan === 'object' ? sqlPlan?.reason : null;

  return (
    <div style={{
      margin: 'var(--sp-3) 0',
      padding: 'var(--sp-4)',
      background: 'linear-gradient(135deg, rgba(245,166,35,0.06) 0%, rgba(245,166,35,0.02) 100%)',
      border: '1px solid rgba(245,166,35,0.25)',
      borderRadius: 'var(--r-lg)',
      display: 'flex', flexDirection: 'column', gap: 'var(--sp-4)',
    }}>
      {/* Warning header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-2)' }}>
        <AlertTriangle size={16} color="var(--warning)" />
        <span style={{ fontWeight: 700, color: 'var(--warning)', fontSize: '0.88rem' }}>
          Confirm {intent || 'Data Modification'}
        </span>
      </div>

      <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
        The AI plans to execute the following SQL statement. Please review and confirm or cancel.
      </p>

      {/* SQL Plan */}
      {sql && (
        <div className="code-block" style={{ borderColor: 'rgba(245,166,35,0.2)', background: 'rgba(0,0,0,0.5)' }}>
          {sql}
        </div>
      )}

      {reason && (
        <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
          Reason: {reason}
        </p>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
        <button
          id="confirm-cancel-btn"
          className="btn btn-ghost"
          onClick={onCancel}
          disabled={confirming}
          style={{ color: 'var(--danger)', borderColor: 'rgba(255,91,107,0.25)' }}
        >
          <XCircle size={15} />
          Cancel
        </button>
        <button
          id="confirm-execute-btn"
          className="btn btn-success"
          onClick={onConfirm}
          disabled={confirming}
        >
          {confirming
            ? <><span className="spinner" style={{ width: 14, height: 14 }} />Executing…</>
            : <><CheckCircle2 size={15} />Confirm & Execute</>}
        </button>
      </div>
    </div>
  );
}
