import { useState } from 'react'
import { api } from '../api'
import { generateDeviceKeyPair } from '../crypto'

export default function DevicePanel({ token, devices, onRegistered }) {
  const [ownerName, setOwnerName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [copiedId, setCopiedId] = useState(null)

  async function handleRegister(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const publicKey = await generateDeviceKeyPair()
      const registered = await api.registerDevice(token, ownerName, publicKey)
      onRegistered(registered)
      setOwnerName('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function copyId(id) {
    navigator.clipboard?.writeText(id)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 1500)
  }

  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">My Devices</h2>

      {devices.length === 0 && (
        <p className="mb-3 text-sm text-slate-500">You haven't registered a device yet.</p>
      )}

      <ul className="mb-3 space-y-2">
        {devices.map((device) => (
          <li key={device.id} className="rounded border border-slate-700 bg-slate-900 p-2 text-sm">
            <p className="font-medium text-slate-100">{device.ownerName}</p>
            <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
              <span className="break-all">{device.id}</span>
              <button
                type="button"
                onClick={() => copyId(device.id)}
                className="shrink-0 rounded border border-slate-600 px-1.5 py-0.5 text-slate-300 hover:bg-slate-700"
              >
                {copiedId === device.id ? 'Copied' : 'Copy'}
              </button>
            </div>
          </li>
        ))}
      </ul>

      <form onSubmit={handleRegister} className="flex flex-col gap-2">
        <input
          type="text"
          required
          placeholder="device name"
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
          {loading ? 'Generating keypair…' : 'Register another device'}
        </button>
      </form>
    </div>
  )
}
