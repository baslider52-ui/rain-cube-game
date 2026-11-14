# Rain Cube

Rain Cube Android mobil oyunu projesi.

## 📱 APK İndir (Android)

Kullanıcılar en güncel APK dosyasını doğrudan GitHub üzerinden indirebilir:

1. Repository sayfasının sağ tarafındaki **Releases** bölümüne girin (veya `https://github.com/<KULLANICI_ADINIZ>/rain-cube-game/releases/latest` adresini açın).
2. **`RainCube-android.apk`** dosyasına dokunarak indirin.
3. Android cihazınızda dosyayı açıp kurulumu tamamlayın.

> `main` dalına her yeni kod gönderildiğinde `.github/workflows/android-ci.yml` otomatik olarak güncel `RainCube-android.apk` dosyasını derler ve **Releases** sayfasında yayınlar.

---

## 🏗️ Teknik Mimari

- **Platform:** Android (Kotlin, Jetpack Compose, Material 3)
- **Ekran Yönü:** Yalnızca Dikey (`portrait`)
- **Paket Adı (`applicationId`):** `com.raincube.game` (`app/build.gradle.kts` içinden değiştirilebilir)
- **Kimlik Doğrulama:** Google Sign-In (`Jetpack CredentialManager` + Firebase Auth)
- **Veritabanı & Analitik:** Cloud Firestore + Firebase Analytics + Cloud Functions uyumlu güvenlik kuralları (`firestore.rules`)

```text
app/src/main/java/com/example/
├── auth/                  # Google Sign-In & oturum akışı
├── core/                  # Yapılandırma, Firebase sağlayıcıları ve hata günlükçüsü
├── data/                  # Modeller ve Firestore repository katmanı
├── engine/                # 2D gerçek zamanlı oyun döngüsü teknik sözleşmeleri
├── ui/                    # Giriş ekranı, Oyuncu Merkezi ve tema
└── MainActivity.kt        # Edge-to-edge kök bileşeni
```

---

## 🚀 GitHub Repository ve Otomatik APK Kurulumu

Hassas dosyalar (`app/google-services.json`, `firebase_applet_config.xml`, `.env`, `*.keystore`, `*.jks`) `.gitignore` ile korunmaktadır.

### 1. Kodu GitHub'a Gönderme (`rain-cube-game`)

```bash
git add .
git commit -m "chore: setup Rain Cube app, icon, Firebase and GitHub APK release"
git branch -M main
git remote add origin https://github.com/<KULLANICI_ADINIZ>/rain-cube-game.git
git push -u origin main
```

### 2. GitHub Secrets Ayarları (APK'nın Google ile Giriş Çalışması İçin)

GitHub repository ayarlarından **Settings → Secrets and variables → Actions** bölümüne şu iki secret'ı ekleyin:

- `GOOGLE_SERVICES_JSON_BASE64`: `base64 -w0 app/google-services.json` çıktısı
- `FIREBASE_APPLET_CONFIG_XML_BASE64`: `base64 -w0 app/src/main/res/values/firebase_applet_config.xml` çıktısı

İmzalı sürüm yayınlamak için (`v1.0.0` etiketi ile `release-apk.yml`):
- `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`
