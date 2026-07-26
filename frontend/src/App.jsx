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
  const [senderDevice, setSenderDevice] = useState(null)
  const [receiverDevice, setReceiverDevice] = useState(null)
  const [refreshSignal, setRefreshSignal] = useState(0)

  useEffect(() => {
    if (!token) {
      setMe(null)
      return
    }
    localStorage.setItem('token', token)
    api.me(token).then(setMe).catch(() => {
      setToken(null)
      localStorage.removeItem('token')
    })
  }, [token])

  function handleLogout() {
    setToken(null)
    setMe(null)
    setSenderDevice(null)
    setReceiverDevice(null)
    localStorage.removeItem('token')
  }

  if (!token || !me) {
    return <AuthPanel onAuthenticated={setToken} />
  }

  const bumpRefresh = () => setRefreshSignal((value) => value + 1)

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

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <DevicePanel token={token} label="Sender Device" device={senderDevice} onRegistered={setSenderDevice} />
        <DevicePanel token={token} label="Receiver Device" device={receiverDevice} onRegistered={setReceiverDevice} />
      </div>

      <div className="mt-4">
        <SendPaymentPanel
          token={token}
          senderDeviceId={senderDevice?.id}
          receiverDeviceId={receiverDevice?.id}
          onSettled={bumpRefresh}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <WalletPanel
          token={token}
          label="Sender Wallet"
          walletId={senderDevice?.walletId}
          refreshSignal={refreshSignal}
        />
        <WalletPanel
          token={token}
          label="Receiver Wallet"
          walletId={receiverDevice?.walletId}
          refreshSignal={refreshSignal}
        />
      </div>

      <div className="mt-4">
        <DemoPanel
          token={token}
          senderDeviceId={senderDevice?.id}
          receiverDeviceId={receiverDevice?.id}
          onSettled={bumpRefresh}
        />
      </div>
    </div>
  )
}
