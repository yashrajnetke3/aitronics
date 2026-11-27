# AITRONICS architecture

Target architecture, not a claim of completed features.

```mermaid
flowchart TD
 A[Voice or text input] --> B[Intent router]
 B --> C{Device command?}
 C -->|Yes| D[Permission and confirmation gate]
 C -->|No| E{AI mode}
 E -->|Offline| F[Local model or built-in commands]
 E -->|Online| G[Configured cloud provider]
 E -->|Auto| H[Cloud if available, otherwise local]
 H --> F
 H --> G
 D --> I[Authorized Android action]
 F --> J[Text and speech response]
 G --> J
 I --> J
 J --> K[Opt-in local conversation history]
 K --> A
```

## Implementation roadmap
1. Modern Compose UI, mode selection, speech and cloud client: implemented in source, verify build and device behavior.
2. Local inference runtime and downloadable compatible model; built-in commands remain available without model.
3. Room-backed opt-in history and memory with deletion controls.
4. User-enabled notification and accessibility services, explicit confirmation for sensitive actions.
5. WorkManager scheduling and supported wake-word service with visible controls.
6. Unit tests, lint, APK build and physical-device validation.

Cloud credentials must never be committed. Offline mode must not silently send conversation data to cloud. Android permissions and OS limitations apply. No claim of full offline generative AI until local inference is integrated and tested.
