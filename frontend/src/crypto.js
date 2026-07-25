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
  const bytes = new Uint8Array(spki)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }

  return btoa(binary)
}
