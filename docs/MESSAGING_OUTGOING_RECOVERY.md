# NeaRer Messaging: outgoing delivery and ACK recovery

Status: active development batch
Base: current `main`
Working branch: `work/messaging-outgoing-recovery`

## Existing delivery guarantees retained

- A direct outgoing message is added to the encrypted durable outbox before a Nearby payload is attempted.
- The outbox stores a stable trusted peer identity, not a display name as recipient authority.
- Reconnection retries only entries whose pinned stable peer identity matches the authenticated peer.
- Retries reuse the original message ID. If the recipient committed the message but its ACK was lost, receiver-side dedupe returns an ACK instead of creating a duplicate chat bubble.
- Authenticated ACK retirement and bounded completed-receipt history are committed together.
- On Activity reattachment, pending and completed transport IDs reconcile persisted chat bubbles to Queued, Delivered, or Interrupted states.
- A message left queued after restart remains queued until the same trusted peer reconnects.

## New ACK identity hardening

A Nearby endpoint ID is a temporary transport identifier. It must never be treated as the recipient's durable identity.

The ACK acceptance gate now requires all of the following:
1. The message still exists in the pending outgoing outbox.
2. The ACK arrives on the exact endpoint currently bound to that message.
3. The pending outbox recipient stable ID is valid.
4. The authenticated stable ID currently associated with the ACK endpoint normalizes to the same recipient ID.

This prevents a stale endpoint binding from being enough to retire a message if Android/ Nearby later reuses an endpoint identifier for a different peer.

When an authenticated endpoint disconnects, all pending messages bound to that endpoint and stable peer are released back to endpoint-less queued state. Their retry timers are cancelled, attempts are reset, and their endpoint-target mappings are removed. A later reconnect can bind them again only after the expected stable identity authenticates. The Activity is immediately told to display those messages as `Queued`; if no Activity is attached, the outbox remains the source of truth and the state is reconciled on reattachment.

## Tests added

- ACK accepted from the exact expected endpoint and stable peer.
- Normalized stable peer IDs compare consistently.
- ACK rejected if an endpoint ID is reused by a different stable peer.
- ACK rejected from a different endpoint even if the stable peer string matches.
- ACK rejected when the expected or incoming stable identity is absent or malformed.

## Remaining checks

- JVM tests and manifest processing in GitHub Actions.
- Android lint report review against the existing baseline.
- Two-phone validation for disconnect/reconnect, endpoint churn, ACK loss, process death, and delivery-state recovery.
- Physical-device testing is not claimed by CI.

## Product/security boundary

The recipient check protects ACK attribution; it does not upgrade the current message cryptography to Signal Double Ratchet or provide forward secrecy/post-compromise recovery. Those remain separate security-review requirements.
