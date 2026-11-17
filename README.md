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

## 🌟 Features / Funcionalidades

- **Animated & Static Artwork (`.m3u8` & High-Res):** Automatically fetches animated vertical artwork (3:4 `url_tall`) from Apple Music via the `artwork.m8tec.top` API, with seamless fallback to high-resolution static album covers.
- **Silky-Smooth 4-Stage Ping-Pong Blur:** Professional dual-pass Gaussian blur rendered via OpenGL ES 2.0 Framebuffers (FBO `256x340`), creating a dense, fog-like frosted glass effect without blocky artifacts or visual ghosts.
- **Sandwich Layout & Center-Crop:** Keeps album artwork crisp in a standardized 3:4 box (preserving original geometry via center-crop without stretching) while mirroring top and bottom blurred reflections behind system UI elements.
- **Multi-Player Support:** Intercepts media sessions and notifications from **Apple Music**, **Spotify**, **YouTube Music**, **Tidal**, **Amazon Music**, **Bitchord**, and **Cider**.
- **Custom Standby Wallpaper:** Choose any photo from your gallery to act as your standby background whenever music is paused (with a configurable 5-second delay).
- **Fully Customizable Sliders:** Adjust artwork vertical position (0% to 40%) and blur intensity (60% to 100%) in real time directly from the app interface.
- **Bilingual UI:** Automatically adapts between English and Portuguese based on your system language.

---

## ⚙️ How to Grant Notification Permission on Android / Como Ativar a Permissão de Notificação

Because this app reads media sessions to detect what music is playing, Android requires **Notification Listener** access. 

1. Open the **MusicWallpaper** app and tap **"1. Music Detection"** (or *Autorizar Leitura de Música*).
2. Find **`MusicWallpaper2`** in the system settings list and toggle the switch to **On**.
3. **Important for Android 13/14/15+ & MIUI/HyperOS:** If the permission switch appears greyed out or shows a *"Restricted setting"* warning:
   - Go to your phone's **Settings** -> **Apps** -> **MusicWallpaper**.
   - Tap the **three dots (⋮)** in the top-right corner (or scroll down on Xiaomi/Poco devices).
   - Select **"Allow restricted settings"** (*Permitir configurações restritas*).
   - Go back and enable the notification permission switch.

---

## 🛠️ Troubleshooting / Solução de Problemas Comuns

### 🔒 1. Android 13 / 14 / 15 / 16: "Restricted Setting" (Configuração Restrita)
**🇺🇸 English:**
When installing an `.apk` outside the Play Store on Android 13+, the system blocks **Notification Access** by default for security reasons:
1. Open **MusicWallpaper**, tap **Configure Permission**, and tap on **MusicWallpaper** in the list (a *"Restricted setting"* pop-up will appear—tap **OK**).
2. Open your phone's **Settings > Apps > See all apps > MusicWallpaper**.
3. Tap the **3 dots (⋮)** in the top-right corner and select **Allow restricted settings** (confirm with your fingerprint/PIN).
4. Return to **MusicWallpaper** and enable **Configure Permission** normally.
*(Advanced ADB alternative: `adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener`)*

**🇧🇷 Português:**
Ao instalar um arquivo `.apk` fora da Play Store no Android 13 ou superior, o sistema bloqueia o **Acesso a Notificações** por padrão:
1. Abra o **MusicWallpaper**, toque em **Configurar Permissão** e toque no **MusicWallpaper** na lista (aparecerá o aviso *"Configuração restrita"* — toque em **OK**).
2. Vá nas **Configurações do celular > Apps > Ver todos os apps > MusicWallpaper**.
3. Toque nos **3 pontinhos (⋮)** no canto superior direito e selecione **Permitir configurações restritas** (confirme com sua digital ou PIN).
4. Volte ao **MusicWallpaper** e ative a permissão normalmente.

---

### 📱 2. Honor (MagicOS) & Huawei (EMUI): Lockscreen Wallpaper Redirects to Home Screen
**🇺🇸 English:**
Honor's MagicOS blocks third-party Live Wallpapers from the lockscreen when **Magazine Unlock** is enabled:
1. Go to **Settings > Home screen & wallpaper > Magazine Unlock** and turn it **OFF** (and ensure you are using the default stock theme).
2. Install the official **Google Wallpapers** app from the Play Store, go to **Live Wallpapers**, select **MusicWallpaper**, and apply it to **Home screen and lock screen**.

**🇧🇷 Português:**
Aparelhos Honor (MagicOS) e Huawei bloqueiam papéis de parede animados na tela de bloqueio quando o **Desbloqueio de Revista (Magazine Unlock)** está ativo:
1. Vá em **Configurações > Tela inicial e papel de parede > Magazine Unlock** e **desative** a opção (usando o tema padrão do sistema).
2. Baixe o app oficial **Planos de fundo do Google (Google Wallpapers)** na Play Store, vá em **Planos de fundo animados**, escolha o **MusicWallpaper** e aplique na **Tela inicial e tela de bloqueio**.

---

## 🚀 Getting Started / Como Executar

1. Clone this repository or download the source code:
   ```bash
   git clone [https://github.com/YOUR_USERNAME/MusicWallpaper.git](https://github.com/YOUR_USERNAME/MusicWallpaper.git)
