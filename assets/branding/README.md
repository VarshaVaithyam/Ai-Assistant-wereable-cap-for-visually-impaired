# VisionAssist branding

`visionassist-original.jpeg` is the artwork supplied by the user.
`visionassist-logo.png` crops the presentation canvas to the icon itself and
resizes it to 1024 × 1024, preserving the supplied eye and app name. The original
is retained unchanged. `preview/assets/visionassist-logo.png` is a 256px copy.

Flutter declares the logo as an asset. The Android bootstrap runs
`dart run flutter_launcher_icons` after platform generation and dependency
resolution to create launcher icons. For an existing android/ project run that
command manually after `flutter pub get`, then rebuild/reinstall. This change
does not change an already published APK. The updated native build has not yet
been compiled or installed in this sandbox.

Artwork ownership/third-party rights remain those of its original creator;
project MIT licensing does not independently establish rights to supplied art.
