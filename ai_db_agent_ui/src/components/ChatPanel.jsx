import { useEffect, useRef, useState, useCallback } from 'react';
import { Send, Bot, User, Table2, AlertCircle, CheckCircle2, Terminal, Clock, Copy, Check, Trash2, MessageSquare } from 'lucide-react';
import DataTable from './DataTable';
import ConfirmDialog from './ConfirmDialog';

/* ── helpers ──────────────────────────────────────────────────── */
function Timestamp({ ts }) {
  const d = new Date(ts);
  return (
    <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 3 }}>
      <Clock size={9} />
      {d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
    </span>
  );
}

function CopyBtn({ text }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };
  return (
    <button onClick={copy} title="Copy" style={{
      background: 'none', border: 'none', cursor: 'pointer',
      color: copied ? 'var(--success)' : 'var(--text-muted)',
      padding: '2px 6px', borderRadius: 'var(--r-sm)',
      display: 'flex', alignItems: 'center', gap: 4,
      fontSize: '0.7rem', fontWeight: 500, transition: 'color 0.2s',
    }}>
      {copied ? <Check size={12} /> : <Copy size={12} />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}

function SqlBlock({ sql }) {
  if (!sql) return null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 2 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <Terminal size={11} color="var(--text-muted)" />
          <span style={{ fontSize: '0.69rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.07em', fontWeight: 600 }}>SQL Executed</span>
        </div>
        <CopyBtn text={sql} />
      </div>
      <div className="code-block" style={{ fontSize: '0.78rem' }}>{sql}</div>
    </div>
  );
}

/* ── User Message ─────────────────────────────────────────────── */
function UserMessage({ msg }) {
  return (
    <div className="animate-fade-in-up" style={{ display: 'flex', gap: 'var(--sp-3)', justifyContent: 'flex-end' }}>
      <div style={{ maxWidth: '72%', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        <div style={{
          background: 'linear-gradient(135deg, var(--accent) 0%, var(--accent-dim) 100%)',
          color: '#fff', padding: '10px 16px',
          borderRadius: '16px 16px 4px 16px', fontSize: '0.88rem', lineHeight: 1.6,
          boxShadow: '0 2px 12px var(--accent-glow)', wordBreak: 'break-word',
        }}>
          {msg.text}
        </div>
        <Timestamp ts={msg.ts} />
      </div>
      <div style={{
        width: 32, height: 32, flexShrink: 0,
        background: 'linear-gradient(135deg, var(--accent) 0%, var(--accent-dim) 100%)',
        borderRadius: 'var(--r-full)', display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 2px 8px var(--accent-glow)',
      }}>
        <User size={15} color="#fff" />
      </div>
    </div>
  );
}

/* ── AI Message ───────────────────────────────────────────────── */
function AiMessage({ msg, onConfirm, onCancel, confirming }) {
  const hasTable   = msg.type === 'table'   && (msg.columns?.length || msg.rows?.length);
  const hasConfirm = msg.type === 'confirm' && onConfirm;
  const isError    = msg.type === 'error';
  const isSuccess  = msg.type === 'success';

  return (
    <div className="animate-fade-in-up" style={{ display: 'flex', gap: 'var(--sp-3)', alignItems: 'flex-start' }}>
      {/* Avatar */}
      <div style={{
        width: 32, height: 32, flexShrink: 0,
        background: 'linear-gradient(135deg, #7b5ea7 0%, var(--accent) 100%)',
        borderRadius: 'var(--r-full)', display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 2px 8px rgba(123,94,167,0.4)', marginTop: 2,
      }}>
        <Bot size={15} color="#fff" />
      </div>

      <div style={{ maxWidth: '82%', display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
        {/* Text bubble */}
        {msg.text && (
          <div style={{
            background: isError ? 'var(--danger-subtle)' : 'var(--bg-elevated)',
            border: `1px solid ${isError ? 'rgba(255,91,107,0.25)' : 'var(--border)'}`,
            padding: '10px 16px', borderRadius: '4px 16px 16px 16px',
            fontSize: '0.88rem', lineHeight: 1.7,
            color: isError ? 'var(--danger)' : 'var(--text-primary)',
            wordBreak: 'break-word',
          }}>
            {isError && <AlertCircle size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />}
            {msg.text}
          </div>
        )}

        {/* SQL block */}
        <SqlBlock sql={msg.sql} />

        {/* Data table */}
        {hasTable && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, paddingLeft: 2 }}>
              <Table2 size={11} color="var(--accent)" />
              <span style={{ fontSize: '0.69rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.07em', fontWeight: 600 }}>Result Set</span>
            </div>
            <DataTable columns={msg.columns} rows={msg.rows} />
          </div>
        )}

        {/* Success badge */}
        {isSuccess && (
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '5px 12px', borderRadius: 'var(--r-full)',
            background: 'var(--success-subtle)', border: '1px solid rgba(34,211,160,0.2)',
            color: 'var(--success)', fontSize: '0.78rem', fontWeight: 600,
          }}>
            <CheckCircle2 size={13} />
            {msg.affectedRows != null ? `${msg.affectedRows} row(s) affected` : 'Operation completed successfully'}
          </div>
        )}

        {/* Confirm dialog */}
        {hasConfirm && (
          <ConfirmDialog sqlPlan={msg.sqlPlan} onConfirm={onConfirm} onCancel={onCancel} confirming={confirming} />
        )}

        <Timestamp ts={msg.ts} />
      </div>
    </div>
  );
}

/* ── Typing indicator ─────────────────────────────────────────── */
function TypingIndicator() {
  return (
    <div className="animate-fade-in" style={{ display: 'flex', gap: 'var(--sp-3)', alignItems: 'center' }}>
      <div style={{
        width: 32, height: 32, flexShrink: 0,
        background: 'linear-gradient(135deg, #7b5ea7 0%, var(--accent) 100%)',
        borderRadius: 'var(--r-full)', display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Bot size={15} color="#fff" />
      </div>
      <div style={{
        background: 'var(--bg-elevated)', border: '1px solid var(--border)',
        borderRadius: '4px 16px 16px 16px', padding: '12px 18px',
        display: 'flex', gap: 5, alignItems: 'center',
      }}>
        {[0, 1, 2].map(i => (
          <span key={i} style={{
            width: 7, height: 7, borderRadius: '50%', background: 'var(--accent)',
            animation: `pulse-dot 1.2s ease-in-out ${i * 0.2}s infinite`,
            display: 'inline-block',
          }} />
        ))}
      </div>
    </div>
  );
}

/* ── Suggested prompts ────────────────────────────────────────── */
const SUGGESTIONS = [
  'Show all tables',
  'List the first 10 rows',
  'Count total records',
  'Describe the schema',
];

/* ── Main ChatPanel ───────────────────────────────────────────── */
export default function ChatPanel({ messages, onSend, loading, onConfirm, onCancel, confirming, pendingConfirm, onClearChat }) {
  const [input, setInput] = useState('');
  const bottomRef   = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const handleSend = useCallback(() => {
    const text = input.trim();
    if (!text || loading || pendingConfirm) return;
    setInput('');
    if (textareaRef.current) { textareaRef.current.style.height = 'auto'; }
    onSend(text);
  }, [input, loading, pendingConfirm, onSend]);

  const handleKey = e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
  };

  const lastAiIdx = messages.reduceRight((acc, m, i) => acc === -1 && m.role === 'ai' ? i : acc, -1);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Toolbar */}
      {messages.length > 0 && (
        <div style={{
          display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
          padding: '8px var(--sp-6) 0',
          gap: 'var(--sp-2)', flexShrink: 0,
        }}>
          <button
            id="clear-chat-btn"
            className="btn btn-ghost"
            onClick={onClearChat}
            style={{ fontSize: '0.75rem', padding: '5px 10px', gap: 4 }}
            title="Clear chat history"
          >
            <Trash2 size={12} /> Clear chat
          </button>
        </div>
      )}

      {/* Messages */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 'var(--sp-5) var(--sp-6)', display: 'flex', flexDirection: 'column', gap: 'var(--sp-5)' }}>
        {messages.length === 0 && (
          <div className="animate-fade-in" style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', height: '100%', gap: 'var(--sp-5)',
            color: 'var(--text-muted)', textAlign: 'center',
          }}>
            <div style={{
              width: 72, height: 72,
              background: 'linear-gradient(135deg, var(--accent-subtle) 0%, transparent 100%)',
              border: '1px solid var(--border)', borderRadius: 'var(--r-xl)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <MessageSquare size={30} color="var(--accent)" />
            </div>
            <div>
              <div style={{ fontWeight: 700, color: 'var(--text-secondary)', fontSize: '1rem', marginBottom: 8 }}>
                Ask your database anything
              </div>
              <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: 'var(--sp-5)' }}>
                Use natural language — the AI handles the SQL
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'center' }}>
                {SUGGESTIONS.map(s => (
                  <button key={s} onClick={() => onSend(s)} className="btn btn-ghost"
                    style={{ fontSize: '0.78rem', padding: '6px 14px' }}>
                    {s}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {messages.map((msg, i) =>
          msg.role === 'user'
            ? <UserMessage key={i} msg={msg} />
            : <AiMessage
                key={i} msg={msg}
                onConfirm={i === lastAiIdx && pendingConfirm ? onConfirm : undefined}
                onCancel={i === lastAiIdx && pendingConfirm ? onCancel : undefined}
                confirming={confirming}
              />
        )}

        {loading && <TypingIndicator />}
        <div ref={bottomRef} />
      </div>

      {/* Input bar */}
      <div style={{
        padding: 'var(--sp-3) var(--sp-6) var(--sp-4)',
        borderTop: '1px solid var(--border)',
        background: 'var(--bg-glass-heavy)',
        backdropFilter: 'blur(20px)',
        flexShrink: 0,
      }}>
        {pendingConfirm && (
          <div style={{
            marginBottom: 'var(--sp-3)', padding: '7px 12px',
            background: 'var(--warning-subtle)', border: '1px solid rgba(245,166,35,0.3)',
            borderRadius: 'var(--r-md)', fontSize: '0.78rem', color: 'var(--warning)',
            display: 'flex', alignItems: 'center', gap: 6,
          }}>
            <AlertCircle size={13} />
            Action required — confirm or cancel the SQL above before continuing
          </div>
        )}
        <div style={{ display: 'flex', gap: 'var(--sp-3)', alignItems: 'flex-end' }}>
          <textarea
            ref={textareaRef}
            id="chat-input"
            className="form-input"
            rows={1}
            value={input}
            onChange={e => {
              setInput(e.target.value);
              e.target.style.height = 'auto';
              e.target.style.height = Math.min(e.target.scrollHeight, 130) + 'px';
            }}
            onKeyDown={handleKey}
            placeholder={
              pendingConfirm
                ? 'Confirm the operation above first…'
                : 'Ask anything about your database…'
            }
            disabled={loading || pendingConfirm}
            style={{ resize: 'none', flex: 1, minHeight: 44, maxHeight: 130, overflow: 'auto', lineHeight: 1.55 }}
          />
          <button
            id="chat-send-btn"
            className="btn btn-primary btn-icon"
            onClick={handleSend}
            disabled={!input.trim() || loading || pendingConfirm}
            style={{ minWidth: 44, height: 44, flexShrink: 0 }}
            title="Send (Enter)"
          >
            {loading ? <span className="spinner" style={{ width: 15, height: 15 }} /> : <Send size={16} />}
          </button>
        </div>
        <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)', marginTop: 5, textAlign: 'right' }}>
          Enter to send · Shift+Enter for newline
        </div>
      </div>
    </div>
  );
}
