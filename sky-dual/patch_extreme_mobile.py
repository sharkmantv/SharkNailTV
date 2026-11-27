#!/usr/bin/env python3
"""Replace Extreme's classic / Home with actual SKY RETRO tile art; no player changes."""
from pathlib import Path
import re
root=Path("extreme")
page=root/"src/pages/index.astro"
s=page.read_text()
cards=[
("Live TV","/livetv"),
("Movies","/movies"),
("Series","/series"),
("Playlists","/playlist-editor"),
("Settings","/settings"),
("Reload",None),
("Account","/login"),
("Exit",None),
]
tiles=[]
for i,(label,url) in enumerate(cards):
    cls="sky-classic-tile"
    attr=(f'href="{url}"' if url is not None else f'type="button" data-sky-action="{label.lower()}"')
    tag="a" if url else "button"
    x=(i%4)*100/3
    y=0 if i<4 else 100
    tiles.append(
        f'            <{tag} {attr} class="{cls}" aria-label="{label}">'
        f'<span class="sky-classic-art" aria-hidden="true" '
        f'style="background-position:{x:.6f}% {y}%"></span></{tag}>'
    )
replacement='''        <nav id="hub-tiles" class="sky-classic-grid" aria-label="SKY RETRO Home">
'''+'\n'.join(tiles)+'''
            <span id="livetv-tile-art" hidden></span>
            <span id="movies-tile-art" hidden></span>
            <span id="series-tile-art" hidden></span>
            <span id="hero-eyebrow" hidden></span>
            <span id="livetv-tile-sub" hidden></span>
        </nav>'''
pattern=r'(?s)\s*<nav\s+id="hub-tiles".*?</nav>'
s,n=re.subn(pattern,"\n"+replacement,s,count=1)
assert n==1,f"Expected one classic home nav, got {n}"
# Keep the original app's first-run gate and scripts; remove only the extra home strips.
s=s.replace('        <HubStrips client:load />','')
s=s.replace('\t\t<HubStrips client:load />','')
# Bind actions via native script (layout code and remote navigation remain unchanged).
s=s.replace('<script>\n\timport "../scripts/updater-startup.js";','''<script>
    document.querySelectorAll<HTMLButtonElement>("[data-sky-action]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const mode = btn.dataset.skyAction
        if (mode === "reload") window.location.reload()
        if (mode === "exit") {
          const bridge = (window as any).AndroidDeviceInfo
          if (bridge && typeof bridge.exitToLauncher === "function") bridge.exitToLauncher()
        }
      })
    })
\timport "../scripts/updater-startup.js";''',1)
page.write_text(s)
css=root/"src/styles/global.css"
css.write_text(css.read_text()+'''
/* SKY RETRO classic Android/phone UI (separate from the /tv leanback router) */
:root {
  --color-bg:#030b22;
  --color-surface:#071a39;
  --color-surface-2:#0a2753;
  --color-surface-3:#143773;
  --color-line:#1753a2;
  --color-line-soft:#0b3974;
  --color-accent:#ffd329;
  --color-accent-soft:#ffd32935;
  --color-on-accent:#061633;
  --color-fg:#f2f7ff;
  --color-fg-2:#cbd9ef;
  --color-fg-3:#9bb1d4;
  color-scheme:dark;
}
html body {background:#030b22}
.rounded-2xl,.rounded-xl,.rounded-lg,.rounded-md {border-radius:2px!important}
:where(a,button):focus-visible {outline:2px solid #ffd329;outline-offset:1px}
.sky-classic-grid {
  width:100%;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));
  gap:clamp(7px,1.5vw,16px);align-self:center;margin:auto;
}
.sky-classic-tile {
  aspect-ratio:420/294;display:block;position:relative;overflow:hidden;
  border:2px solid #197ff8;border-radius:0!important;
  background:#071a39;box-shadow:0 0 7px #1967c488;
  padding:0;min-width:0;cursor:pointer;animation:sky-classic-pulse 1.25s ease-in-out infinite alternate;
}
.sky-classic-art {position:absolute;inset:0;display:block;
  background-image:url('/sky-retro/cards.webp');background-size:400% 200%;
  background-repeat:no-repeat;
}
.sky-classic-tile:focus-visible,.sky-classic-tile:focus {
  border-color:#ffd329;outline:2px solid #ffd329;box-shadow:0 0 15px #ffd329;
}
@keyframes sky-classic-pulse{from{border-color:#167fff}to{border-color:#ffd329}}
@media(min-width:650px){.sky-classic-grid{grid-template-columns:repeat(4,minmax(0,1fr));}}
@media(prefers-reduced-motion:reduce){.sky-classic-tile{animation:none}}
''')
print("Patched classic / Home with 8 SKY RETRO posters, blue+gold theme; existing player code unchanged")
