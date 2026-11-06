# HyperTransfer performance decision: Nearby connection strategies

Status: research-backed implementation decision; runtime strategy switching is not yet implemented.

## Evidence reviewed

- Google's Nearby Connections strategy guide describes:
  - `P2P_CLUSTER` as a flexible many-to-many topology with lower bandwidth.
  - `P2P_STAR` as one-to-many with higher bandwidth than Cluster.
  - `P2P_POINT_TO_POINT` as one-to-one and the highest-throughput strategy.
- Google's Android exchange-data guide says FILE payloads backed by a `ParcelFileDescriptor` can minimize copying, FILE payloads support arbitrary file sizes, and payload callbacks must be correlated by payload ID because BYTES and FILE delivery order is not guaranteed.
- An independent empirical study of Nearby Connections found Point-to-Point generally outperformed Star for high-traffic transfers, while Star could show lower throughput and instability. It also found link performance can be asymmetric depending on which device acts as the access point.

## Decision

1. Keep the current Cluster discovery/connection mode as the compatibility default for NeaRer's existing multi-peer nearby experience. Do not silently replace it with Point-to-Point: the topology is part of the connection handshake and both peers must use compatible strategies.
2. Add a dedicated one-to-one High-Speed session mode as a separate, explicit path. Both phones must enter that mode before discovering/connecting. The mode should use Point-to-Point for the actual transfer session and return to Cluster discovery when the session ends.
3. Keep FILE payloads backed by the selected content URI's ParcelFileDescriptor for the initial high-speed path. Google's docs specifically identify this as an efficient way to avoid unnecessary byte copying. Do not migrate all files to STREAM merely because streams exist; streams require prompt background consumption to prevent pipe back-pressure and have indeterminate progress totals.
4. Preserve the current bounded parallel-file window in Cluster mode. In a Point-to-Point single-peer session, benchmark one large payload first; do not assume that increasing parallel payloads improves throughput.
5. Do not advertise a numeric speed multiplier until controlled tests on the same pair of physical phones demonstrate it.

## Required implementation sequence

- Add an explicit High-Speed mode choice visible on both devices.
- Stop the existing Cluster advertising/discovery before starting Point-to-Point on both peers; do not attempt to upgrade an already-established Cluster connection in place.
- Add a handshake/version capability so older builds fail back to Cluster safely.
- Run identical files and conditions through Cluster and Point-to-Point, alternating sender/receiver roles.
- Record median MiB/s, first-byte latency, failure rate, and thermal state over repeated runs. Compare medians, retain failed runs, and report device/network conditions.
- Only enable automatic strategy selection after these tests show a repeatable benefit without unacceptable connection failures.

## Resume work remains separate

The current resume journal, prefix hashing, negotiation codec, and durable partial writer are foundations only. They are not yet connected to the active Nearby FILE transport. Keep resume marked experimental until the sender's accepted offset is applied to the outgoing FILE payload and the receiver can safely preserve/revalidate its partial data across disconnects.

## References

- https://developers.google.com/nearby/connections/strategies
- https://developers.google.com/nearby/connections/android/exchange-data
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- https://www.sciencedirect.com/science/article/pii/S2542660523002184
