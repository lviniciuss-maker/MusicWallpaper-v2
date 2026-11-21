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

# 🎵 MusicWallpaper

**Live Lockscreen Animated Album Artwork for Android (OpenGL ES 2.0 + ExoPlayer)**  
*Papéis de parede animados com capas de álbuns em tempo real para Android.*

MusicWallpaper detects media playing from any music app on your Android device (Apple Music, Spotify, YouTube Music, Tidal, Amazon Music, Bitchord, Cider, and more) and renders high-resolution animated (`.m3u8`) or static (`1000x1000`) album artwork with a smooth, multi-pass Gaussian blur directly on your Lockscreen and Home Screen.

---

## ✨ Features / Recursos

- **Animated `.m3u8` & High-Res Static Artwork:** Fetches official vertical 3:4 animated covers (with direct Apple Music fallback), `1000x1000` static covers from iTunes, or uses your player's local notification artwork (Offline Mode).
- **350ms GPU Crossfade & Multi-Stage Verification:** Smooth dissolve transitions between tracks with a 9-point bitmap signature tracker and automatic **2s and 4s** verifications for delayed player notifications and slow Wi-Fi.
- **GPU-Accelerated Multi-Pass Gaussian Blur & Live Preview:** Customize **Vertical Position (0%–60%)**, **Blur Intensity**, **Cover Expansion (up to 95%)**, **Cover Dimming (0%–40%)**, and **Top Clock Shadow** with an interactive real-time Lockscreen preview inside the app.
- **Subtle Zoom Animation on Static Covers (Optional):** Gentle *Ken Burns* breathing effect (`1.00x` ↔ `1.06x`) with 3 speeds (**Fast 8s**, **Medium 12s**, **Slow 18s**), capped at 30 FPS with zero blur recalculation overhead.
- **Home Screen Diffused Album Blur:** Optionally fills your Home Screen with a soft, aspect-ratio-preserving blur of the current album cover with customizable **Diffusion Brightness (40%–100%)** and a 260ms unlock crossfade.
- **Persistent Disk Cache (LRU), Wi-Fi Only & Battery Saver Modes:** Caches recent `.m3u8` URLs and up to 25 MB of static covers on disk, skips video streaming on 4G/5G when Wi-Fi Only is enabled, and automatically freezes animations in Android Battery Saver.
- **Flexible Standby & Home Controls:** Customizable pause delay (`5s`, `15s`, `30s`, `1 min`), **Keep Last Cover on Pause**, **Lockscreen-Only Mode**, and independent lossless Home Screen photo pickers.
- **Light, Dark & System Themes:** Clean adaptive interface with **System**, **Light**, and **Dark** theme selector, collapsible Media Diagnostics, one-tap Cache Cleaner, Ignored Apps filter, Material You dynamic colors (Google Pixel 7+ exclusive), built-in GitHub Update Checker, and bilingual FAQ (EN/PT).

---

## 📦 Download & Installation / Download e Instalação

1. Go to the **[Releases](https://github.com/lviniciuss-maker/MusicWallpaper-v2/releases/latest)** page and download **`app-release.apk`**.
2. Install the APK on your Android device (Android 10 / API 29 or higher).
3. Open **MusicWallpaper**, grant **Notification Access**, customize your preferences, and tap **Activate Live Wallpaper**.

---

## ❓ Frequently Asked Questions (FAQ) — English

### 1. How do I keep the last album cover on my Lockscreen without keeping it on my Home Screen?
Enable both **"Keep last album cover when paused"** and **"Show music artwork on Lockscreen only"**. Then, pick your preferred static Home Screen photo and apply MusicWallpaper to **"Home and lock screens"**. This way, the last album artwork (animated or static) stays active on your Lockscreen even when playback is paused or stopped, while your Home Screen keeps your chosen static photo.

### 2. Why can't I enable "Use diffused album blur on Home Screen" and "Show music artwork on Lockscreen only" at the same time?
Both toggles define different behaviors for your Home Screen while music is playing:
- **Use diffused album blur on Home Screen:** fills your Home Screen with a soft blur of the current album cover (with adjustable brightness) and only switches back to your chosen standby photo when music stops.
- **Show music artwork on Lockscreen only:** always keeps your chosen static photo on the Home Screen, even while music is playing.

Each option has its own independent photo picker.

### 3. My device already lets me apply Live Wallpapers to the Lockscreen only. Do I need to enable "Show music artwork on Lockscreen only"?
No. That option is only meant for devices/ROMs that don't allow applying Live Wallpapers exclusively to the Lockscreen (forcing you to choose *"Home and lock screens"*). If your phone lets you set it on the Lockscreen alone, leave this toggle off.

### 4. Android blocked Notification Access with a "Restricted setting" warning. How do I unlock it?
On Android 13 and newer, apps sideloaded via APK outside the Play Store have Notification Access restricted by default. You can unlock it in two ways:

**Option A — Directly on your phone:**
1. Tap **Configure Permission** and select **MusicWallpaper** (the *Restricted setting* warning will appear — tap OK).
2. Return to the app, tap the yellow help button, and open **App Info**.
3. Tap the **3-dot menu (⋮)** in the top-right corner and select **Allow restricted settings**.
4. Go back to **Configure Permission** and enable MusicWallpaper normally.

**Option B — Via ADB (PC or Shizuku/Termux):**
If the 3-dot menu does not appear on your device, connect it with USB Debugging enabled and run:
```bash
# 1. Allow restricted settings for the app
adb shell appops set com.lucasvinicius.musicwallpaper2 ACCESS_RESTRICTED_SETTINGS allow

# 2. (Optional) Directly grant Notification Listener access
adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener
```

### 5. How do I stop YouTube, Reels, or web browsers from changing my wallpaper?
In **Card 1 (Music Detection)**, tap **Ignored Apps** and check any video players, social media apps, or browsers you don't want MusicWallpaper to track.

### 6. Why are some songs static or take a few seconds to update on slow Wi-Fi?
Not all albums have official animated artwork in the Apple Music catalog. When no video is available, the app automatically fetches the high-res static cover (`1000x1000`) or falls back to your player's notification artwork. On slower connections, the previous cover stays smoothly on screen (without flashing the standby wallpaper) while automatic **2s and 4s** checks ensure the new cover loads once the connection stabilizes.

### 7. Does keeping the animated cover on pause, using Home Screen Diffusion, or enabling Static Cover Zoom drain a lot of battery?
No. Video decoding (`ExoPlayer`), GPU blur passes, and the optional 30 FPS static zoom automatically pause whenever the screen turns off, when you unlock to the Home Screen, or when Android Battery Saver is active. On the Home Screen with **Diffusion (Blur)** enabled, the crossfade lasts 260ms and video playback pauses right after, keeping battery usage virtually zero while using your phone.

### 8. Why doesn't the "Dynamic System Colors (Material You)" option show up on my phone?
This feature is exclusively enabled on **Google Pixel 7 or newer** devices, as it relies on Pixel's native Material You lockscreen color engine (with a strict 60-second cooldown and lockscreen-only trigger to protect CPU performance).

---

## 🇧🇷 Perguntas Frequentes (FAQ) — Português

### 1. Como mantenho a última capa do álbum na Tela de Bloqueio sem que ela permaneça na minha Tela Inicial?
Ative **"Manter última capa quando a música parar"** e também **"Exibir música apenas na Tela de Bloqueio"**. Em seguida, escolha a foto fixa desejada para a Tela Inicial e aplique o MusicWallpaper em **"Tela inicial e tela de bloqueio"**. Assim, a última capa (animada ou estática) continua ativa apenas na Tela de Bloqueio mesmo quando a música pausar ou parar.

### 2. Por que não consigo marcar "Usar difusão (Blur) da capa na Tela Inicial" e "Exibir música apenas na Tela de Bloqueio" ao mesmo tempo?
As duas opções controlam comportamentos diferentes para a sua Tela Inicial enquanto há música tocando:
- **Usar difusão (Blur) da capa na Tela Inicial:** exibe o efeito difuso com as cores do álbum atual na Tela Inicial (com brilho ajustável) e só volta para a foto de espera escolhida quando a música para.
- **Exibir música apenas na Tela de Bloqueio:** mantém sempre a foto fixa escolhida na Tela Inicial, mesmo com música tocando.

Cada uma das opções possui seu próprio seletor independente de foto.

### 3. Meu aparelho já permite aplicar Live Wallpapers somente na Tela de Bloqueio. Preciso ativar "Exibir música apenas na Tela de Bloqueio"?
Não. Essa opção foi criada apenas para aparelhos que não permitem definir papéis de parede animados exclusivamente na Tela de Bloqueio (exigindo aplicar em *"Tela inicial e tela de bloqueio"*). Se o sistema do seu celular já permite aplicar só na Tela de Bloqueio, você pode deixar essa chave desativada.

### 4. O Android bloqueou o Acesso a Notificações com o aviso "Configuração restrita". Como liberar?
No Android 13 ou superior, aplicativos instalados via APK fora da Play Store têm essa permissão bloqueada por padrão. Você pode liberar de duas formas:

**Opção A — Pelo próprio celular:**
1. Toque em **Configurar Permissão** e toque em **MusicWallpaper** (o aviso de *Configuração restrita* aparecerá — toque em OK).
2. Volte ao app, toque no botão amarelo de ajuda e abra **Informações do App**.
3. Toque nos **3 pontinhos (⋮)** no canto superior direito e selecione **Permitir configurações restritas**.
4. Volte em **Configurar Permissão** e ative o MusicWallpaper normalmente.

**Opção B — Via ADB (PC ou Shizuku/Termux):**
Se a opção dos 3 pontinhos não aparecer no seu sistema, conecte o aparelho com a Depuração USB ativada e execute os comandos abaixo:
```bash
# 1. Libera as configurações restritas para o aplicativo
adb shell appops set com.lucasvinicius.musicwallpaper2 ACCESS_RESTRICTED_SETTINGS allow

# 2. (Opcional) Concede o Acesso a Notificações diretamente
adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener
```

### 5. Como evitar que vídeos do YouTube, Reels ou navegadores troquem a capa do papel de parede?
No **Cartão 1 (Detecção de Música)**, toque em **Apps Ignorados** e marque todos os aplicativos de vídeo ou navegadores que você não deseja monitorar.

### 6. Por que algumas músicas não ficam animadas ou demoram alguns segundos para atualizar no Wi-Fi lento?
Nem todos os álbuns possuem arte animada oficial no catálogo da Apple Music. Quando não há vídeo disponível, o app baixa automaticamente a capa estática em alta resolução (`1000x1000`) ou usa a arte da notificação do player. Em conexões lentas, a capa anterior continua em movimento (sem piscar para o papel de parede padrão) enquanto checagens automáticas em **2s e 4s** garantem o carregamento.

### 7. Manter a capa animada ao pausar, usar a Difusão na Tela Inicial ou ativar o Zoom em capas estáticas consome muita bateria?
Não. O reprodutor de vídeo (`ExoPlayer`), os efeitos de blur na GPU e o zoom opcional de 30 FPS pausam automaticamente sempre que a tela desliga, quando você vai para a Tela Inicial ou quando a Economia de Bateria do Android está ativa. Na Tela Inicial com **Difusão (Blur)**, a transição dura 260ms e o vídeo é pausado logo em seguida, mantendo o consumo praticamente nulo enquanto você usa o celular.

### 8. Por que a opção "Cores Dinâmicas da Capa (Material You)" não aparece no meu aparelho?
Esse recurso é exibido exclusivamente em aparelhos **Google Pixel 7 ou superior**, pois depende do comportamento nativo do motor Material You na Tela de Bloqueio (com intervalo mínimo de 60 segundos entre trocas para proteger o desempenho do sistema).
