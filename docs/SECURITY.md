# AndroGate Security Notes

## Security objective

AndroGate is an outbound SMS transport endpoint for a trusted CONNECT ME backend. It has permission to send SMS, so compromise of its device credential or backend authorization can cause abuse and unexpected carrier charges. Reduce its privileges and exposure to the minimum required for this role.

## Trust boundaries

- **CONNECT ME backend:** generates and verifies OTPs, controls recipients and rate limits, authorizes jobs, maintains challenge expiry, and persists the authoritative job state.
- **AndroGate:** authenticates to the configured backend, durably stores authorized jobs, rejects expired jobs, submits SMS via the Android telephony stack, and returns transmission state.
- **Android / carrier:** controls process scheduling, SIM/subscription availability, submission callbacks, delivery receipts, and actual carrier transmission.
- **Device operator:** protects the handset, SIM, app permissions, operating system updates, and production credentials.

## Required controls

### Authentication and transport
- Use HTTPS for every remote request and keep system certificate validation enabled.
- Provision a distinct credential for each device; rotate/revoke it when the device is lost, retired, or suspected compromised.
- Restrict the configured backend URL to the intended CONNECT ME service.
- Do not put production secrets in source, build configuration, screenshots, issue reports, or logs.
- Verify that device registration and every message/status operation are authenticated and authorized by the backend.

### OTP and message handling
- Generate, expire, rate-limit, and verify OTPs in CONNECT ME.
- Include a stable server-side job ID and expiry with each authorized message.
- Check expiry immediately before the Android SMS hand-off.
- Reject invalid recipients and apply backend-side per-account, per-recipient, per-IP, and global rate limits.
- Do not blindly retry an uncertain submission; reconcile before resending.
- Treat submitted and delivered as distinct states.

### Device and Android components
- Keep inbound inbox permissions and features removed from this outbound-only application.
- Review all exported activities, services, broadcast receivers, and providers in the merged manifest.
- Internal status receivers should not be callable by unrelated apps; correlate callback intents with the pending operation.
- Keep backup disabled or explicitly exclude credentials and sensitive message data.
- Use a device PIN/password, current security updates, physical protection, and a dedicated handset with no personal accounts or unrelated applications.
- Do not expose a local unauthenticated HTTP server or SMS-relay interface.
- Do not disable TLS checks to get around certificate problems.

### Logging and retention
- Never log OTP contents, message bodies, authorization headers, full phone numbers, device tokens, or raw telephony PDUs.
- Mask destinations in diagnostic logs.
- Apply bounded log retention and delete stale message payloads when the retention policy allows.
- Store only data required for queue recovery, deduplication, and status reconciliation.

## Release artifact integrity

Before distributing a release:
1. Build from a reviewed commit.
2. Confirm the release is signed with the intended production certificate, not a debug key.
3. Verify the APK signature.
4. Record the SHA-256 digest, version name/code, commit SHA, target SDK, and signer certificate fingerprint.
5. Inspect the merged manifest and packaged dependency inventory.
6. Retain the build log and test results.

The release workflow verifies signing and emits a SHA-256 digest. That CI verification must be observed in a successful run; the existence of a workflow file alone does not prove a build was signed or tested.

## Known limitations

- No Android application can guarantee that it will never be killed.
- Delivery receipts depend on device, Android, carrier, and destination support.
- The Android telephony submission and local database update are not one atomic transaction; ambiguous outcomes require conservative reconciliation.
- Physical-device and carrier behavior cannot be validated only by unit tests or GitHub source review.
- Carrier terms, A2P/commercial messaging rules, and plan limits must be confirmed independently before production use.

## Security incident response

If credentials leak, the device is lost, or unauthorized sends are suspected:
1. Revoke the affected device credential at the backend.
2. Disable the affected device in CONNECT ME and stop further dispatch.
3. Preserve only sanitized logs and relevant request/job IDs.
4. Review recipient patterns, volume, timestamps, retries, and backend authentication events.
5. Rotate related secrets as appropriate.
6. Re-provision only after the device and build are trusted again.
