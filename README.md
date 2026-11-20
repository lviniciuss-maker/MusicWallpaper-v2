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
- **Home Screen Diffused Album Blur:** Optionally fills your Home Screen with a soft, darkened, proportional blur of the current album cover using a 260ms GPU crossfade—pausing video decoding immediately after unlock for zero battery drain.
- **GPU-Accelerated Multi-Pass Gaussian Blur:** Smooth top/bottom atmospheric blur with customizable **Vertical Position**, **Blur Intensity**, and **Cover Expansion (up to 95% screen height)** while keeping original aspect ratios.
- **Lockscreen-Only Mode & Keep Cover on Pause:** Choose whether to revert to a lossless standby wallpaper after 5 seconds or keep the last cover active indefinitely, plus independent Home Screen photo pickers.
- **Material You Dynamic Colors (Google Pixel 7+ Exclusive):** Extracts system colors from the album cover strictly on the Lockscreen with a 60-second cooldown.
- **Real-Time Customization Sliders:** Adjust the artwork's vertical position (`0%–40%` from top) and blur intensity (`60%–100%`) dynamically.

### 🔒 Lockscreen-Only Mode & Dual Wallpapers ()
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

---

## ❓ Frequently Asked Questions (FAQ) — English

### 1. How do I keep the last album cover on my Lockscreen without keeping it on my Home Screen?
Enable both **"Keep last album cover when paused"** and **"Show music artwork on Lockscreen only"**. Then, pick your preferred static Home Screen photo and apply MusicWallpaper to **"Home and lock screens"**. This way, the last album artwork (animated or static) stays active on your Lockscreen even when playback is paused or stopped, while your Home Screen keeps your chosen static photo.

### 2. Why can't I enable "Use diffused album blur on Home Screen" and "Show music artwork on Lockscreen only" at the same time?
Both toggles define different behaviors for your Home Screen while music is playing:
- **Use diffused album blur on Home Screen:** fills your Home Screen with a soft, darkened blur of the current album cover and only switches back to your chosen standby photo when music stops.
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
Not all albums have official animated artwork in the Apple Music catalog. When no video is available, the app automatically fetches the high-res static cover (`1000x1000`) or falls back to your player's notification artwork. On slower connections, the previous cover stays smoothly on screen (without flashing the standby wallpaper) while an automatic 4-second retry ensures the new cover loads once the connection stabilizes.

### 7. Does keeping the animated cover on pause or using Home Screen Diffusion drain a lot of battery?
No. Both the video decoder (`ExoPlayer`) and GPU blur passes automatically pause whenever the screen turns off. On the Home Screen with **Diffusion (Blur)** enabled, the crossfade lasts 260ms and video playback pauses right after, keeping battery usage virtually zero while using your phone.

### 8. Why doesn't the "Dynamic System Colors (Material You)" option show up on my phone?
This feature is exclusively enabled on **Google Pixel 7 or newer** devices, as it relies on Pixel's native Material You lockscreen color engine (with a strict 60-second cooldown and lockscreen-only trigger to protect CPU performance).

---

## 🇧🇷 Perguntas Frequentes (FAQ) — Português

### 1. Como mantenho a última capa do álbum na Tela de Bloqueio sem que ela permaneça na minha Tela Inicial?
Ative **"Manter última capa quando a música parar"** e também **"Exibir música apenas na Tela de Bloqueio"**. Em seguida, escolha a foto fixa desejada para a Tela Inicial e aplique o MusicWallpaper em **"Tela inicial e tela de bloqueio"**. Assim, a última capa (animada ou estática) continua ativa apenas na Tela de Bloqueio mesmo quando a música pausar ou parar.

### 2. Por que não consigo marcar "Usar difusão (Blur) da capa na Tela Inicial" e "Exibir música apenas na Tela de Bloqueio" ao mesmo tempo?
As duas opções controlam comportamentos diferentes para a sua Tela Inicial enquanto há música tocando:
- **Usar difusão (Blur) da capa na Tela Inicial:** exibe o efeito difuso com as cores do álbum atual na Tela Inicial e só volta para a foto de espera escolhida quando a música para.
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
Nem todos os álbuns possuem arte animada oficial no catálogo da Apple Music. Quando não há vídeo disponível, o app baixa automaticamente a capa estática em alta resolução (`1000x1000`) ou usa a arte da notificação do player. Em conexões lentas, a capa anterior continua em movimento (sem piscar para o papel de parede padrão) enquanto uma segunda verificação automática é feita após 4 segundos para garantir o carregamento.

### 7. Manter a capa animada ao pausar ou usar a Difusão na Tela Inicial consome muita bateria?
Não. O reprodutor de vídeo (`ExoPlayer`) e os efeitos de blur na GPU são pausados automaticamente sempre que a tela desliga. Na Tela Inicial com **Difusão (Blur)**, a transição dura 260ms e o vídeo é pausado logo em seguida, mantendo o consumo praticamente nulo enquanto você usa o celular.

### 8. Por que a opção "Cores Dinâmicas da Capa (Material You)" não aparece no meu aparelho?
Esse recurso é exibido exclusivamente em aparelhos **Google Pixel 7 ou superior**, pois depende do comportamento nativo do motor Material You na Tela de Bloqueio (com intervalo mínimo de 60 segundos entre trocas para proteger o desempenho do sistema).
