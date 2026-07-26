export async function generateDeviceKeyPair() {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: 'RSA-OAEP',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256',
    },
    true,
    ['encrypt', 'decrypt'],
  )

  const spki = await crypto.subtle.exportKey('spki', keyPair.publicKey)
  return bytesToBase64(new Uint8Array(spki))
}

function base64ToBytes(base64) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

function bytesToBase64(bytes) {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

// Mirrors PacketCryptoService server-side: AES-256-GCM for the payload,
// RSA-OAEP-SHA256 to wrap the session key with the server's public key.
export async function encryptPayload(serverPublicKeyBase64, amount) {
  const publicKey = await crypto.subtle.importKey(
    'spki',
    base64ToBytes(serverPublicKeyBase64),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )

  const sessionKey = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
  const nonce = crypto.getRandomValues(new Uint8Array(12))
  const plaintext = new TextEncoder().encode(JSON.stringify({ amount }))

  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, sessionKey, plaintext)
  const rawSessionKey = await crypto.subtle.exportKey('raw', sessionKey)
  const encryptedSessionKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawSessionKey)

  return {
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
    encryptedSessionKey: bytesToBase64(new Uint8Array(encryptedSessionKey)),
    nonce: bytesToBase64(nonce),
  }
}
