## Summary
<!-- What and why. -->
Closes #

## Area
- [ ] Camera / video (CameraX, YUV, JPEG)
- [ ] Microphone / audio
- [ ] HTTP server / dashboard / HTTPS / login
- [ ] App UI / settings
- [ ] Build / CI / docs

## Invariants kept
- [ ] Latency path unchanged (no extra frame or audio buffering), or explained below
- [ ] Endpoints and URLs StreamBridge uses (`/video`, `/audio.wav`) still work

## Verification
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug`
- [ ] Manual test on a phone: <!-- model, Android version, client used -->

## Hygiene
- [ ] No passwords, keystores, signing secrets, real credentialed URLs or personal paths committed
