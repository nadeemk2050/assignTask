# AssignTask Kotlin Cloud Setup (Same Firebase Project)

This repository now contains a cloud-ready Android Kotlin app scaffold at:
- `android-kotlin/`

Firebase project reused:
- `assigntask-51813`

## 1) Create Android App in same Firebase project

1. Open Firebase Console -> project `assigntask-51813`.
2. Add Android app.
3. Use package name: `com.assigntask.app`.
4. Download `google-services.json`.

## 2) Use only GitHub cloud (no local Android install)

1. Open this repo in GitHub Codespaces.
2. Codespace loads `.devcontainer/` and installs Android SDK + Gradle tools.
3. Build in Codespaces terminal:
   ```bash
   cd android-kotlin
   gradle :app:assembleDebug
   ```
4. APK output:
   `android-kotlin/app/build/outputs/apk/debug/app-debug.apk`

## 3) Configure CI build artifacts in GitHub Actions

The workflow is:
- `.github/workflows/build-android-kotlin.yml`

Optional but recommended:
1. In GitHub repo -> Settings -> Secrets and variables -> Actions.
2. Add secret `GOOGLE_SERVICES_JSON`.
3. Paste full contents of your downloaded `google-services.json`.

On every push to `master`/`main` touching `android-kotlin/**`, Actions builds debug APK and uploads artifact.

## 4) Current app scope

The Kotlin app currently includes:
- Jetpack Compose UI
- Firebase Firestore task list sync
- Add task + toggle done

## 5) Next feature parity steps

1. Match all web features from your existing app.
2. Add notifications using WorkManager + Firebase Messaging.
3. Add auth flows if needed (email/Google sign-in).
4. Add release workflow for signed AAB.
