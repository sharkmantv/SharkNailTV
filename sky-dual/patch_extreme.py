#!/usr/bin/env python3
"""SKY RETRO styling for Extreme InfiniTV v1.9 source. Keep video/Xtream code intact."""
from pathlib import Path
r=Path("extreme")
def edit(name,old,new):
    p=r/name;s=p.read_text()
    assert old in s,(name,old)
    p.write_text(s.replace(old,new,1))
edit("src/scripts/tv/router.ts", 'home: () => import("./views/home")', 'home: () => import("./views/sky-home")')
edit("src/layouts/TvLayout.astro", '<body class="bg-bg text-fg h-dvh overflow-hidden">', '<body class="bg-bg text-fg h-dvh overflow-hidden" data-sky-retro-page={view}>')
edit("src/layouts/Layout.astro", 'if (receiverStored === null && isTvDevice) {','if (receiverStored === null && isTvDevice && false) {')
edit("src/layouts/Layout.astro", 'if (receiverBootStored === null && isTvDevice) {','if (receiverBootStored === null && isTvDevice && false) {')
# Prevent receiver auto-boot on startup in SKY TV version without altering original player.
p=r/"src/layouts/Layout.astro";s=p.read_text()
a='''            localStorage.getItem("xt_receiver_mode") === "1" &&
            localStorage.getItem("xt_receiver_boot") === "1" &&'''
if a in s: s=s.replace(a,'''            false && localStorage.getItem("xt_receiver_mode") === "1" &&
            localStorage.getItem("xt_receiver_boot") === "1" &&''',1);p.write_text(s)
p=r/"src/layouts/TvLayout.astro";s=p.read_text()
s=s.replace('''            localStorage.getItem("xt_receiver_mode") === "1" &&
            localStorage.getItem("xt_receiver_boot") === "1" &&''','''            false && localStorage.getItem("xt_receiver_mode") === "1" &&
            localStorage.getItem("xt_receiver_boot") === "1" &&''',1)
p.write_text(s)
# Buttons and existing remote focus manager use same app infrastructure.
edit("src/scripts/tv/shell.ts", 'import { mountBackHandler } from "@/scripts/lib/back-handler"', 'import { mountBackHandler, registerBackInterceptor } from "@/scripts/lib/back-handler"')
edit("src/scripts/tv/shell.ts", '''  mountBackHandler()
  mountTvInputGuard()''', '''  mountBackHandler()
  registerBackInterceptor(() => {
    const path = window.location.pathname.replace(/\/+$/, "") || "/"
    if (path === "/tv" || !path.startsWith("/tv/")) return false
    if (document.querySelector("dialog[open], [role=dialog][aria-modal=true]")) return false
    if (document.fullscreenElement) return false
    void navigate("/tv", { history: "replace" })
    return true
  })
  mountTvInputGuard()''')
p=r/"src-tauri/gen/android/app/src/main/java/com/infinitel8p/xtream/MainActivity.kt"
s=p.read_text()
needle="class DeviceInfoBridge(private val activity: TauriActivity) {\n"
assert needle in s
s=s.replace(needle,needle+'''  @JavascriptInterface
  fun exitToLauncher() {
    activity.runOnUiThread { activity.moveTaskToBack(true) }
  }

''',1)
p.write_text(s)
# Test installation side by side with Extreme InfiniTV.
p=r/"src-tauri/tauri.conf.json";s=p.read_text().replace('"productName": "Extreme InfiniTV"','"productName": "SKY RETRO Extreme"');p.write_text(s)
p=r/"src-tauri/gen/android/app/build.gradle.kts";s=p.read_text().replace('applicationId = "com.infinitel8p.xtream"','applicationId = "tv.sharknail.skyretro.extreme"',1);p.write_text(s)
css=r/"src/styles/tv.css"
css.write_text(css.read_text()+'''
/* SKY RETRO: IBO-style blue panels, square edges and gold selection. */
html[data-tv-ui] {--color-bg:#030b22;--color-surface:#081533;--color-surface-2:#10244b;--color-surface-3:#183464;--color-accent:#ffd028;--sky-electric:#1679fd}
html[data-tv-ui] body {background:#030b22!important}
body[data-sky-retro-page="home"] #tv-nav {display:none}
body[data-sky-retro-page="home"] #tv-main {width:100%}
body[data-sky-retro-page="home"] [data-tv-view-root] {padding:0!important}
.sky-home-root {height:100%;width:100%;display:flex;align-items:center;justify-content:center;overflow:hidden}
.sky-home-grid {width:96%;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));grid-template-rows:repeat(2,minmax(0,1fr));gap:1.2rem}
.sky-home-card {display:block;position:relative;aspect-ratio:420/294;min-width:0;background:#071943;border:2px solid #167bfa;border-radius:0;box-shadow:0 0 8px #167bfa88;outline:none;overflow:hidden;cursor:pointer;animation:sky-pulse 1.3s ease-in-out infinite alternate}
.sky-home-art {display:block;position:absolute;inset:0;background-image:url('/sky-retro/cards.webp');background-repeat:no-repeat;background-size:400% 200%}
.sky-home-card:focus-visible,.sky-home-card:focus {border:3px solid #ffd21d;box-shadow:0 0 15px #ffd21d;z-index:2}
@keyframes sky-pulse {from{border-color:#167bfa}to{border-color:#ffd21d}}
html[data-tv-ui] #tv-nav .tv-rail-surface {background:#071b3c!important;border-radius:0!important}
html[data-tv-ui] [data-role="groups-col"],html[data-tv-ui] [data-role="channels-col"],html[data-tv-ui] [data-role="guide-col"] {background:#061631!important;border:2px solid #1872ed!important;border-radius:0!important}
html[data-tv-ui] [data-role="channels-track"] button[data-channel-id] {border-radius:0!important;background:#0c2046!important}
html[data-tv-ui] [data-role="channels-track"] button:focus-visible,html[data-tv-ui] [data-role="groups-track"] button:focus-visible {box-shadow:inset 0 0 0 2px #ffd329!important}
html[data-tv-ui] [data-poster-wrap] {border-radius:0!important;border:1px solid #167bfa}
html[data-tv-ui] #tv-main button[class*="rounded-"],html[data-tv-ui] #tv-main a[class*="rounded-"] {border-radius:0!important}
html[data-tv-ui] #tv-focus-glide {border-color:#ffd329!important;border-radius:0!important}
html[data-tv-ui]::view-transition-group(*) {animation-duration:0ms!important}
html[data-tv-ui][data-perf-mode="on"] .sky-home-card {animation:none;border-color:#ffd329}
''')
print("Patched Extreme 1.9 SKY look, 8-card Home and remote Back; playback left original")
