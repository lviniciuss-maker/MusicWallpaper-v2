# 🎵 MusicWallpaper

<img width="1536" height="1608" alt="Gemini_Generated_Image_gd970mgd970mgd97" src="https://github.com/user-attachments/assets/1dfaa0a7-2d80-4227-92f5-fdca69b0e3e4" />


<p align="center">
  <b>Live lockscreen animated artwork with silky-smooth frosted glass blur and background music detection for Android.</b><br>
  <i>Capas de álbuns animadas na tela de bloqueio com efeito de vidro fosco (blur) espelhado e detecção automática de players.</i>
</p>

---

**IF YOU WANT TO BUY ME A COFFEE ☕**
PIX (BRAZILIAN): f73342e6-86cb-412c-aa5f-1ea2f1faf763
PAYPAL: https://www.paypal.com/donate/?business=6J6ACS6XFSFYE&no_recurring=0&currency_code=BRL

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

## 🚀 Getting Started / Como Executar

1. Clone this repository or download the source code:
   ```bash
   git clone [https://github.com/YOUR_USERNAME/MusicWallpaper.git](https://github.com/YOUR_USERNAME/MusicWallpaper.git)
