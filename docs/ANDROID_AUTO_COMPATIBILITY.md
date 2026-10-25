# Android Auto compatibility (honest statement)

Checked against Google's documentation and press coverage on 25 Oct 2026.

## What RoadPlay does in the car
RoadPlay is a **media (audio) app**. `automotive_app_desc.xml` declares `<uses name="media"/>` and
`PlaybackService` is a Media3 `MediaLibraryService`. Android Auto can browse
All videos / Favourites / Recently played / Playlists and play the **soundtrack** with play, pause,
next and previous. Playback started from the car disables the video track entirely.

## What it does NOT do
It does not show video on the car display, and installing the APK does not make it appear as a video app.

## Why (official status)
- Google's parked-apps page lists the **video category as generally available on Android Automotive OS
  but unsupported on Android Auto**, and says the video category is "coming to Android Auto in beta" with an
  interest form. Games are available on Android Auto; browsers are not.
- Press coverage says Android Auto video apps (YouTube first) are rolling out gradually, only on compatible
  vehicles, only when parked, and depend on the phone's Android version (reports mention Android 16/17).
  Google had not published a firm date for general availability in the sources found.
- Parked apps go through Google Play review. A sideloaded debug APK is not eligible.
- Unofficial tools exist that force video onto the dashboard; RoadPlay deliberately does not use them.

## Safety design
- A car connection (`CarConnection`) is treated as "connected", never as "parked".
- When a car is connected, phone playback defaults to audio-only and shows a reminder.
- `ParkedVideoSupport.evaluate()` always returns `Unavailable`.

## What must change when official support is available
1. Confirm the phone OS/Android Auto version, the head unit, and that your app/account is accepted in the
   Android Auto video programme (interest form linked from developer.android.com/training/cars/parked/video).
2. Distribute through Google Play (car app quality review).
3. Add `android:appCategory="video"` on `<application>`; do NOT put `<uses name="video"/>` in
   `automotive_app_desc.xml`. Keep the media declaration for audio.
4. Make the player Activity adaptive (portrait/landscape, resizable) and pause on user-experience restrictions.
5. Implement the documented parked-state signal in the `safety` package and only then return `Available`.
6. Optional: declare `com.android.car.background_audio_while_driving` per the video docs (early-access partners).
Do not add manifest entries "to try it": they do not enable anything by themselves.
