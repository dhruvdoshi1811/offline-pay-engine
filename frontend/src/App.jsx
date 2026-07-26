import { useEffect, useState } from 'react'
import { api } from './api'
import AuthPanel from './components/AuthPanel'
import DevicePanel from './components/DevicePanel'
import SendPaymentPanel from './components/SendPaymentPanel'
import WalletPanel from './components/WalletPanel'
import DemoPanel from './components/DemoPanel'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token'))
  const [me, setMe] = useState(null)
  const [myDevices, setMyDevices] = useState([])
  const [senderDeviceId, setSenderDeviceId] = useState('')
  const [refreshSignal, setRefreshSignal] = useState(0)

  useEffect(() => {
    if (!token) {
      setMe(null)
      setMyDevices([])
      return
    }
    localStorage.setItem('token', token)
    api.me(token).then(setMe).catch(() => {
      setToken(null)
      localStorage.removeItem('token')
    })
    api.listMyDevices(token).then((devices) => {
      setMyDevices(devices)
      if (devices.length > 0) {
        setSenderDeviceId((current) => current || devices[0].id)
      }
    })
  }, [token])

  function handleLogout() {
    setToken(null)
    setMe(null)
    setMyDevices([])
    setSenderDeviceId('')
    localStorage.removeItem('token')
  }

  function handleDeviceRegistered(device) {
    setMyDevices((current) => [...current, device])
    setSenderDeviceId((current) => current || device.id)
  }

  const bumpRefresh = () => setRefreshSignal((value) => value + 1)

  if (!token || !me) {
    return <AuthPanel onAuthenticated={setToken} />
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-100">Offline Payment Relay</h1>
          <p className="text-sm text-slate-400">
            {me.email} · {me.role}
          </p>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className="rounded border border-slate-600 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800"
        >
          Log out
        </button>
      </header>

      <DevicePanel token={token} devices={myDevices} onRegistered={handleDeviceRegistered} />

      {myDevices.length > 1 && (
        <label className="mt-4 flex flex-col text-xs text-slate-400">
          sending from
          <select
            value={senderDeviceId}
            onChange={(event) => setSenderDeviceId(event.target.value)}
            className="mt-1 rounded border border-slate-600 bg-slate-900 px-2 py-1.5 text-sm text-slate-100"
          >
            {myDevices.map((device) => (
              <option key={device.id} value={device.id}>
                {device.ownerName}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="mt-4">
        <SendPaymentPanel token={token} senderDeviceId={senderDeviceId} onSettled={bumpRefresh} />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        {myDevices.map((device) => (
          <WalletPanel
            key={device.walletId}
            token={token}
            label={`${device.ownerName} Wallet`}
            walletId={device.walletId}
            refreshSignal={refreshSignal}
          />
        ))}
      </div>

      <div className="mt-4">
        <DemoPanel token={token} senderDeviceId={senderDeviceId} onSettled={bumpRefresh} />
      </div>
    </div>
  )
}
