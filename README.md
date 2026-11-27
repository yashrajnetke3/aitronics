# AITRONICS for Android

Download AITRONICS-debug from the Android APK workflow and extract the APK. Open it on Android 8 or newer and approve installation when prompted. Debug builds from different CI runners may have different signatures. Preserve important data before removing an older installation.

Start by typing **battery level**. **Open WhatsApp** launches an installed app by its launcher name. **Dial 12345** and **message 12345** open the system dialer or message composer for you to review.

| Feature | How to use it |
| --- | --- |
| Local notes | Type **remember my shift starts at nine**, **recall memory**, or **forget all memories**. |
| Reminders | In Settings, enable reminder notifications. Type **remind me in 10 minutes to check the line**. Android power management may delay delivery. |
| Notification summary | Grant Notification access in Settings. Type **summarize notifications** for counts by app. |
| Navigation | Enable AITRONICS in Accessibility settings, then type **go home**, **go back**, or **recent apps**. Screen commands operate on the active accessibility window. |
| Wake phrase | On a supported Android 12+ device, tap Wake and say **hey aitronics, battery level**. The foreground session ends after one command or one minute. Set your own phrase in Settings. |
| Offline AI | Import a compatible Android MediaPipe LLM .task model in Settings and select Offline. Import, inference speed, and available memory depend on the model and device. |
| Cloud AI | Enter your own OpenAI API key in Settings and select Online or Auto. Cloud chat requires internet and API access. |
| History | Turn on Save history on device to retain messages between launches. Clear conversation in Settings to remove it. Saved memories have a separate deletion command. |

GitHub Actions builds the debug APK, runs Android lint, and exercises the chat interface on an Android emulator. To run locally, use JDK 17, Gradle 8.9 and Android SDK 35. Run `gradle :app:assembleDebug :app:lintDebug`, then `gradle :app:connectedDebugAndroidTest` with an Android device connected.
