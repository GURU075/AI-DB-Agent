import { useState, useCallback } from 'react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import ConnectPanel from './components/ConnectPanel';
import ChatPanel from './components/ChatPanel';
import { ToastContainer } from './components/Toast';
import { toast } from './services/toast';
import { connectToDb, disconnectDb, sendMessage, confirmSql, getSchema, clearChatHistory } from './services/api';
import './index.css';

/* ── Schema normaliser ─────────────────────────────────────── */
function normalizeSchema(raw) {
  if (!raw) return null;
  if (Array.isArray(raw)) return raw;
  if (raw.schema) return normalizeSchema(raw.schema);
  if (Array.isArray(raw.tables)) return raw.tables;
  return null;
}

/* ── Response parser ────────────────────────────────────────── */
function parseAiResponse(raw) {
  if (!raw) return { type: 'text', text: 'No response received.' };

  if (typeof raw === 'string') {
    return { type: 'text', text: raw };
  }

  const rawType = (raw.type || '').toString().toLowerCase();
  const plan = raw.sqlPlan || null;

  if (rawType === 'error' || raw.errorMessage) {
    return { type: 'error', text: raw.errorMessage || raw.message || 'An error occurred.' };
  }

  if (rawType === 'confirmation_required' || raw.pendingConfirmation === true) {
    return {
      type: 'confirm',
      text: raw.answer || 'Review and confirm the generated SQL operation.',
      sqlPlan: plan,
      sql: plan?.sql || '',
      operationId: raw.operationId,
    };
  }

  if (rawType === 'execution_result') {
    return {
      type: 'success',
      text: raw.answer || 'Operation completed successfully.',
      sql: plan?.sql || '',
      affectedRows: raw.affectedRows ?? null,
    };
  }

  if (rawType === 'query_result') {
    return {
      type: 'table',
      text: raw.answer || '',
      sql: plan?.sql || '',
      columns: raw.columns || [],
      rows: raw.rows || [],
    };
  }

  if (rawType === 'answer') {
    return { type: 'text', text: raw.answer || 'No response received.' };
  }

  // SELECT / table result
  if (
    rawType.includes('select') ||
    rawType.includes('table') ||
    rawType.includes('result') ||
    raw.columns != null ||
    raw.rows    != null ||
    raw.data    != null
  ) {
    return {
      type: 'table',
      text: raw.answer || raw.message || raw.text || '',
      sql:  plan?.sql || raw.sql || raw.executedSql || raw.query || '',
      columns: raw.columns || [],
      rows:    raw.rows || raw.data || [],
    };
  }

  // Confirmation required (write operation)
  if (
    rawType.includes('confirm') ||
    rawType.includes('pending') ||
    raw.requiresConfirmation === true ||
    raw.sqlPlan != null ||
    raw.plan    != null
  ) {
    return {
      type: 'confirm',
      text: raw.answer || raw.message || raw.text || 'Please review and confirm the following SQL operation:',
      sqlPlan: plan || raw.sql || raw.plan || '',
      sql: plan?.sql || raw.sql || '',
      operationId: raw.operationId,
    };
  }

  // Success / affected rows
  if (
    rawType.includes('success') ||
    rawType.includes('executed') ||
    rawType.includes('done') ||
    raw.success === true ||
    raw.affectedRows != null ||
    raw.rowsAffected != null
  ) {
    return {
      type: 'success',
      text: raw.answer || raw.message || raw.text || 'Operation completed successfully.',
      sql:  plan?.sql || raw.sql || raw.executedSql || '',
      affectedRows: raw.affectedRows ?? raw.rowsAffected ?? null,
    };
  }

  // Error
  if (rawType.includes('error') || raw.error != null || raw.errorMessage != null) {
    return { type: 'error', text: raw.message || raw.error || raw.errorMessage || 'An error occurred.' };
  }

  // Fallback: plain text
  return {
    type: 'text',
    text: raw.answer || raw.message || raw.text || JSON.stringify(raw, null, 2),
  };
}

/* ── App ────────────────────────────────────────────────────── */
export default function App() {
  const [connected,      setConnected]      = useState(false);
  const [sessionId,      setSessionId]      = useState(null);
  const [connectionInfo, setConnectionInfo] = useState(null);
  const [schema,         setSchema]         = useState(null);
  const [messages,       setMessages]       = useState([]);
  const [connecting,     setConnecting]     = useState(false);
  const [disconnecting,  setDisconnecting]  = useState(false);
  const [chatLoading,    setChatLoading]    = useState(false);
  const [confirming,     setConfirming]     = useState(false);
  const [pendingOperationId, setPendingOperationId] = useState(null);
  const [refreshingSchema, setRefreshingSchema] = useState(false);

  const addMsg = useCallback((role, payload) => {
    setMessages(prev => [...prev, { role, ts: Date.now(), ...payload }]);
  }, []);

  /* ── Connect ── */
  const handleConnect = useCallback(async (formData) => {
    setConnecting(true);
    try {
      const res = await connectToDb(formData);
      const sid = res?.sessionId || res?.session_id || res?.id;
      if (!sid) throw new Error('Server did not return a session ID.');

      setSessionId(sid);
      setConnectionInfo({ ...formData });
      setSchema(normalizeSchema(res?.schema || res?.tables || res?.schemaInfo));
      setMessages([]);
      setConnected(true);
      toast('success', 'Connected', `${formData.dbType} · ${formData.host}:${formData.port}`);
    } catch (err) {
      toast('error', 'Connection failed', err.message);
      throw err; // re-throw so ConnectPanel shows inline error
    } finally {
      setConnecting(false);
    }
  }, []);

  /* ── Disconnect ── */
  const handleDisconnect = useCallback(async () => {
    setDisconnecting(true);
    try {
      if (sessionId) await disconnectDb(sessionId);
    } catch { /* session cleanup is best effort during disconnect */ }
    setConnected(false);
    setSessionId(null);
    setConnectionInfo(null);
    setSchema(null);
    setMessages([]);
    setPendingOperationId(null);
    setDisconnecting(false);
    toast('info', 'Disconnected', 'Session ended');
  }, [sessionId]);

  /* ── Chat ── */
  const handleSend = useCallback(async (text) => {
    addMsg('user', { text });
    setChatLoading(true);
    try {
      const raw = await sendMessage(sessionId, text);
      const parsed = parseAiResponse(raw);
      addMsg('ai', parsed);
      if (parsed.type === 'confirm') {
        if (!parsed.operationId) throw new Error('Server did not return a confirmation operation ID.');
        setPendingOperationId(parsed.operationId);
        toast('warning', 'Confirmation required', 'Review the SQL plan below before executing');
      }
    } catch (err) {
      addMsg('ai', { type: 'error', text: err.message || 'Failed to get a response.' });
      toast('error', 'Request failed', err.message);
    } finally {
      setChatLoading(false);
    }
  }, [sessionId, addMsg]);

  /* ── Confirm SQL ── */
  const handleConfirm = useCallback(async () => {
    setConfirming(true);
    try {
      const raw = await confirmSql(sessionId, pendingOperationId, true);
      const parsed = parseAiResponse(raw);
      addMsg('ai', {
        ...parsed,
        type: ['text', 'confirm'].includes(parsed.type) ? 'success' : parsed.type,
      });
      setPendingOperationId(null);
      toast('success', 'SQL executed', parsed.affectedRows != null ? `${parsed.affectedRows} row(s) affected` : 'Done');
    } catch (err) {
      addMsg('ai', { type: 'error', text: err.message || 'Execution failed.' });
      setPendingOperationId(null);
      toast('error', 'Execution failed', err.message);
    } finally {
      setConfirming(false);
    }
  }, [sessionId, pendingOperationId, addMsg]);

  /* ── Cancel SQL ── */
  const handleCancel = useCallback(async () => {
    setConfirming(true);
    try {
      const raw = await confirmSql(sessionId, pendingOperationId, false);
      const parsed = parseAiResponse(raw);
      addMsg('ai', { type: 'text', text: parsed.text || 'Operation cancelled.' });
      setPendingOperationId(null);
      toast('info', 'Cancelled', 'The SQL operation was not executed');
    } catch (err) {
      addMsg('ai', { type: 'error', text: err.message });
      setPendingOperationId(null);
    } finally {
      setConfirming(false);
    }
  }, [sessionId, pendingOperationId, addMsg]);

  /* ── Refresh schema ── */
  const handleRefreshSchema = useCallback(async () => {
    if (!sessionId || refreshingSchema) return;
    setRefreshingSchema(true);
    try {
      const res = await getSchema(sessionId);
      setSchema(normalizeSchema(res?.schema || res?.tables || res));
      toast('success', 'Schema refreshed', '');
    } catch (err) {
      toast('warning', 'Schema refresh failed', err.message);
    } finally {
      setRefreshingSchema(false);
    }
  }, [sessionId, refreshingSchema]);

  /* ── Clear chat ── */
  const handleClearChat = useCallback(async () => {
    try {
      await clearChatHistory(sessionId);
      setMessages([]);
      setPendingOperationId(null);
      toast('info', 'Chat cleared', 'Browser and server history were cleared');
    } catch (err) {
      toast('error', 'Unable to clear chat', err.message);
    }
  }, [sessionId]);

  return (
    <>
      <div className="app-shell">
        <Header
          connected={connected}
          onDisconnect={handleDisconnect}
          disconnecting={disconnecting}
        />
        <div className="app-body">
          <Sidebar
            connected={connected}
            connectionInfo={connectionInfo}
            sessionId={sessionId}
            schema={schema}
            onRefreshSchema={handleRefreshSchema}
            refreshingSchema={refreshingSchema}
          />
          <main className="main-content">
            {!connected
              ? <ConnectPanel onConnect={handleConnect} loading={connecting} />
              : <ChatPanel
                  messages={messages}
                  onSend={handleSend}
                  loading={chatLoading}
                  onConfirm={handleConfirm}
                  onCancel={handleCancel}
                  confirming={confirming}
                  pendingConfirm={Boolean(pendingOperationId)}
                  onClearChat={handleClearChat}
                />
            }
          </main>
        </div>
      </div>
      <ToastContainer />
    </>
  );
}
