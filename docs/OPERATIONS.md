# AndroGate Operations Runbook

This runbook covers operating the Android gateway that transmits authorized CONNECT ME SMS jobs through a physical SIM.

## Responsibility boundary

CONNECT ME is the system of record for user accounts and verification challenges. It generates OTPs, sets expiry, validates submissions, enforces resend and attempt limits, authorizes recipients, and decides whether a verification challenge is valid. AndroGate transports an authorized SMS request; it must not become the authority for authentication.

## Provisioning

1. Prepare a dedicated Android handset with telephony support and a functioning SMS plan.
2. Install the reviewed AndroGate release APK. Record its version, SHA-256 digest, and signer certificate.
3. Launch the app and grant the explicitly requested permissions required for SMS sending, network access, and the selected active SIM.
4. Register the device using a credential issued by an administrator through the approved CONNECT ME process.
5. Configure the server URL using HTTPS and verify that the certificate is valid on the handset. Do not bypass certificate validation.
6. Confirm that the displayed server authentication state, connection state, and active subscription are accurate.
7. Send a controlled test message only to a number operated by the administrator. Confirm server state transitions and delivery receipt availability.
8. Record device model, Android release, security patch level, SIM slot, carrier, and background-management settings.

## Normal operation

The operator dashboard should be used to determine whether the gateway is enabled, the server is reachable, the intended SIM is available, and the durable dispatch queue has work waiting. A connection indicator alone does not prove that SMS can be submitted.

The gateway should remain on a stable power supply and use a reliable mobile-data or Wi-Fi path to reach the backend. Confirm that the Android service notification remains present while the service is active. An OS notification is an operational indicator, not a guarantee against process termination.

## Recovery checklist

| Event | Operator action and expected behavior |
| --- | --- |
| Internet outage | Jobs already persisted remain queued. AndroGate reconnects when connectivity returns and reconciles pending jobs. |
| Backend outage | Keep the phone online; inspect authentication, DNS, TLS, and backend health. Do not reinstall or clear app data as a first response. |
| Android terminates the process | Launch/recovery mechanisms should restore service and reconcile durable queue state. Validate on the exact OEM build. |
| Phone reboot | Wait for boot processing and verify service/connection status. Some OS/OEM policies may require unlocking the device or enabling vendor-specific auto-start settings. |
| SIM removed or moved | Stop relying on the current slot selection until the active subscription mapping is confirmed. Never assume slot index equals subscription ID. |
| SMS permission revoked | Transmission should remain blocked and surface a clear degraded state until permission is restored. |
| OTP expiry while offline | The code must not be transmitted after its validity window. Initiate a new challenge in CONNECT ME. |
| Submission callback ambiguous | Do not force a blind resend. Reconcile using the job ID, persisted state, and available send/delivery callbacks. |
| Queue grows continuously | Review server availability, expired work, retry backoff, SIM readiness, carrier errors, and backend acknowledgements. Avoid logging full OTP payloads during diagnosis. |

## SMS state semantics

Keep these states separate in operational reporting:

1. **Accepted/queued:** a job is authorized and stored for processing.
2. **Submitting:** AndroGate is attempting a hand-off to Android telephony.
3. **Submitted:** Android's send operation reported submission success. This is not proof that the recipient handset received the message.
4. **Delivered:** a supported delivery callback reported delivery.
5. **Failed/expired/unknown:** an attempt failed, an OTP expired, or the outcome must be reconciled.

SMS delivery receipts vary by Android build, carrier, network path, and recipient carrier. Do not promise delivery based solely on successful submission.

## Safe diagnostic data

Record event time, job ID, coarse error category, application version, connection state, and a masked destination only when needed. Do not log OTP values, whole SMS bodies, authorization headers, device registration secrets, full phone numbers, or raw telephony payloads. Limit log retention.

## Release procedure

- Build from a reviewed commit and clean checkout.
- Run unit tests and the debug build verification workflow.
- Build a signed release using the protected production keystore.
- Verify the APK signature and SHA-256 digest.
- Inspect the merged release manifest and bundled permissions.
- Install the exact release artifact on the target Android device.
- Run the reliability test matrix below before connecting production traffic.
- Record the tested build, test duration, handset model, OS build, SIM/carrier, and observed results.
- Retain the previous known-good APK and a tested rollback procedure.

## Required pre-production reliability matrix

Run on the real target device; an emulator cannot validate a physical SIM or carrier delivery.

- [ ] Idle with screen off and Doze/battery-restricted conditions.
- [ ] Temporary internet loss and recovery.
- [ ] Private backend unavailable and recovery.
- [ ] Force-stop versus OS process termination (these have different semantics).
- [ ] Full device reboot and subsequent unlock.
- [ ] SIM removal/reinsertion and dual-SIM subscription changes.
- [ ] SMS permission revocation/restoration.
- [ ] An OTP expires while the job is queued offline.
- [ ] App upgrade while jobs are queued.
- [ ] Process interruption around SMS send and status callbacks.
- [ ] Duplicate job/event replay.
- [ ] Failed/ambiguous submission reconciliation.
- [ ] Twenty-four-hour soak test, followed by a longer soak covering the intended service duty cycle.

## Reliability disclaimer

Android cannot guarantee that a third-party process remains alive continuously. Foreground services, scheduled work, boot recovery, and durable state improve resilience but remain subject to Android policy, device-manufacturer behavior, user actions, power, radio/network availability, SIM state, and carrier restrictions. A 24-hour test is useful evidence, not proof of indefinite uninterrupted operation.
