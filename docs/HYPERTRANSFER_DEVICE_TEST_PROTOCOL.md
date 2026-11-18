# HyperTransfer physical-device acceptance protocol

This is the user-run validation procedure for real Android phones. It is intentionally separate from emulator/JVM tests. Do not merge or advertise the 280 MB/s / 7x target as achieved until this protocol produces passing evidence.

## Safety and setup

- Use two Android phones with enough free storage for the same test file plus the received copy.
- Use the same source file on both apps. Prefer a 1 GiB file; use 4 GiB only when both phones have comfortable free space. Do not use a file that contains private data.
- Record model, Android version, Wi-Fi chipset/radio capabilities if known, battery percentage, and whether battery saver is off. Do not record phone numbers, account IDs, or personal names.
- Put phones side by side in the same location, keep them cool, and avoid changing orientation or distance between app runs.
- Disconnect unrelated transfers and pause cloud backups/downloads. Keep the same Wi-Fi/Bluetooth/radio state for both apps. Follow each app's normal supported connection flow; do not change hidden developer settings between runs.
- Test only phones you own or have permission to use.

## Exact comparison rules

1. Compute the source file SHA-256 once and record its exact byte length.
2. Use the exact same two physical phones for NeaRer and Xender.
3. Test each direction separately: Phone A → Phone B, then Phone B → Phone A.
4. For each direction and each app, perform at least five successful runs. Keep failed attempts in the report; never discard a failure and silently replace it.
5. Alternate apps (for example, NeaRer, Xender, Xender, NeaRer) to reduce temperature/battery/time bias. Let the phones return to a similar temperature between long runs.
6. Measure application goodput from the first payload byte to the final verified/received byte. Record setup/discovery/handshake separately; do not include setup time in the payload-rate figure, but do report it separately.
7. Verify the received file byte length and SHA-256 after every successful run.
8. Keep the test conditions identical: file hash/size, device pair, direction, distance, location, power mode, and background workload.

## Calculations

- Decimal MB/s = received bytes ÷ payload seconds ÷ 1,000,000.
- MiB/s = received bytes ÷ payload seconds ÷ 1,048,576.
- Speedup = NeaRer median MB/s ÷ Xender median MB/s.
- A pass requires **both** NeaRer median ≥ 280 MB/s and speedup ≥ 7.0x, with at least five successful runs per app for the exact same device pair, direction, file, and conditions.
- Report each direction independently. Do not average a fast direction with a slow direction to hide a failure.
- If either app has fewer than five successful runs, the comparison is inconclusive, not a pass.

## Results sheet

Use one row per attempt. Suggested columns:

| App | Pair key | Direction | File SHA-256 | File bytes | Conditions key | Run | Success | Bytes received | Payload ms | First-byte ms | MB/s | MiB/s | Verified SHA-256 |
|---|---|---|---|---:|---|---:|---|---:|---:|---:|---:|---:|---|

Use anonymous pair keys such as `phone-A__phone-B`. Keep the full SHA-256 and exact file size. Preserve failed runs and explain the failure in notes.

The app-side `TransferBenchmarkReport.toCsv(...)` helper exports run-level evidence with both throughput units and retains failed attempts. It is a library helper at this stage, not yet exposed as an in-app benchmark screen.

## What the numbers mean

280 MB/s is 2.24 Gbit/s of application goodput. It is not a Wi-Fi link/PHY rate. Older Wi-Fi 5 phones may be useful for compatibility and stability testing but may not have enough radio capacity to reach this target. A result below target is still valuable: include it so engineering can identify the actual bottleneck rather than tuning blindly.

## Report back

For each device pair and direction, provide:
- both phone models and Android versions;
- file byte length and SHA-256;
- five or more NeaRer run times and outcomes;
- five or more Xender run times and outcomes;
- first-byte latency, failures, and final SHA-256 verification;
- battery/thermal observations and any disconnects.

Do not send personal identifiers or private file contents. Raw measurements are more useful than a screenshot of the fastest run.
