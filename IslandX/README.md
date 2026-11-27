# Island X – Dynamic Island for Android (Oppo A3 Pro 5G)

A pill-shaped overlay around your punch-hole camera, inspired by iPhone 14 Pro's Dynamic Island.

## What it does
- **Music**: shows the playing track; tap to expand (title, artist, prev / play-pause / next); long-press opens the music app.
- **Call timer**: green timer and waveform while a call is active.
- **Charging**: brief pop-up with battery % when you plug in.
- **Notifications**: title and text slide out for about 3.5 seconds.

## Build the APK (Android Studio)
1. Install Android Studio (Koala or newer) and open this `IslandX` folder.
2. Let Gradle sync (it downloads the Android Gradle Plugin and SDK 34).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The file appears at `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy it to the phone and install it (allow "install unknown apps" for your file manager).

Or from a terminal, after Android Studio has created the Gradle wrapper: `./gradlew assembleDebug`.

## First run on the phone
1. Open **Island X**.
2. Tap each permission button: overlay, notification access, phone state.
3. Use the sliders to line the pill up with the camera hole.
4. Tap **Start Island**.

## Oppo / ColorOS tips
- Settings > Battery > Island X > allow background activity.
- Lock the app in the Recents screen so it is not killed.
- If the overlay does not appear, check Settings > Apps > Island X > Display over other apps.

## Known limits
- Cannot answer or hang up calls from the pill.
- No album art or progress bar yet.
- Notification pop-ups skip ongoing and media notifications.
