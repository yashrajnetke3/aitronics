# AITRONICS development checkpoint

## Verified checkpoint on 28 November 2026

[Build 40](https://github.com/yashrajnetke3/aitronics/actions/runs/46147519833) passed at ee350f7f34a49f582770a5289c5399c6091405b7. Android 15 / API 35 instrumentation completed 13 tests with the real model smoke test enabled. Debug assembly, lint, APK reinstall and activity launch passed. The signed APK identifies itself as com.aitronics.assistant 0.10 (version code 10, minimum API 26). APK SHA-256 is 600f5b4184ce46dc43b352b1c866a54de250445f4a700a013ab7154847bf8c22.

The APK v2 signature verified. Its debug signer SHA-256 is 3e38d76431d215d8a50d28139a8da43ea5cebc0f6f876ab98629c40ccc255e94. The previously distributed 0.9 signer was 6b7bb758a5aa1a253fbb6eeff2c41ea68183c10b68b9835b6c395b9b271ede8a. The certificate mismatch prevents that in-place upgrade. Keep the original installation and use a separate test phone for this APK. The migration regression exercises a genuine version-1 SQLite database, while the CI reinstall exercises the same APK. Production signing was skipped because signing secrets were absent.

**Verified on emulator:** compound offline readings and rejection of unsupported, duplicate and oversized plans; private chat-history redaction; foreground microphone service start and Stop; wake panel shown from Home after injected transcripts; real model download and inference; Room rollback/retry, preserved IDs, distinct ciphertext, corrupt-record rejection, stale edit/delete rejection, and chat-level note save/recall after activity recreation. End-to-end human speech, real WhatsApp/Telegram UI versions, screen-off behavior and battery measurements still require hardware. The edit/delete dialog UI and the signed release workflow require additional testing.

## Audit baseline

The last green baseline before this cycle was [run 35](https://github.com/yashrajnetke3/aitronics/actions/runs/46132508393), commit 8bad7ed448145f1a4f9f81b500d687b9864d23f7. Its API 35 suite exercised ten cases, including a real model download and inference. [Run 36](https://github.com/yashrajnetke3/aitronics/actions/runs/46141976436) compiled and linted 0.9 but lost its emulator during the wake test. A passing build does not establish microphone recognition on a physical phone.

| Phase | Classification | Evidence and open work |
|---|---|---|
| 0, 19, 20, 21 | Partial | Single Compose app, Gradle Kotlin DSL, GitHub debug build, lint, API 35 instrumentation. No repeatable release signing, physical upgrade test or performance baseline evidenced. |
| 1, 3, 4 | Partial | WakeCommands, ScreenCommands, MainActivity and InstalledApps route local actions. This checkpoint adds an allowlist of five read-only operations shared by chat and wake. General goal planning, cancellation and result observation across apps are pending. |
| 2 | Partial | LocalModel provides model import, Qwen download and local inference. Real inference verified in run 35. Configurable cloud chat exists. Streaming, resumable downloads and model resource selection need work. |
| 4, 5 | Requires physical-device testing | Accessibility navigation, draft preparation and confirmed send are implemented. Verify supported apps, changed screens and denied permissions. Audit sensitive screen and notification text before extending collection. OCR and visual grounding are absent. |
| 6, 7 | Partial | Vosk foreground service, system assistant, neon overlay and persistent toggles implemented. The failed run 36 verification passed in run 40. Check panel after Home, short wake and manual Stop on a phone. Bluetooth, multilingual models and energy measurement remain open. |
| 8 | Partial | Room notes persist across activity recreation in the baseline. Version 2 encrypts each note using randomized AES-GCM with an Android Keystore key. Selective changes are implemented. Encrypted export remains pending. |
| 9, 15 | Partial | WorkManager reminders and notification summaries exist. Multi-trigger workflows and opt-in learning remain open. |
| 10–14 | Not implemented | No verified vision, specialized agent framework, manufacturing analysis, paired-device adapters or EEG classifier. Actual sensors and labeled inspection data required for hardware claims. |
| 16, 17 | Partial | Keystore cloud credential, Android permission checks and draft-send confirmation exist. Need privacy regression tests, global stop and structured recovery. |
| 18 | Implemented but untested | Meter samples every fourth PCM sample. No before/after battery, memory or latency measurements on hardware. |

## Active checkpoint

- [x] Fix wake dispatch relying on asynchronous view attachment. Use the panel accepted by WindowManager in the current callback.
- [x] Route real battery, storage, media volume, time and date reads through a local allowlist. Validate all steps before execution. Reject duplicated, unsupported and oversized plans.
- [x] Cover chat integration, limits, unknown actions, duplicate actions and unrelated input in instrumentation.
- [x] Gate passed in run 40. Resume from the code SHA above and the latest documentation commit.
- [ ] Verify acoustic wake on a physical phone with overlay enabled and disabled, screen locked and after Stop.
- [x] Deliver screen action replies transiently. Redact messaging and screen commands from chat history and screen replies from foreground-service notification text. Add a saved-history regression test. Historical records are unchanged.
- [x] Migrate Room v1 notes in a single transaction, encrypt subsequent writes, keep note commands and recalled note text out of chat history. Regression covers interrupted migration, retry, record IDs, randomized ciphertext and corrupt records. Legacy chat history and filesystem remnants are outside this migration.
- [x] Recall displays stable note numbers. Edit memory NUMBER and forget memory NUMBER open a local review dialog. Atomic version checks reject stale edits and deletions.
- [x] Add an optional main-branch release build gated on successful device checks and four complete signing secrets (AITRONICS_KEYSTORE_BASE64, AITRONICS_STORE_PASSWORD, AITRONICS_KEY_ALIAS, AITRONICS_KEY_PASSWORD). Key material exists in a temporary runner file only during signing. No credentials are in the repository.
- [ ] Next checkpoint: compare the authorized release certificate with the installed app, then test in-place upgrade with notes on a physical phone. Without a supplied signer, only the debug artifact is produced. Physical acoustic and device-control testing can proceed independently.
- [ ] Obtain authorized stable release signer and verify an in-place upgrade with existing notes before production distribution. The CI artifact is debug-signed.

## Resume independently of physical testing

- [x] Export a selected note with “export memory NUMBER”. Confirm the plaintext preview and use the Android document picker. Recheck ciphertext before opening the destination. Unit-level instrumentation covers stale/deleted selections, output failure and Unicode export.
- [x] Exclude visible password and Android sensitivity-marked nodes, common authentication labels, and notification previews containing standalone 4–8 digit codes. Screen reading stops on a protected node. Heuristics are conservative; custom controls and multilingual labels require physical testing.
- [ ] For hands-free testing, open Set up wake, allow microphone and notifications, and enable Settings → Allow bubble over other apps. Keep bubble visible controls the idle orb. The Stop notification action disables wake listening.

## Voice regression cycle for 0.11

Local decoding of 16 kHz mono Flite speech using the English model extracted from the distributed 0.10 APK reproduced missed wake and camera/battery commands. Handle the observed long wake and action transcriptions. Test fixtures contain synthetic speech exclusively, packaged only in the instrumentation APK. Exercise real Vosk decoding for long/short wake, camera, battery and a negative control; also exercise missing-app and disabled-accessibility feedback. Build 43 passed the full API 35 suite and targeted API 36 cases. Single-word wake remains speaker dependent: one synthesized “hey” decoded as “they”; that ambiguous word deliberately remains ineligible for activation.

## Hybrid chat recovery

Auto mode checks for an active internet-capable network before selecting the cloud. A connection error, timeout or transient server error can fall back to an installed local model. Authentication failures remain visible. Offline and keyless paths avoid a network check. Live paid cloud inference requires an authorized API key.

## Offline model transfer

The pinned model download keeps a private partial file across network loss or pause. A compatible server resumes at the saved offset. A full HTTP 200 response restarts safely. Length and SHA-256 of the entire file are checked before activation. Cancel removes the partial. Tests cover short reads, invalid ranges, resume, server restart and corrupt data. Import and existing installed model selection stay independent.
