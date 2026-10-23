# AndroGate

<p align="center"><img src="assets/androgate-logo.svg" width="112" alt="AndroGate logo" /></p>

<p align="center"><strong>Private outbound SMS transport for CONNECT ME</strong></p>

**Private outbound SMS transport for CONNECT ME**

AndroGate turns a dedicated Android phone with an active SIM into an authenticated SMS transmission worker. It is intended for CONNECT ME's server-generated verification messages. The backend remains responsible for OTP generation, expiry, verification, abuse prevention, and account state; AndroGate transports authorized jobs and reports the processing state available from Android and the carrier.

> **Operational note:** Android does not guarantee that a process will remain alive forever. AndroGate is designed to recover using durable local state, a foreground service, scheduled work, and boot/network recovery. Actual reliability depends on Android version, manufacturer power management, connectivity, SIM/service availability, permissions, and carrier policy. Validate on the target handset before relying on it for production.

## Scope

The intended application scope is deliberately narrow:

- Receive authorized outbound SMS jobs from a configured backend over TLS.
- Persist jobs locally before processing.
- Send SMS using Android's telephony APIs and the selected active subscription.
- Enforce a backend-supplied expiry (`validUntil`) before transmission so expired OTPs are not sent after an outage.
- Report submission and delivery status separately where callbacks are available.
- Reconcile pending work after reconnects and device restarts.
- Display gateway, network, SIM, queue, and recent diagnostic status without exposing OTPs or full message bodies in routine logs.

AndroGate is not intended to read or export the device inbox, intercept inbound SMS/MMS, provide an embedded local HTTP API, or forward arbitrary webhooks. OTP generation and verification stay on the CONNECT ME backend.

## Architecture overview

```text
CONNECT ME backend
  ├─ generates and verifies OTPs
  ├─ authorizes recipients and request limits
  ├─ assigns message IDs and expiry
  └─ exposes authenticated HTTPS/SSE job and status APIs
                │
                ▼
AndroGate Android app
  ├─ authenticated TLS connection
  ├─ durable Room queue
  ├─ expiry and retry/reconciliation policy
  ├─ SIM/subscription resolution
  ├─ SmsManager submission
  └─ state updates and operational dashboard
                │
                ▼
Android telephony stack → mobile carrier → destination handset
```

This repository contains the Android client. The CONNECT ME backend and its API contract must be deployed and configured separately. Do not assume that an arbitrary private server is compatible: the client/server protocol, authentication, message payload schema, and state semantics must match.

## Requirements

- Android device with telephony hardware and an active SMS-capable SIM.
- Android SDK and JDK versions supported by the Gradle/Android Gradle Plugin versions declared by this repository.
- A reachable CONNECT ME-compatible backend with a valid HTTPS certificate.
- Per-device registration credentials provisioned using the backend's approved process.
- Permission to send SMS and any additional telephony permissions that the selected SIM-discovery implementation requires.

The application requires `SEND_SMS` to transmit SMS. The current manifest also declares telephony, network, foreground-service, notification, boot, and wake-lock permissions. Review the compiled release manifest before deployment; the source manifest is the authority for declared permissions, while runtime behavior determines when they are requested or needed.

## Build from source

Use a clean checkout and a JDK/Android SDK compatible with the current Gradle wrapper and Android Gradle Plugin.

```bash
git clone https://github.com/Sugarcube08/AndroGate.git
cd AndroGate
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

Release signing should use a dedicated production keystore supplied through the supported CI or local environment configuration. **Do not ship a release signed with the Android debug key.** Verify signing configuration and inspect the final artifact before distributing it.

Expected APK output for a successful release build is typically under `app/build/outputs/apk/release/`; confirm the exact filename produced by the current Gradle configuration rather than assuming it.

## Provision a device

1. Use a dedicated handset and insert the intended SIM.
2. Install a release APK built from the reviewed source and record its SHA-256 checksum.
3. Launch AndroGate, grant only the permissions required by the build, and complete device registration with the trusted CONNECT ME backend.
4. Configure the backend URL and device credentials according to the deployed API contract. Use HTTPS with normal certificate validation.
5. Select or verify the intended SIM. On dual-SIM devices, verify that the actual active subscription maps to the requested physical slot; a slot index is not interchangeable with a subscription ID.
6. Enable the gateway service from the app. Confirm the ongoing notification and actual server connection state.
7. Configure device-specific background operation only as required by the handset and Android version. Battery-optimization exemption is not a universal guarantee and does not prevent every type of process termination.
8. Send a controlled test message to a number you own, then validate submission status, delivery receipt availability, and backend reconciliation.

## Reliability and recovery

The application is intended to use a foreground service for active gateway connectivity and persistent work for recoverable tasks. These mechanisms improve resilience but cannot promise uninterrupted execution or immediate scheduling under every Android policy.

Before production, test all of the following on the actual handset:

- Network disabled and restored.
- Backend unreachable and restored.
- App process terminated and restarted.
- Device rebooted.
- SIM removed, reinserted, or moved between slots.
- SMS permission revoked and restored.
- A queued OTP expiring while offline.
- Duplicate server events.
- Process termination around the SMS submission callback.
- Update installation while jobs are pending.

A job marked submitted is not necessarily delivered. Delivery reports depend on Android/carrier support and must not be represented as guaranteed. The SMS handoff and local database update cannot be made one atomic transaction, so ambiguous submissions require conservative reconciliation to reduce the chance of duplicate OTP SMS.

## Security and privacy

- Use TLS and keep certificate validation enabled.
- Treat device credentials as secrets; never commit credentials, keystores, or production configuration.
- Avoid logging OTPs, message bodies, authorization headers, full phone numbers, or device tokens.
- Use per-device credentials and revoke them when a device is retired or lost.
- Keep the phone dedicated to gateway use and protected by a device lock.
- Do not expose a local unauthenticated SMS relay.
- Inspect exported components and the merged release manifest before each production release.
- Keep Android backups from including sensitive gateway credentials or message contents unless an explicit secure policy requires it.

This project handles SMS-sending capability, which can be abused if the backend or device credentials are compromised. Backend authorization, recipient validation, OTP rate limiting, expiry enforcement, auditing, and carrier-policy compliance are essential controls and cannot be delegated to the Android client alone.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| Gateway stays offline | Verify backend URL, HTTPS certificate, device registration, credentials, DNS, and connectivity. |
| Service stops after idle time | Check Android version, manufacturer battery controls, notification/service state, and system logs; then repeat Doze and process-death tests. |
| SMS fails | Confirm SIM service, SMS allowance, destination format, runtime permission, selected subscription, and Android's submission error. |
| Message remains pending | Check server availability, queue state, retry time, expiry, and reconciliation logs. |
| OTP expires while offline | Expected behavior: an expired code should not be sent. Request a new OTP from CONNECT ME. |
| Status says submitted but no SMS arrives | Submission is not delivery; examine available delivery callbacks and carrier/network conditions. |

## Release checklist

- [ ] Clean build and automated tests pass.
- [ ] Release APK is signed with the intended production certificate, not a debug key.
- [ ] SHA-256 checksum and version are recorded.
- [ ] Merged manifest and permissions are reviewed.
- [ ] No old cloud, incoming inbox, MMS, local-server, or webhook workflows are exposed.
- [ ] API contract and server authentication are verified against the deployed backend.
- [ ] Queue/reboot/network/SIM-recovery scenarios are tested on the target handset.
- [ ] Sensitive data is absent from routine logs.
- [ ] Remaining OS, OEM, SIM, and carrier limitations are documented.

## Licensing and attribution

This repository is derived from the upstream Android SMS Gateway project. Retain the applicable license, copyright notices, and third-party attribution in accordance with the repository's LICENSE file. The AndroGate product identity does not imply affiliation with Android, Google, or mobile carriers.

---

**Repository:** [Sugarcube08/AndroGate](https://github.com/Sugarcube08/AndroGate)  
**Application ID:** `com.connectme.androgate`  
**Product role:** CONNECT ME outbound SMS transport
