# Rain Cube — Android Mobil Oyun Altyapısı

Rain Cube için oluşturulmuş, Google Play kalite standartlarına uygun, modüler Android + Firebase + CI/CD teknik altyapı projesi.

> **Not:** Bu aşamada yalnızca teknik altyapı, kimlik doğrulama, veritabanı güvenlik kuralları ve otomatik derleme boru hattı kurulmuştur. Oyun mekanikleri (küp hareketi, XP döngüsü, izler, rakipler, oyun menüsü, sıralama UI) sonraki geliştirme fazlarında eklenecektir.

---

## 1. Teknoloji ve Mimari Özeti

- **Platform:** Android (Kotlin + Jetpack Compose + Material 3)
- **Ekran Yönü:** Yalnızca Dikey (`portrait`)
- **Paket Adı (`applicationId`):** `com.raincube.game` (`app/build.gradle.kts` içinden değiştirilebilir)
- **2D Oyun Altyapısı:** Compose `Canvas` + sabit adımlı kare bütçesi sözleşmesi (`com.example.engine`)

### Modüler Klasör Yapısı

```text
app/src/main/java/com/example/
├── auth/                  # Jetpack Credential Manager + Google Sign-In katmanı
├── core/
│   ├── config/            # Uygulama & Firebase kaynak yapılandırma okuyucusu
│   └── firebase/          # Firestore (named DB), Auth, Analytics, yapısal hata günlükçüsü
├── data/
│   ├── model/             # UserProfile, ConnectivityCheck, PlayerStats, Leaderboard, MatchRecord
│   └── repository/        # UserProfileRepository, LeaderboardRepository, MatchRecordRepository
├── engine/                # İlerideki 2D gerçek zamanlı oyun döngüsü için teknik arayüzler
├── ui/
│   ├── auth/              # Google ile Giriş ekranı (Auth Gate)
│   ├── diagnostics/       # Gerçek Firebase/Firestore/Analytics doğrulama paneli
│   └── theme/             # Renk paleti, şekiller ve yerel Google Fonts tipografisi
└── MainActivity.kt        # Edge-to-edge kök bileşeni ve oturum kapısı
```

---

## 2. Firebase & Güvenlik Mimarisi

- **Firebase Authentication:** Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + `GetGoogleIdOption`) ile gerçek Google Sign-In. Anonim veya sahte giriş kapalıdır.
- **Cloud Firestore:** Özel veritabanı ID'si (`R.string.firestore_database_id`) üzerinden bağlanır.
- **Firebase Analytics:** `FirebaseAnalytics` SDK'sı ile oturum ve altyapı olayları kaydedilir.
- **Cloud Functions Hazır Veri Yapısı (`firebase-blueprint.json` & `firestore.rules`):**
  - `/users/{userId}` — Kullanıcı profili (yalnızca hesap sahibi okuyup güncelleyebilir).
  - `/users/{userId}/diagnostics/{checkId}` — İstemci yazma/okuma doğrulama kayıtları.
  - `/users/{userId}/stats/{statDocId}` — Oyuncu istatistikleri ve XP (istemci salt okunur; yalnızca Cloud Functions yazabilir).
  - `/leaderboard_global/{userId}` — Genel sıralama (istemci salt okunur; yalnızca Cloud Functions yazabilir).
  - `/leaderboard_weekly/{weekId}/entries/{userId}` — Haftalık sıralama (istemci salt okunur; yalnızca Cloud Functions yazabilir).
  - `/match_records/{matchId}` — İstemci yalnızca `status = "pending_verification"` ve `xpEarned = 0` ile oluşturabilir; güncelleme/silme kapalıdır, doğrulamayı Cloud Functions yapar.

---

## 3. Yerel Kurulum ve Derleme

1. Projeyi Android Studio (Ladybug / Meerkat+) ile açın veya Gradle 9.3.1+ kullanın.
2. Firebase dosyalarını ekleyin (bkz. `docs/examples/`):
   - `app/google-services.json`
   - `app/src/main/res/values/firebase_applet_config.xml`
3. Debug APK derleyin:
   ```bash
   gradle :app:assembleDebug
   ```
4. Güvenlik kurallarını yerel emülatörde test edin:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```

---

## 4. GitHub Repository Bağlantısı (`rain-cube-game`)

Hassas dosyalar (`app/google-services.json`, `firebase_applet_config.xml`, `.env`, `*.keystore`, `*.jks`) `.gitignore` içine eklenmiştir.

GitHub hesabınızda `rain-cube-game` adlı boş bir repository oluşturduktan sonra yerel projeyi bağlamak için:

```bash
git status
git add .
git commit -m "chore: initialize Rain Cube Android & Firebase infrastructure"
git branch -M main
git remote add origin https://github.com/<KULLANICI_ADINIZ>/rain-cube-game.git
git push -u origin main
```

---

## 5. GitHub Actions — Otomatik APK Derleme & Sürümleme

- `.github/workflows/android-ci.yml`: `main` dalına her push ve pull request'te birim testlerini çalıştırır, Debug APK üretir ve `rain-cube-debug-apk` artifact'ı olarak yükler.
- `.github/workflows/release-apk.yml`: `v*.*.*` etiketi (tag) gönderildiğinde imzalı Release APK & AAB üretip GitHub Releases üzerinde yayınlar.

### Önerilen GitHub Repository Secrets

Repository ayarlarından **Settings → Secrets and variables → Actions** bölümüne şu secret'ları ekleyebilirsiniz:

| Secret Adı | Açıklama |
|---|---|
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 app/google-services.json` çıktısı |
| `FIREBASE_APPLET_CONFIG_XML_BASE64` | `base64 -w0 app/src/main/res/values/firebase_applet_config.xml` çıktısı |
| `RELEASE_KEYSTORE_BASE64` | Yayın imzalama `.jks` dosyasının base64 çıktısı (yalnızca Release workflow için) |
| `STORE_PASSWORD` | Release keystore şifresi |
| `KEY_PASSWORD` | Release key alias (`upload`) şifresi |
