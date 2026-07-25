import { useState } from 'react'
import { api } from '../api'

export default function AuthPanel({ onAuthenticated }) {
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const response = mode === 'login'
        ? await api.login(email, password)
        : await api.register(email, password)
      onAuthenticated(response.token)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mx-auto mt-24 max-w-sm rounded-lg border border-slate-700 bg-slate-800 p-6">
      <h1 className="mb-1 text-xl font-semibold text-slate-100">Offline Payment Relay</h1>
      <p className="mb-6 text-sm text-slate-400">
        {mode === 'login' ? 'Log in to continue' : 'Create an account'}
      </p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <input
          type="email"
          required
          placeholder="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className="rounded border border-slate-600 bg-slate-900 px-3 py-2 text-slate-100 placeholder:text-slate-500"
        />
        <input
          type="password"
          required
          minLength={8}
          placeholder="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className="rounded border border-slate-600 bg-slate-900 px-3 py-2 text-slate-100 placeholder:text-slate-500"
        />

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={loading}
          className="rounded bg-indigo-600 px-3 py-2 font-medium text-white hover:bg-indigo-500 disabled:opacity-50"
        >
          {loading ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Register'}
        </button>
      </form>

      <button
        type="button"
        onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
        className="mt-4 text-sm text-indigo-400 hover:underline"
      >
        {mode === 'login' ? "Need an account? Register" : 'Already have an account? Log in'}
      </button>
    </div>
  )
}
