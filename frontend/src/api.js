const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function request(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const error = new Error(data?.message || `request failed with status ${response.status}`)
    error.status = response.status
    throw error
  }

  return data
}

export const api = {
  register: (email, password) => request('/auth/register', { method: 'POST', body: { email, password } }),
  login: (email, password) => request('/auth/login', { method: 'POST', body: { email, password } }),
  me: (token) => request('/auth/me', { token }),
  getServerPublicKey: () => request('/crypto/public-key'),
  registerDevice: (token, ownerName, publicKey) =>
    request('/devices', { method: 'POST', body: { ownerName, publicKey }, token }),
  listMyDevices: (token) => request('/devices/mine', { token }),
  getDevice: (token, id) => request(`/devices/${id}`, { token }),
  getWallet: (token, id) => request(`/wallets/${id}`, { token }),
  getLedger: (token, id) => request(`/wallets/${id}/ledger`, { token }),
  fundWallet: (token, id, amount) =>
    request(`/wallets/${id}/fund`, { method: 'POST', body: { amount }, token }),
  relayPacket: (token, senderDeviceId, receiverDeviceId, encrypted, packetTimestamp, relayPathId) =>
    request('/packets/relay', {
      method: 'POST',
      body: {
        senderDeviceId,
        receiverDeviceId,
        ciphertext: encrypted.ciphertext,
        encryptedSessionKey: encrypted.encryptedSessionKey,
        nonce: encrypted.nonce,
        packetTimestamp,
        relayPathId,
      },
      token,
    }),
  simulateDuplicateDelivery: (token, senderDeviceId, receiverDeviceId, amount, concurrentPaths) =>
    request('/demo/simulate-duplicate-delivery', {
      method: 'POST',
      body: { senderDeviceId, receiverDeviceId, amount, concurrentPaths },
      token,
    }),
}
