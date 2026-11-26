const BASE_URL = '/api/v1';
const REQUEST_TIMEOUT_MS = 60_000;

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
    });

    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }

    if (!response.ok) {
      const message = data?.message || data?.errorMessage || data?.error
        || `HTTP ${response.status}: ${response.statusText}`;
      throw new Error(message);
    }
    return data;
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('The server did not respond before the request timed out.');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function connectToDb(config) {
  return request('/connections', {
    method: 'POST',
    body: JSON.stringify({ ...config, port: Number(config.port) }),
  });
}

export function disconnectDb(sessionId) {
  return request(`/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });
}

export function sendMessage(sessionId, userMessage) {
  return request('/chat', {
    method: 'POST',
    body: JSON.stringify({ sessionId, userMessage }),
  });
}

export function confirmSql(sessionId, operationId, confirm) {
  return request('/operations/confirm', {
    method: 'POST',
    body: JSON.stringify({ sessionId, operationId, confirm }),
  });
}

export function getSchema(sessionId) {
  return request(`/sessions/${encodeURIComponent(sessionId)}/schema`);
}

export function clearChatHistory(sessionId) {
  return request(`/sessions/${encodeURIComponent(sessionId)}/history`, { method: 'DELETE' });
}
