# Offline Capable Payment Relay

A Spring Boot backend (plus a small React frontend) for payments made under intermittent or no connectivity. A payment packet gets relayed from device to device across a simulated mesh network until it reaches a node with real connectivity, and only then does it settle against a wallet.

The project exists to work through two problems that are genuinely hard, not just CRUD with extra steps:

1. Securing a payment payload from the people carrying it. In a mesh relay, the devices forwarding your packet are not trusted. They should never be able to read or tamper with the amount inside.
2. Guaranteeing a packet settles exactly once even when it arrives through several different relay paths at the same time, which is the normal case in a real mesh network, not an edge case.

Demo walkthrough (PDF): [DEMO_Offline_Pay.pdf](./DEMO_Offline_Pay.pdf)

## What it actually does

A user registers one or more devices (each gets its own wallet and RSA keypair generated in the browser). To send money, you pick one of your own devices as the sender, type in the recipient's device ID (the same way you'd type in a bank account number or a UPI handle), look them up to confirm you have the right person, then send. The amount gets encrypted client side, relayed to the server, decrypted, and settled: the sender's wallet is debited and the receiver's is credited in one atomic step.

There's also a concurrency demo built in. It takes one payment packet and fires it through several relay paths at once, using real concurrent threads, not a loop. Exactly one of those attempts settles. The rest get rejected as duplicates. You can point at the relay log and the packet's final status as proof, and the wallet balance only ever moves once no matter how many paths raced for it.

## Architecture

Backend: Java 17, Spring Boot 4, Spring Security with JWT, Spring Data JPA, PostgreSQL in production and H2 for local development and tests, Flyway for migrations, Maven for the build.

Frontend: React with Vite, Tailwind CSS. No router, since the whole flow is one linear page.

Deployment: a multi stage Dockerfile (build with a full JDK, run on a slim JRE, non root user) and a Render blueprint. A docker compose file wires the app up against a real Postgres container for local verification, separate from the day to day H2 dev loop.

The backend is layered the usual way: controller, service, repository, entity, with a dedicated `crypto` package for anything touching encryption and a `security` package for JWT handling. One convention worth calling out: entities reference each other by plain UUID fields, not JPA relationships. A `Wallet` has a `deviceId`, a `PaymentPacket` has `senderDeviceId` and `receiverDeviceId`, and so on. Nothing is `@ManyToOne`. Foreign key constraints still exist at the database level for integrity, but the object graph in Java stays flat. This keeps every table's rows independently reasoned about, which matters a lot once you get to the concurrency work below.

### Domain model

| Entity | Purpose |
|---|---|
| `User` | Login identity for the API. Has a role, `USER` or `ADMIN`. |
| `Device` | A registered device, owned by a user, with its own RSA public key and its own wallet. |
| `Wallet` | Balance for one device. Optimistic locking via `@Version`. |
| `PaymentPacket` | One encrypted payment attempt: ciphertext, wrapped session key, nonce, a hash of the ciphertext, a status. |
| `RelayLog` | One row per simulated delivery attempt for a packet, proving how many paths it actually arrived through. |
| `SettlementLedgerEntry` | Append only record of a settlement: amount, resulting balance, timestamp. Never edited. |

## How the encryption works

Every payment packet is protected with hybrid encryption: a fast symmetric cipher for the actual data, and a slower asymmetric cipher just to protect the symmetric key. This is the same pattern TLS and most real messaging protocols use, and for the same reason: RSA is too slow and too limited in message size to encrypt arbitrary payloads directly, but it's a good fit for wrapping a small one time key.

The server holds its own RSA 2048 keypair, generated once when it starts up and kept only in memory. This was a deliberate choice over generating a keypair per device: it means the server is the only party that can ever decrypt a packet, a relay intermediary carrying the packet through the mesh never has anything close to the private key, and there's no key material of any kind travelling over the wire in a request. A sender fetches the server's public key from `/crypto/public-key` before encrypting anything.

Sending a payment, step by step:

1. The client builds the plaintext payload, which is just `{"amount": 25.50}`.
2. It generates a random AES-256 key and a random 12 byte nonce, and encrypts the payload with AES-GCM. GCM gives you authenticated encryption for free: the ciphertext comes with a tag baked in, so if a single byte is tampered with anywhere along the relay, decryption fails outright instead of quietly returning garbage.
3. It wraps that one time AES key with RSA-OAEP using SHA-256, encrypting it against the server's public key.
4. The ciphertext, the wrapped key, and the nonce all get base64 encoded and sent to `/packets/relay` along with the sender and receiver device IDs and a timestamp.
5. The server does the reverse: RSA-OAEP decrypts the wrapped key with its private key, then AES-GCM decrypts the payload with that key and the nonce. If the tag check fails, that request never gets past decryption. No amount is ever recoverable, no wallet is touched.

The browser side of this uses the Web Crypto API directly (`crypto.subtle`), and it produces byte for byte compatible output with the Java side (`javax.crypto`). That took a bit of care: Java's shorthand RSA transformation string defaults the mask generation function to SHA-1 even when the main digest is SHA-256, so the backend builds an explicit `OAEPParameterSpec` pinning both to SHA-256, matching what Web Crypto does automatically.

The `ciphertextHash` stored on every packet is a SHA-256 hash of the ciphertext bytes, computed by the server, never trusted from the client. That single value is what the next section is built around.

## Exactly once settlement

Two failure modes have to be ruled out for a payment relay to be trustworthy: a stale, resent packet settling long after it should have expired, and the same packet settling twice because it arrived through more than one path.

Freshness is the simpler of the two. Every packet carries a timestamp from when it was created, and the server rejects anything outside a configurable window (five minutes by default) regardless of whether it's otherwise a brand new packet. An attacker who captures a packet and resends it later gets nothing.

Duplicate delivery is handled with a different mechanism on purpose, because it's a different kind of problem. A `UNIQUE` constraint on `ciphertext_hash` is the actual claim: the very first thing that happens when a packet arrives is an attempt to insert a new row with that hash. If it succeeds, this arrival is the one that gets to proceed. If the database rejects it with a constraint violation, some other arrival already claimed it, and this one is logged and rejected immediately, no decryption, no settlement attempt. This is worth contrasting with the `@Version` optimistic locking used on `Wallet`: locking coordinates safe access to a row that's being modified, while the unique constraint here coordinates first time acceptance of an event that hasn't been processed at all yet. Same underlying goal of correctness under concurrency, different mechanism because the shape of the problem is different.

The claim step and any later rejection (stale timestamp, insufficient balance) run in their own independent transactions, committing separately from the rest of the request. That's not incidental. If decryption or settlement fails after a packet has already been claimed, the claim itself must still stick, otherwise resubmitting the exact same bad packet would look brand new every time and the whole guarantee falls apart.

There's a concurrency test that proves this holds under real racing, not just sequential calls, by firing several genuine threads at the same packet through a demo endpoint and asserting only one settles. And it's mutation tested in the literal sense: at one point during development the unique constraint was deliberately removed, the test was rerun, and it failed exactly as expected, with several attempts settling instead of one. The constraint was then restored and the test went back to green. Proving a test can fail is the only way to trust that it's actually checking something.

## Settlement itself

When a packet is genuinely new and still fresh after decryption, settlement debits the sender's wallet, checks first that the balance is sufficient, and credits the receiver, both in the same transaction. If the sender doesn't have enough, the packet is rejected with its own status and nothing about either wallet changes. Only one thread per packet ever reaches this step at all, since the claim mechanism above already guarantees that, so this doesn't introduce a second concurrency problem to solve. A `SettlementLedgerEntry` gets written alongside the credit, giving an append only audit trail of every amount that ever settled and the balance right after.

## Devices, accounts, and why that changed partway through

Early on, devices were intentionally kept separate from user accounts, matching the idea of a device as a mesh network identity rather than something tied to a login. That held up fine for the backend in isolation, but it broke down the moment the frontend needed to be used the way a real app is used: logging out and back in lost track of which device was yours, because nothing in the data model said a device belonged to anyone. Devices now belong to the account that registered them. Listing your own devices is scoped to your account, but looking up a specific device by ID stays open to anyone signed in, since you need to be able to confirm a recipient's identity before paying them, the same as looking up someone's account number.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | Create an account. |
| POST | `/auth/login` | Get a JWT. |
| GET | `/auth/me` | Current user. |
| GET | `/crypto/public-key` | The server's RSA public key. No auth required, public keys aren't secret. |
| POST | `/devices` | Register a device under your account. |
| GET | `/devices/mine` | Your own devices. |
| GET | `/devices/{id}` | Look up any device by ID. |
| GET | `/wallets/{id}` | Balance. |
| POST | `/wallets/{id}/fund` | Seed a balance. Admin only, meant for demo setup, not the payment path. |
| GET | `/wallets/{id}/ledger` | Settlement history for a wallet. |
| POST | `/packets/relay` | Submit an encrypted packet. This is the actual payment mechanism. |
| GET | `/packets/{id}` | Packet status. |
| GET | `/packets?deviceId=` | A device's packet history. |
| POST | `/demo/simulate-duplicate-delivery` | Fires one packet through several concurrent relay paths. |
| GET | `/admin/rejected-packets` | Every rejected packet, grouped by why. Admin only. |

## Running it locally

Backend, from the project root:

```
mvnw spring-boot:run
```

Uses an H2 file database by default, no setup required. Frontend, from `frontend/`:

```
npm install
npm run dev
```

Defaults to talking to `http://localhost:8080`, configurable through `frontend/.env`.

An admin account is seeded automatically on first startup so there's a way to reach the admin only endpoints without touching the database directly. Configurable through `ADMIN_EMAIL` and `ADMIN_PASSWORD`, with local development defaults if you don't set them.

## Running against real Postgres

```
docker compose up
```

Builds the app image and starts a real Postgres container alongside it, so the Flyway migrations run against actual Postgres rather than H2's compatibility mode. Useful for confirming schema changes before deploying for real.

## Tests

```
mvnw test
```

Covers the crypto primitives in isolation, the full request flow through every layer, and the concurrency behavior described above with real threads. The frontend build is checked with `npm run build`.
