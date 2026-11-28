# AITRONICS development checkpoint

## Evidence on 28 November 2026

The last green baseline before this cycle was [run 35](https://github.com/yashrajnetke3/aitronics/actions/runs/46132508393), commit 8bad7ed448145f1a4f9f81b500d687b9864d23f7. Its API 35 suite exercised ten cases, including a real model download and inference. [Run 36](https://github.com/yashrajnetke3/aitronics/actions/runs/46141976436) compiled and linted 0.9 but lost its emulator during the wake test. A passing build does not establish microphone recognition on a physical phone.

| Phase | Classification | Evidence and open work |
|---|---|---|
| 0, 19, 20, 21 | Partial | Single Compose app, Gradle Kotlin DSL, GitHub debug build, lint, API 35 instrumentation. No repeatable release signing, physical upgrade test or performance baseline evidenced. |
| 1, 3, 4 | Partial | WakeCommands, ScreenCommands, MainActivity and InstalledApps route local actions. This checkpoint adds an allowlist of five read-only operations shared by chat and wake. General goal planning, cancellation and result observation across apps are pending. |
| 2 | Partial | LocalModel provides model import, Qwen download and local inference. Real inference verified in run 35. Configurable cloud chat exists. Streaming, resumable downloads and model resource selection need work. |
| 4, 5 | Requires physical-device testing | Accessibility navigation, draft preparation and confirmed send are implemented. Verify supported apps, changed screens and denied permissions. Audit sensitive screen and notification text before extending collection. OCR and visual grounding are absent. |
| 6, 7 | Partial | Vosk foreground service, system assistant, neon overlay and persistent toggles implemented. Broken verification in run 36. Check panel after Home, short wake and manual Stop on a phone. Bluetooth, multilingual models and energy measurement remain open. |
| 8 | Partial | Room notes persist across activity recreation in the baseline. Version 2 encrypts each note using randomized AES-GCM with an Android Keystore key. Selective edit/delete and encrypted export remain pending. |
| 9, 15 | Partial | WorkManager reminders and notification summaries exist. Multi-trigger workflows and opt-in learning remain open. |
| 10–14 | Not implemented | No verified vision, specialized agent framework, manufacturing analysis, paired-device adapters or EEG classifier. Actual sensors and labeled inspection data required for hardware claims. |
| 16, 17 | Partial | Keystore cloud credential, Android permission checks and draft-send confirmation exist. Need privacy regression tests, global stop and structured recovery. |
| 18 | Implemented but untested | Meter samples every fourth PCM sample. No before/after battery, memory or latency measurements on hardware. |

## Active checkpoint

- [x] Fix wake dispatch relying on asynchronous view attachment. Use the panel accepted by WindowManager in the current callback.
- [x] Route real battery, storage, media volume, time and date reads through a local allowlist. Validate all steps before execution. Reject duplicated, unsupported and oversized plans.
- [x] Cover chat integration, limits, unknown actions, duplicate actions and unrelated input in instrumentation.
- [ ] Gate this checkpoint on assemble, lint and the API 35 suite including model smoke. Check the Actions run for this commit before resuming.
- [ ] Verify acoustic wake on a physical phone with overlay enabled and disabled, screen locked and after Stop.
- [x] Deliver screen action replies transiently. Redact messaging and screen commands from chat history and screen replies from foreground-service notification text. Add a saved-history regression test. Historical records are unchanged.
- [x] Migrate Room v1 notes in a single transaction, encrypt subsequent writes, keep note commands and recalled note text out of chat history. Regression covers interrupted migration, retry, record IDs, randomized ciphertext and corrupt records. Legacy chat history and filesystem remnants are outside this migration.
- [ ] Next independent module: implement selective memory correction and confirmed deletion with stale-record checks.
- [ ] Obtain authorized stable release signer and verify an in-place upgrade with existing notes before production distribution. The CI artifact is debug-signed.
