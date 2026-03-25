# Secure Biometric Authentication Sample (Views/XML)

This module demonstrates:

- **BiometricPrompt authentication** with **device credential fallback** (PIN/passcode).
- **Secure token storage** using `EncryptedSharedPreferences` backed by an Android Keystore `MasterKey`.
- **Automatic access-token refresh** (demo refresh logic) with an OkHttp interceptor.
- **Logout / session invalidation**.
- **Lifecycle-aware app lock**: when the app goes to background, it locks and requires biometric/device-credential auth to continue.

## Screens

- **Login**: enter any username/password (demo) to create a secure session.
- **Locked**: shown after backgrounding the app; unlock with biometrics/device credential.
- **Main**: call an API endpoint and logout.

## Run

From the repository `android_kotlin_frontend` directory:

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Launch **“Secure Biometric Auth Sample”** on the device/emulator.
