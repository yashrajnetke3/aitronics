# AITRONICS for Android

Download AITRONICS-debug from the Android APK workflow and extract the APK. Open it on Android 8 or newer and approve installation when prompted. Debug builds from different CI runners may have different signatures. Preserve important data before removing an older installation.

Start by typing **battery level**. **Open WhatsApp** launches an installed app by its launcher name. **Dial 12345** and **message 12345** open the system dialer or message composer for you to review.

| Feature | How to use it |
| --- | --- |
| Local notes | Type **remember my shift starts at nine**, **recall memory**, or **forget all memories**. |
| Reminders | In Settings, enable reminder notifications. Type **remind me in 10 minutes to check the line**. Android power management may delay delivery. |
| Notification summary | Grant Notification access in Settings. Type **summarize notifications** for counts by app. Use **read notifications** to review recent previews locally. |
| Navigation | Enable AITRONICS in Accessibility settings, then type **go home**, **go back**, or **recent apps**. Screen commands operate on the active accessibility window. |
| Wake phrase | Once, tap **Set up wake → Enable hands-free**, grant microphone and notification access, then press Home. Say **Hey AITRONICS, battery level** or **Hey AITRONICS, turn on the flashlight**. For automatic hands-free opening on unlocked phones, use **Settings → Set default assistant for hands-free opening**. Otherwise tap the wake notification for actions requiring a screen. Stop via the notification. Opening AITRONICS or activating it as the default assistant resumes enabled listening. Stop disables automatic listening. After force-stop, reopen the app. |
| Offline AI | Import a compatible Android MediaPipe LLM .task model in Settings and select Offline. Import, inference speed, and available memory depend on the model and device. |
| Cloud AI | Enter your own OpenAI API key in Settings and select Online or Auto. Cloud chat requires internet and API access. |
| History | Turn on Save history on device to retain messages between launches. Clear conversation in Settings to remove it. Saved memories have a separate deletion command. |

GitHub Actions builds the debug APK, runs Android lint, and exercises the chat interface on an Android emulator. To run locally, use JDK 21, Gradle 8.9 and Android SDK 35. Run `gradle :app:assembleDebug :app:lintDebug`, then `gradle :app:connectedDebugAndroidTest` with an Android device connected.

Background recognition uses the bundled Vosk small US-English 0.15 model (Alpha Cephei, Apache-2.0). The build downloads the model archive and verifies its SHA-256. Microphone audio is processed on the phone.

### Commands while another app is open

After hands-free setup, say **Hey** or **Hey AITRONICS**, followed by your command. Enable AITRONICS accessibility for screen commands. Say **Hey, close camera** to return Home from Camera, or **Hey, scroll down** in a scrollable app. To dictate, open WhatsApp or Telegram, then use **open chat with Sam** when that chat is visible, **write message I am on my way**, **read draft**, **send message**, and **confirm send**. Use the wake word for each command. Approval expires after 45 seconds and requires the same visible recipient and draft. If Android or the messaging app hides a required control, use the app’s native button.

### Neon wake bubble

The bubble is on by default. While hands-free listening runs, the pinned bubble stays visible while the phone is unlocked and pulses for four seconds when a wake phrase is recognized. To see it above another app, select **Settings → Allow bubble over other apps** and grant Android’s display permission. Turn off **Keep bubble visible** for a wake-only indicator, or turn off **Neon wake bubble** to hide the indicator while keeping voice commands active.

### Answering questions on a fresh installation

Greetings, basic arithmetic and device commands work immediately. For general questions, type or speak your question, then select **Download & answer** (547 MB). AITRONICS verifies the pinned Qwen2.5 0.5B model before activating it, then answers the pending question locally. Keep the app open until setup finishes. A compatible imported model or an OpenAI key in Settings also enables chat. Voice input can use the active hands-free microphone without waiting for a wake word.

In Auto or Online mode, topic questions such as “What is photosynthesis?” also offer **Look up on Wikipedia**. This sends the topic to English Wikipedia only when selected and returns the article excerpt and source URL.
