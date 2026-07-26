import { useState } from 'react'
import { api } from '../api'

export default function DemoPanel({ token, senderDeviceId, onSettled }) {
  const [recipientId, setRecipientId] = useState('')
  const [recipient, setRecipient] = useState(null)
  const [lookupError, setLookupError] = useState(null)
  const [lookingUp, setLookingUp] = useState(false)

  const [amount, setAmount] = useState('10.00')
  const [concurrentPaths, setConcurrentPaths] = useState(5)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

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

  async function handleTrigger(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    setResult(null)
    try {
      const response = await api.simulateDuplicateDelivery(
        token,
        senderDeviceId,
        recipient.id,
        Number(amount),
        Number(concurrentPaths),
      )
      setResult(response)
      onSettled?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800 p-4">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
        Concurrency Proof — Exactly-Once Settlement
      </h2>
      <p className="mb-3 text-xs text-slate-500">
        Not a payment action — this fires the same encrypted packet through several simulated relay
        paths at once, to prove only one of them ever settles.
      </p>

      {!senderDeviceId && (
        <p className="text-sm text-slate-500">Register a device of your own first.</p>
      )}

      {senderDeviceId && (
        <>
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
          {recipient && <p className="mt-1 text-sm text-emerald-400">Target: {recipient.ownerName}</p>}
        </>
      )}

      {recipient && (
        <form onSubmit={handleTrigger} className="mt-3 flex flex-wrap items-end gap-3">
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
          <label className="flex flex-col text-xs text-slate-400">
            concurrent paths
            <input
              type="number"
              min="2"
              max="20"
              value={concurrentPaths}
              onChange={(event) => setConcurrentPaths(event.target.value)}
              className="mt-1 w-24 rounded border border-slate-600 bg-slate-900 px-2 py-1.5 text-sm text-slate-100"
            />
          </label>
          <button
            type="submit"
            disabled={loading}
            className="rounded bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-500 disabled:opacity-50"
          >
            {loading ? 'Firing concurrent paths…' : 'Simulate duplicate delivery'}
          </button>
        </form>
      )}

      {error && <p className="mt-3 text-sm text-red-400">{error}</p>}

      {result && (
        <div className="mt-4">
          <p className="mb-2 text-sm text-slate-300">
            {result.totalPaths} paths delivered, {result.settledCount} settled, {result.rejectedCount} blocked
          </p>
          <div className="flex flex-wrap gap-2">
            {result.outcomes.map((outcome) => {
              const settled = outcome.status === 'SETTLED'
              return (
                <div
                  key={outcome.relayPathId}
                  className={`rounded border px-3 py-2 text-xs ${
                    settled
                      ? 'border-emerald-600 bg-emerald-900/40 text-emerald-300'
                      : 'border-red-700 bg-red-900/30 text-red-300'
                  }`}
                >
                  <p className="font-medium">{outcome.relayPathId}</p>
                  <p>{settled ? 'SETTLED' : 'BLOCKED'}</p>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
