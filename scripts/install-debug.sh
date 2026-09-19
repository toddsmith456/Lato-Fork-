cd p
./gradlew android:assembleDebug
adb install ./android/build/outputs/apk/debug/android-debug.apk
cd ..
