import { useState } from 'react'
import { api } from '../api'
import { generateDeviceKeyPair } from '../crypto'

export default function DevicePanel({ token, label, device, onRegistered }) {
  const [ownerName, setOwnerName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function handleRegister(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const publicKey = await generateDeviceKeyPair()
      const registered = await api.registerDevice(token, ownerName, publicKey)
      onRegistered(registered)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{label}</h2>

      {device ? (
        <div className="text-sm text-slate-300">
          <p className="font-medium text-slate-100">{device.ownerName}</p>
          <p className="mt-1 break-all text-slate-500">device: {device.id}</p>
          <p className="break-all text-slate-500">wallet: {device.walletId}</p>
        </div>
      ) : (
        <form onSubmit={handleRegister} className="flex flex-col gap-2">
          <input
            type="text"
            required
            placeholder="owner name"
            value={ownerName}
            onChange={(event) => setOwnerName(event.target.value)}
            className="rounded border border-slate-600 bg-slate-900 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500"
          />
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="rounded bg-slate-700 px-3 py-2 text-sm font-medium text-white hover:bg-slate-600 disabled:opacity-50"
          >
            {loading ? 'Generating keypair…' : 'Register device'}
          </button>
        </form>
      )}
    </div>
  )
}
