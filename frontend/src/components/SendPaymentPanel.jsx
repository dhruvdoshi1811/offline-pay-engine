import { useState } from 'react'
import { api } from '../api'
import { encryptPayload } from '../crypto'

export default function SendPaymentPanel({ token, senderDeviceId, onSettled }) {
  const [recipientId, setRecipientId] = useState('')
  const [recipient, setRecipient] = useState(null)
  const [lookupError, setLookupError] = useState(null)
  const [lookingUp, setLookingUp] = useState(false)

  const [amount, setAmount] = useState('25.00')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  function handleRecipientIdChange(value) {
    setRecipientId(value)
    setRecipient(null)
    setLookupError(null)
  }

  async function handleLookup() {
    setLookupError(null)
    setLookingUp(true)
    try {
      const device = await api.getDevice(token, recipientId)
      setRecipient(device)
    } catch (err) {
      setLookupError('No device found with that ID')
    } finally {
      setLookingUp(false)
    }
  }

  async function handleSend(event) {
    event.preventDefault()
    setError(null)
    setResult(null)
    setLoading(true)
    try {
      const { publicKey } = await api.getServerPublicKey()
      const encrypted = await encryptPayload(publicKey, Number(amount))
      const response = await api.relayPacket(
        token,
        senderDeviceId,
        recipient.id,
        encrypted,
        new Date().toISOString(),
        'browser-send',
      )
      setResult(response)
      onSettled?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  if (!senderDeviceId) {
    return (
      <div className="rounded-lg border border-indigo-700 bg-indigo-950/40 p-4 text-sm text-slate-500">
        Register a device of your own first.
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-indigo-700 bg-indigo-950/40 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-indigo-300">Send Payment</h2>

      <label className="flex flex-col text-xs text-slate-400">
        recipient device ID
        <div className="mt-1 flex gap-2">
          <input
            type="text"
            placeholder="paste the recipient's device ID"
            value={recipientId}
            onChange={(event) => handleRecipientIdChange(event.target.value)}
            className="flex-1 rounded border border-slate-600 bg-slate-900 px-2 py-1.5 text-sm text-slate-100"
          />
          <button
            type="button"
            onClick={handleLookup}
            disabled={!recipientId || lookingUp}
            className="rounded border border-slate-600 px-3 py-1.5 text-sm text-slate-200 hover:bg-slate-800 disabled:opacity-50"
          >
            {lookingUp ? 'Looking up…' : 'Look up'}
          </button>
        </div>
      </label>

      {lookupError && <p className="mt-1 text-sm text-red-400">{lookupError}</p>}
      {recipient && <p className="mt-1 text-sm text-emerald-400">Sending to: {recipient.ownerName}</p>}

      {recipient && (
        <form onSubmit={handleSend} className="mt-3 flex flex-wrap items-end gap-3">
          <label className="flex flex-col text-xs text-slate-400">
            amount
            <input
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              className="mt-1 w-28 rounded border border-slate-600 bg-slate-900 px-2 py-1.5 text-sm text-slate-100"
            />
          </label>
          <button
            type="submit"
            disabled={loading}
            className="rounded bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-50"
          >
            {loading ? 'Encrypting and sending…' : 'Send'}
          </button>
        </form>
      )}

      {error && <p className="mt-3 text-sm text-red-400">{error}</p>}

      {result && (
        <p className="mt-3 text-sm text-emerald-400">
          Sent ${result.decryptedAmount} — packet {result.status.toLowerCase()}.
        </p>
      )}
    </div>
  )
}
