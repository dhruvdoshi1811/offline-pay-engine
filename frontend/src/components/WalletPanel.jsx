import { useEffect, useState } from 'react'
import { api } from '../api'

export default function WalletPanel({ token, label, walletId, refreshSignal }) {
  const [wallet, setWallet] = useState(null)
  const [ledger, setLedger] = useState([])
  const [amount, setAmount] = useState('100.00')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function refresh() {
    const [walletData, ledgerData] = await Promise.all([
      api.getWallet(token, walletId),
      api.getLedger(token, walletId),
    ])
    setWallet(walletData)
    setLedger(ledgerData)
  }

  useEffect(() => {
    if (walletId) {
      refresh()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [walletId, refreshSignal])

  async function handleFund(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await api.fundWallet(token, walletId, Number(amount))
      await refresh()
    } catch (err) {
      if (err.status === 403) {
        setError('Admin role required — log in as the seeded admin account to seed a test balance.')
      } else {
        setError(err.message)
      }
    } finally {
      setLoading(false)
    }
  }

  if (!walletId) {
    return (
      <div className="rounded-lg border border-slate-700 bg-slate-800 p-4 text-sm text-slate-500">
        Register the {label.toLowerCase()} to see its wallet.
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800 p-4">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{label}</h2>

      <p className="text-2xl font-semibold text-slate-100">
        {wallet ? `$${wallet.balance}` : '—'}
      </p>

      <form onSubmit={handleFund} className="mt-4 flex flex-wrap items-center gap-2">
        <input
          type="number"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          className="w-28 rounded border border-slate-600 bg-slate-900 px-2 py-1.5 text-sm text-slate-100"
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded bg-slate-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-600 disabled:opacity-50"
        >
          Seed test balance (admin)
        </button>
      </form>

      {error && <p className="mt-2 text-sm text-red-400">{error}</p>}

      {ledger.length > 0 && (
        <div className="mt-4">
          <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Ledger</h3>
          <ul className="space-y-1 text-xs text-slate-400">
            {ledger.map((entry) => (
              <li key={entry.id}>
                {entry.amount} → balance {entry.balanceAfter} ({new Date(entry.settledAt).toLocaleTimeString()})
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
