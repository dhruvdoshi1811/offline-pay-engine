# Offline-Capable Payment Relay — Build Spec

A Spring Boot backend for payments made under intermittent/no connectivity — a payment packet gets relayed device-to-device (simulated mesh delivery) until it reaches a node with connectivity, then settles against a wallet. The core hard problems: securing the payload from untrusted relay intermediaries, and guaranteeing exactly-once settlement even when the same packet arrives via multiple relay paths (which is expected, not an edge case, in real mesh delivery).

**Note:** concept inspired by a permissively-licensed reference repo, rebuilt from scratch in Spring Boot with original naming and this project's own conventions — no code reused. Framed generically (not India/UPI-specific) since this needs to read clearly to a US audience.

---

## 1. Tech Stack

- **Backend:** Java 17, Spring Boot 4, Spring Data JPA, Spring Security (JWT)
- **DB:** PostgreSQL (H2 for local/test)
- **Crypto:** Java's built-in `javax.crypto` (AES-GCM for payload encryption, RSA-OAEP for key wrapping) — no external crypto library needed
- **Migrations:** Flyway
- **Build/Test/Deploy:** Maven, JUnit 5/Mockito, Docker → Render, React + Tailwind frontend

Same conventions as Microloan — layered architecture, global exception handling, `@Version`/locking patterns where relevant. This is your third project using this stack; by now the boilerplate should feel familiar and fast.

---

## 2. Domain Model

| Entity | Key Fields |
|---|---|
| `Device` | id, ownerName, publicKey (stored, for encrypting payloads to this device), registeredAt |
| `Wallet` | id, deviceId, balance, `@Version` |
| `PaymentPacket` | id, senderDeviceId, receiverDeviceId, ciphertext, **ciphertextHash (unique — the claim key)**, encryptedSessionKey, nonce, packetTimestamp, status (RECEIVED/CLAIMED/SETTLED/REJECTED_REPLAY/REJECTED_EXPIRED) |
| `RelayLog` | id, packetId, relayPathId, receivedAt — records each simulated delivery path a packet arrived through, so duplicate delivery is visible and provable |
| `SettlementLedgerEntry` | id, packetId, amount, balanceAfter, settledAt — append-only, never edited |
| `User` | id, email, passwordHash, role — auth |

---

## 3. Engineering Patterns

1. **Hybrid encryption for untrusted relay paths.** The payment payload is AES-GCM encrypted with a random per-packet session key; that session key is then RSA-OAEP encrypted with the receiver's public key. A relay node forwarding the packet never has the private key needed to decrypt anything — it's just moving opaque bytes. This is the real security property: intermediaries can't read or tamper with payment contents.
2. **Claim-before-process idempotency via unique constraint, not locking.** Before doing anything with a packet, attempt to insert its `ciphertextHash` under a unique DB constraint. If it fails (already exists), the packet is a duplicate delivery — reject it immediately, no further processing. This is a different idempotency mechanism than Microloan's `@Version`/pessimistic-locking approach — worth being able to articulate the contrast: locking coordinates access to a resource being *modified*; this coordinates *first-time processing* of an incoming event. Same goal (exactly-once), different mechanism, because the problem shape is different.
3. **Replay protection via nonce + freshness window.** Reject any packet whose `packetTimestamp` is outside an acceptable window (e.g., 5 minutes), regardless of whether the hash claim succeeds — protects against a captured-and-resent packet well after the fact.
4. **Multi-path duplicate delivery, proven, not assumed.** `RelayLog` records every simulated path a packet arrived through. The demo/test scenario: the *same* packet is delivered concurrently via 3+ simulated relay paths (genuine concurrent threads) — exactly one settles, the rest are logged as rejected duplicates, and you can point at `RelayLog` + `PaymentPacket.status` as proof.
5. **Mutation-tested proof, same standard as Microloan's Phase D.** Once the concurrency test passes, temporarily remove the unique constraint (or the claim-check logic), rerun, confirm it now double-settles/fails. Restore, confirm clean. Same "don't trust a green checkmark, prove the test can fail" discipline you already know works.

---

## 4. Endpoint Map (~15 endpoints — deliberately smaller than Microloan)

**Auth**
- `POST /auth/register`, `POST /auth/login`, `GET /auth/me`

**Devices & Wallets**
- `POST /devices` — registers a device + its public key
- `GET /devices/{id}`
- `GET /wallets/{id}`
- `POST /wallets/{id}/fund` — seed balance, demo/admin utility

**Payment Relay**
- `POST /packets/relay` — a simulated relay node forwards a packet (can be called multiple times with the identical packet to simulate multi-path duplicate delivery)
- `GET /packets/{id}`
- `GET /packets?deviceId=`

**Settlement**
- `GET /wallets/{id}/ledger`

**Demo / Admin**
- `POST /demo/simulate-duplicate-delivery` — fires the same packet through N concurrent simulated relay paths; this is your core demo endpoint
- `GET /admin/rejected-packets` — shows replay/duplicate rejections, proof the guardrails work

---

## 5. Build Order (dependency-driven)

**Phase A — Foundation**
`User`/`Device`/`Wallet` entities, auth, device public-key registration, Flyway, exception handling.

**Phase B — Payload Security**
Hybrid encryption service (AES-GCM payload + RSA-OAEP key wrap), `PaymentPacket` entity, an endpoint that submits and decrypts a packet correctly for a valid, single delivery.

**Phase C — Idempotent Claim & Replay Protection**
Unique `ciphertextHash` constraint as the atomic claim mechanism, nonce/timestamp freshness rejection, `RelayLog` recording every delivery attempt.

**Phase D — Concurrent Duplicate-Delivery Proof**
The `/demo/simulate-duplicate-delivery` endpoint firing genuine concurrent threads at the same packet. Concurrency test proving exactly-once settlement. Mutation-test it (disable the claim mechanism, confirm the test fails; restore, confirm it passes) — same rigor as Microloan Phase D.

**Phase E — Deployment & Frontend**
Docker, Render, a minimal React frontend: register a device, fund a wallet, trigger the duplicate-delivery demo with a visual "3 paths delivered, 1 settled, 2 blocked" result. Integration tests, README, demo GIF.

---

## 6. The Review Ritual

Same as before — Plan Mode, "why" questions before approving, walkthrough + quiz after each phase. Given your stated priority is resume-readiness first and deep understanding on your own timeline after, it's fine if the walkthrough-and-quiz step is lighter here than it was for Microloan — but do at minimum ask Claude Code to explain the claim-vs-locking distinction (Pattern 2) before you consider this done enough to put on a resume, since that's the one detail most likely to come up if an interviewer compares this project to Microloan's concurrency work.

---

## 7. Resume Bullet Targets

- *"Designed a hybrid-encryption payment relay (AES-GCM + RSA-OAEP) allowing untrusted intermediary nodes to forward payment packets without access to their contents."*
- *"Implemented exactly-once settlement under simulated multi-path duplicate delivery using an atomic claim-by-hash pattern, validated via concurrent delivery tests and mutation testing."*
- *"Added replay protection via nonce and timestamp freshness windows, rejecting stale or resent payment packets independent of the idempotency check."*
