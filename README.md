# Darknes VPN

ByeDPI ile WARP'ı birleştiren, telefonun tamamını kapsayan Android VPN uygulaması.

- **DPI aşma (ByeDPI):** sunucusuz, paketleri parçalayarak engeli aşar; hız kaybı yok, IP değişmez.
  Türk Telekom, Turkcell/Superonline, Vodafone, TurkNet ve mobil veri için hazır ayarlar ve "Otomatik bul".
- **WARP:** Cloudflare'in ücretsiz ağı; hesap otomatik açılır.
- **DPI → WARP yedeği:** DPI ile açılmayan siteler WARP'tan geçer ("tara" ile bulunur).
- **Kendi sunucum:** VLESS / VMess / Trojan / Shadowsocks bağlantısı ya da WireGuard ayarı.
- Sadece engelli siteler ya da her şey; bütün uygulamalar, sadece seçtiklerin ya da seçtiklerin hariç.
- Şifreli DNS (DoH), hızlı ayarlar kutucuğu, açılışta / telefon açılınca bağlanma, bağlantı testi.

## İndirme

[Releases](../../releases/latest) sayfasındaki en son `DarknesVPN-*.apk` dosyasını telefona indirip kur.
Şimdilik yalnızca 64 bit ARM (arm64-v8a) telefonlar desteklenir; günümüzdeki telefonların neredeyse hepsi böyledir.

## Derleme

Her `main` gönderiminde GitHub Actions APK'yı derler (`.github/workflows/build.yml`).
Yerel bileşenler `scripts/build-natives.sh` ile hazırlanır:

| Bileşen | Kaynak | Lisans |
|---|---|---|
| ByeDPI (ciadpi) | github.com/hufrea/byedpi | MIT |
| hev-socks5-tunnel | github.com/heiher/hev-socks5-tunnel | MIT |
| Xray-core | github.com/XTLS/Xray-core | MPL-2.0 |

Akış: `TUN → hev-socks5-tunnel → Xray (yönlendirme, DoH) → doğrudan / ByeDPI / WARP / kendi sunucun`
