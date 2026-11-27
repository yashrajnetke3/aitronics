# Voice-first AITRONICS

Target: hands-free Android assistant with optional cloud AI and local inference.

Pipeline: opt-in wake word -> speech recognition -> intent routing -> task plan -> permission and confirmation gate -> authorized Android action -> verify result -> spoken feedback.

Modules: app launching, dialer, message composition, notification summaries, screen navigation, camera, flashlight, volume, brightness, navigation, media, alarms, reminders, user-selected files, translation, opt-in memory and routines. English, Hindi and Marathi speech where supported. Modern cyan/navy dashboard with listening state, mode selector and stop control.

Use explicit Android permissions, visible microphone foreground service where allowed, user-enabled accessibility, and confirmation for sensitive actions. Never bypass lock screen, biometrics, protected app screens or payment authorization. No covert recording. Offline mode must not silently use cloud speech or AI. Verify actions before reporting success. These are target requirements, not implemented feature claims.
