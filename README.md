# GitDroid Studio 🚀

GitDroid Studio is a full-featured, native Android IDE and GitHub management client built purely with **Kotlin** and **Jetpack Compose**. It allows developers to create, browse, edit, build, and deploy GitHub Android projects directly from an Android smartphone without ever needing the web interface.

## ✨ Features
- 🔐 **Secure Authentication**: PAT (Personal Access Token) & GitHub OAuth integration with Android Keystore encryption.
- 📁 **Repository & File Management**: Fast virtualized file tree, folder CRUD, instant sync with GitHub REST & Git Data APIs.
- 💻 **Modern Code Editor**: Syntax highlighting, line numbers, snippets, find-and-replace, instant auto-commit & push.
- ⚙️ **Cloud CI/CD & Live Logs**: Trigger GitHub Actions with 1-tap, watch live build logs stream in real-time.
- 📦 **1-Tap Direct APK Installer**: Download compiled APK artifacts and install them immediately using Android's native `FileProvider`.
- 🌿 **Git Branch & PR Management**: Switch branches, view and create pull requests seamlessly.
- 🎨 **Material 3 / Material You**: Beautiful dynamic theming, dark mode default, tailored for mobile touch screens.

## 🛠 Tech Stack
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Coroutines & StateFlow
- **Networking**: Retrofit 2 + OkHttp 4 + Kotlinx Serialization / Gson
- **Security**: AndroidX Security Crypto (`EncryptedSharedPreferences`)
- **CI/CD**: GitHub Actions (`ubuntu-latest`, Android SDK, Gradle build)

## 📄 License
This project is open-source under the [MIT License](LICENSE).
