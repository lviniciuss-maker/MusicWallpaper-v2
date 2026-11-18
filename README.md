# 🎵 MusicWallpaper

<img width="1536" height="1608" alt="Gemini_Generated_Image_gd970mgd970mgd97" src="https://github.com/user-attachments/assets/1dfaa0a7-2d80-4227-92f5-fdca69b0e3e4" />


<p align="center">
  <b>Live lockscreen animated artwork with silky-smooth frosted glass blur and background music detection for Android.</b><br>
  <i>Capas de álbuns animadas na tela de bloqueio com efeito de vidro fosco (blur) espelhado e detecção automática de players.</i>
</p>

---

**IF YOU WANT TO BUY ME A COFFEE ☕**

- PIX (BRAZILIAN): f73342e6-86cb-412c-aa5f-1ea2f1faf763

- PAYPAL: https://www.paypal.com/donate/?business=6J6ACS6XFSFYE&no_recurring=0&currency_code=BRL

# 🎵 MusicWallpaper v2
**Live Lockscreen Animated Album Artwork & Silky Gaussian Blur for Android**

MusicWallpaper transforms your Android lockscreen into a dynamic, high-definition music display inspired by iOS / Apple Music Live Artwork. Built from scratch with a custom **OpenGL ES 2.0 4-Stage Ping-Pong Shader Engine** and **Media3 ExoPlayer**, it renders vertical `3:4` animated album covers (`.m3u8`) or high-resolution static covers seamlessly blended into a mirrored Gaussian blur background.

---

## ✨ Key Features / Principais Recursos

### 🎬 Visual & OpenGL Engine
- **3:4 Vertical Animated Artwork (`.m3u8`):** Automatically fetches animated cover music (`1080x1440` / `2160x2880`) and centers them in a clean `3:4` box with proportional side center-cropping (zero distortion).
- **4-Stage Ping-Pong Gaussian Blur (`256x340` FBO):** Hardware-accelerated multi-pass shader that eliminates banding and pixelation while using minimal battery.
- **Lossless Standby & Home Wallpapers with GPU Sharpening:** Built-in interactive pinch-to-zoom cropper that extracts raw pixels directly from your original photo, saves as uncompressed PNG, and applies a 5-tap *Unsharp Mask* filter on the GPU to match native gallery sharpness.
- **Real-Time Customization Sliders:** Adjust the artwork's vertical position (`0%–40%` from top) and blur intensity (`60%–100%`) dynamically.

### 🔒 Lockscreen-Only Mode & Dual Wallpapers (New in v1.7)
- **Lockscreen-Only Music Toggle:** Works around Android / Samsung One UI live wallpaper picker limitations. When enabled, animated music artwork is displayed **only while your device is on the Lockscreen**, automatically pausing video playback as soon as you unlock your phone.
- **Dual Independent Static Wallpapers:** Set one custom photo for your **Lockscreen Standby** (shown 5 seconds after pausing music) and a completely different static photo for your **Home Screen** with instant 0ms GPU texture switching.

### 🛡️ Smart Search, Privacy & Autonomy
- **Dual Artist & Track Validation (`isArtistMatch` + `isTextMatch`):** Prevents unreleased songs, remixes, or homonymous tracks from displaying an artist's unrelated popular album.
- **Hybrid On-Device Fallback:** Prioritizes `3:4` vertical animated streams and automatically falls back to direct on-device parsing of Apple's official `collectionViewUrl` if the primary endpoint is unreachable.
- **Offline / Notification Artwork Only Mode:** Optional toggle that completely disables external internet requests and renders the exact local cover art embedded in your Android media notification (ideal for local files or self-hosted servers like Navidrome/Plex/Jellyfin).
- **Per-App Exception List:** Ignore specific media apps (such as YouTube, browsers, or social media) so they never override your wallpaper.
- **Built-in GitHub Update Checker:** Checks `lviniciuss-maker/MusicWallpaper-v2` releases automatically on startup or manually via a footer button.

---

## 📲 How to Install & Setup / Como Instalar e Configurar

1. Download the latest **`MusicWallpaper.apk`** from the [**Releases**](https://github.com/lviniciuss-maker/MusicWallpaper-v2/releases/latest) page and install it.
2. Open **MusicWallpaper** and grant **Notification Access** in Card 1 (see the Android 13+ note below if the toggle is greyed out).
3. Choose your **Lockscreen Standby Photo** in Card 2 (and optionally enable **Show music artwork on Lockscreen only** to pick a separate **Home Screen Photo**).
4. Tap **Activate Live Wallpaper** and select **Home screen and lock screen** (or **Lock screen** if your OEM picker supports it).

---

## 🛠️ Troubleshooting / Solução de Problemas Comuns

### 🔒 1. Android 13 / 14 / 15 / 16: "Restricted Setting" (Configuração Restrita)
**🇺🇸 English:**
When installing an `.apk` outside the Play Store on Android 13+, the system blocks **Notification Access** by default for security reasons:
1. Open **MusicWallpaper**, tap **Configure Permission**, and tap on **MusicWallpaper** in the list (a *"Restricted setting"* pop-up will appear—tap **OK**).
2. Tap the yellow **"⚠️ Blocked on Android 13+? How to unlock"** button inside the app -> **Open App Info** (or go to your phone's **Settings > Apps > See all apps > MusicWallpaper**).
3. Tap the **3 dots (⋮)** in the top-right corner and select **Allow restricted settings** (confirm with your fingerprint/PIN).
4. Return to **MusicWallpaper** and enable **Configure Permission** normally.
*(Advanced ADB alternative: `adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener`)*

**🇧🇷 Português:**
Ao instalar um arquivo `.apk` fora da Play Store no Android 13 ou superior, o sistema bloqueia o **Acesso a Notificações** por padrão:
1. Abra o **MusicWallpaper**, toque em **Configurar Permissão** e toque no **MusicWallpaper** na lista (aparecerá o aviso *"Configuração restrita"* — toque em **OK**).
2. Volte ao app, toque no botão amarelo **"⚠️ Permissão bloqueada (Android 13+)? Veja como liberar"** -> **Abrir Informações do App** (ou vá em **Configurações > Apps > Ver todos os apps > MusicWallpaper**).
3. Toque nos **3 pontinhos (⋮)** no canto superior direito e selecione **Permitir configurações restritas** (confirme com sua digital ou PIN).
4. Volte ao **MusicWallpaper** e ative a permissão normalmente.
*(Alternativa via ADB: `adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener`)*

---

### 📱 2. Samsung (One UI), Motorola & Pixel: Using on Lockscreen Only
**🇺🇸 English:**
When tapping **Activate Live Wallpaper**, Android's system prompt may only show *"Home screen"* and *"Home and lock screens"*.
- Simply select **"Home and lock screens"**, then enable **"Show music artwork on Lockscreen only"** inside Card 2 of **MusicWallpaper** and pick your desired **Home Screen Photo**. Your Home Screen will remain static while your Lockscreen displays the animated music artwork!

**🇧🇷 Português:**
Ao clicar em **Ativar Papel de Parede**, o seletor do Android/Samsung pode mostrar apenas *"Tela inicial"* e *"Tela inicial e tela de bloqueio"*.
- Basta escolher **"Tela inicial e tela de bloqueio"** e, dentro do Cartão 2 do **MusicWallpaper**, marcar a opção **"Exibir música apenas na Tela de Bloqueio"** e escolher sua **Foto da Tela Inicial**. Sua tela inicial ficará sempre fixa enquanto a tela de bloqueio exibirá as capas animadas!

---

### 📱 3. Honor (MagicOS) & Huawei (EMUI): Lockscreen Wallpaper Redirects to Home Screen
**🇺🇸 English:**
Honor's MagicOS blocks third-party Live Wallpapers from the lockscreen when **Magazine Unlock** is enabled:
1. Go to **Settings > Home screen & wallpaper > Magazine Unlock** and turn it **OFF** (and ensure you are using the default stock theme).
2. Install the official **Google Wallpapers** app from the Play Store, go to **Live Wallpapers**, select **MusicWallpaper**, and apply it to **Home screen and lock screen**.

**🇧🇷 Português:**
Aparelhos Honor (MagicOS) e Huawei bloqueiam papéis de parede animados na tela de bloqueio quando o **Desbloqueio de Revista (Magazine Unlock)** está ativo:
1. Vá em **Configurações > Tela inicial e papel de parede > Magazine Unlock** e **desative** a opção (usando o tema padrão do sistema).
2. Baixe o app oficial **Planos de fundo do Google (Google Wallpapers)** na Play Store, vá em **Planos de fundo animados**, escolha o **MusicWallpaper** e aplique na **Tela inicial e tela de bloqueio**.
