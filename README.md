# KUTAŞ SAP ZEBRA ANDROID TERMİNAL UYGULAMASI

Bu proje, **Zebra Android El Terminalleri** (TC21, TC26, TC52, TC57 vb.) üzerinde çalışan ve şirket içi **SAP ITSmobile (`zkut2`)** servisine bağlanan tam ekran (Kiosk) bir Android uygulamasıdır.

- **Hedef URL:** `http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut2`
- **Paket Adı:** `com.kutas.sapterminal`

---

## 🚀 Öne Çıkan Özellikler

1. **Zebra Donanımsal Lazer Barkod Entegrasyonu (DataWedge):**
   - Terminalin tetiğine basılarak okutulan barkod, anında SAP ITSmobile ekranındaki aktif giriş kutusuna aktarılır ve otomatik `Enter` (Gönder) tetiklenir.
   - Odak kutuda olmasa bile ekrandaki ilk metin kutusunu otomatik bulur.
2. **Kiosk / Endüstriyel Tam Ekran Modu:**
   - Adres çubuğu, bildirim çubuğu ve alt navigasyon tuşları gizlenir; operatörün yanlışlıkla uygulamayı kapatması veya sekmeleri karıştırması engellenir.
3. **Ekran Kapanmama (WakeLock):**
   - Sayım veya mal kabul sırasında ekranın otomatik kararmasını önler.
4. **Kesinti & Yenileme Koruması:**
   - Wi-Fi kopmalarında otomatik yeniden deneme ekranı ve sağ altta ufak bir yenileme butonu barındırır.
   - Yanlışlıkla geri tuşuna basıldığında onay diyalogu çıkar.
5. **Dinamik Sunucu Değiştirme:**
   - Yenileme butonuna uzun basıldığında SAP IP/URL adresi değiştirilebilir (ayar cihaz hafızasında saklanır).

---

## 📲 1. Adım: APK'yı Derleme (Build)

1. Bilgisayarınızda **Android Studio**'yu açın.
2. **Open** diyerek bu klasörü seçin: `SAP\ZebraSapTerminal`
3. Proje senkronize olduktan sonra üst menüden:
   **Build > Build Bundle(s) / APK(s) > Build APK(s)** seçeneğine tıklayın.
4. Derleme bittiğinde sağ altta çıkan bildirimden **locate** diyerek oluşturulan `app-debug.apk` dosyasını alın.
5. Bu APK dosyasını USB kablosuyla veya yerel ağ üzerinden Zebra terminaline aktarıp yükleyin.

---

## 🎯 2. Adım: Zebra Terminalinde DataWedge Ayarı (Çok Önemli!)

Zebra'nın barkod lazerinin uygulamayla doğrudan haberleşmesi için terminalde 1 kereye mahsus şu ayar yapılır:

1. Zebra terminalinde menüden **DataWedge** uygulamasını açın.
2. Sağ üstteki **3 nokta** menüsüne basın ve **"New Profile"** seçin.
3. Profil adına `SAP_KUTAS` yazıp **OK** deyin.
4. Listeden oluşturduğunuz `SAP_KUTAS` profiline tıklayın:
   - **Associated apps (İlişkili Uygulamalar):** 
     - 3 noktaya basıp **New app/activity** seçin.
     - Listeden `com.kutas.sapterminal` uygulamasını ve altındaki `*` seçin.
   - Aşağı kaydırın, **Intent Output** bölümüne gelin:
     - **Enabled:** İşaretli (Açık) olsun.
     - **Intent action:** `com.kutas.sapterminal.SCAN` yazın.
     - **Intent category:** Boş bırakın veya `android.intent.category.DEFAULT` yazın.
     - **Intent delivery:** `Broadcast Intent` seçin.
   - (İsteğe bağlı) **Keystroke Output** bölümündeki **Enabled** kutucuğunu kapatabilirsiniz (Intent modu çok daha kararlı ve hızlıdır).

Artık terminalden tetiğe basıp barkod okuttuğunuz anda, SAP ekranına anında düşecektir!

---

## ⚡ Alternatif: Hiç APK Derlemeden Hazır Zebra Enterprise Browser Kullanımı

Eğer terminalinizde **Zebra Enterprise Browser** lisansı / uygulaması zaten yüklüyse:
- Cihazdaki `Config.xml` dosyasındaki `<StartPage>` değerini `http://10.65.12.66:8000/sap/bc/gui/sap/its/zkut2` olarak değiştirmeniz yeterlidir.
- Yan tarafta oluşturduğumuz `EnterpriseBrowser_Config.xml` dosyasını doğrudan kullanabilirsiniz.
