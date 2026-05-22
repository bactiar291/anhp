# AnHP

AnHP adalah versi Android dari pola AnPlay: rekam tap/swipe, replay dengan tombol `A`/`P`, dan mode AI loop memakai Groq Vision untuk berhenti otomatis saat layar sudah masuk state sukses, OTP, atau terminal error.

## Cara pakai

1. Install `release/AnHP-release.apk`.
2. Buka AnHP, aktifkan Accessibility Service `AnHP Control`.
3. Izinkan overlay.
4. Paste Groq API key sekali, lalu tekan `Save Settings`.
5. Tekan `Start Screen Capture` kalau ingin mode AI loop membaca layar.
6. Tekan floating `A` untuk mulai rekam, tekan `A` lagi untuk stop dan autosave.
7. Tekan floating `P` untuk play, tekan `P` lagi untuk stop.

Hardware keyboard juga didukung: tombol `A` toggle record, tombol `P` toggle play/stop selama Accessibility Service aktif.

## Catatan Android

- Android tidak mengizinkan app biasa merekam sentuhan global secara mentah tanpa izin khusus. AnHP memakai overlay transparan saat recording, lalu meneruskan gesture ke app di bawahnya melalui Accessibility `dispatchGesture`.
- Replay tap/swipe membutuhkan Accessibility Service aktif.
- AI screen check membutuhkan izin MediaProjection dari tombol `Start Screen Capture`.
- Groq model akan dicoba berurutan. App juga bisa mengambil daftar model aktif dari `https://api.groq.com/openai/v1/models`, lalu memprioritaskan model vision yang tersedia pada akun/token tersebut.

## Build

```sh
./build.sh
```

Output:

```text
release/AnHP-release.apk
```

