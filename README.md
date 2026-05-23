# AnHP

AnHP adalah macro recorder Android statis untuk rekam tap/swipe, replay, dan loop offline tanpa API key.

## Cara pakai

1. Install `release/AnHP-release.apk`.
2. Buka AnHP, aktifkan Accessibility Service `AnHP Control`.
3. Izinkan overlay.
4. Tekan `Show Panel` sampai tombol mengambang muncul.
5. Tekan `A` untuk mulai rekam, lakukan tap/swipe target, lalu tekan `A` lagi untuk stop.
6. Tekan `P` untuk replay.
7. Tekan `L` untuk loop ON/OFF. `Max loops = 0` berarti ulang terus sampai `P/STOP` ditekan.
8. Matikan `Simpan rekaman` kalau rekaman hanya ingin dipakai sementara. Nyalakan `Load Saved` atau `Auto load` untuk memakai rekaman tersimpan lagi.

Hardware keyboard juga didukung: `A` record/stop, `P` play/stop, `L` loop ON/OFF selama Accessibility Service aktif.

## Catatan Android

- Android tidak mengizinkan app biasa merekam sentuhan global secara mentah tanpa izin khusus. AnHP memakai overlay transparan saat recording, lalu meneruskan gesture ke app di bawahnya melalui Accessibility `dispatchGesture`.
- Replay tap/swipe membutuhkan Accessibility Service aktif.
- Rekaman tersimpan berada di storage privat app sebagai macro event, bukan video layar.
- Tidak ada request internet dan tidak ada integrasi layanan eksternal.

## Build

```sh
./build.sh
```

Output:

```text
release/AnHP-release.apk
```
