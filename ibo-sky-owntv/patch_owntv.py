#!/usr/bin/env python3
"""Pin upstream OwnTV and apply the standalone IBO SKY RETRO skin.
GPLv3 modifications are kept publicly in this GitHub branch.
Do not patch compiled APK bytecode or private IBO player code.
"""
from pathlib import Path

root = Path("own-source")
shell = root / "app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt"
src = shell.read_text(encoding="utf-8")
start_token = "                        selectedSection == MainSection.HOME -> HomeScreen("
end_token = "                        selectedSection == MainSection.SEARCH ->"
assert src.count(start_token) == 1 and src.count(end_token) == 1
start = src.index(start_token)
end = src.index(end_token,start)
assert 1000 < end-start < 7000
replacement = """                        selectedSection == MainSection.HOME -> SkyRetroLanding(
                            onLive = { onSelectSection(MainSection.LIVE_TV) },
                            onMovies = { onSelectSection(MainSection.MOVIES) },
                            onSeries = { onSelectSection(MainSection.SERIES) },
                            onPlaylists = { onSelectSection(MainSection.MORE) },
                            onSettings = { onSelectSection(MainSection.SETTINGS) },
                            onReload = { (context as? android.app.Activity)?.recreate() },
                            onAccount = { onSwitchProfile() },
                            onExit = { onExitApp() },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED) 64.mpx else 150.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

"""
# Use a new dedicated context value to avoid relying on another alias.
replacement = replacement.replace("(context as? android.app.Activity)?.recreate()", "(skyContext as? android.app.Activity)?.recreate()")
src=src[:start]+replacement+src[end:]
assert "    val noSourceLabel = stringResource(R.string.shell_no_source)\n" in src
src=src.replace("    val noSourceLabel = stringResource(R.string.shell_no_source)\n",
                "    val skyContext = androidx.compose.ui.platform.LocalContext.current\n    val noSourceLabel = stringResource(R.string.shell_no_source)\n",1)
shell.write_text(src,encoding="utf-8")
build = root / "app/build.gradle.kts"
b = build.read_text(encoding="utf-8")
assert 'applicationId = "tv.own.owntv"' in b
build.write_text(b.replace('applicationId = "tv.own.owntv"','applicationId = "tv.own.iboskyretro"',1), encoding="utf-8")
res = root / "app/src/main/res/values"
res.mkdir(parents=True,exist_ok=True)
(res/"sky_brand.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="app_name">IBO SKY RETRO</string>
  <string name="sky_retro_live">LIVE TV</string>
  <string name="sky_retro_movies">MOVIES</string>
  <string name="sky_retro_series">SERIES</string>
  <string name="sky_retro_playlists">PLAYLISTS</string>
  <string name="sky_retro_settings">SETTINGS</string>
  <string name="sky_retro_reload">RELOAD</string>
  <string name="sky_retro_account">ACCOUNT</string>
  <string name="sky_retro_exit">EXIT</string>
  <string name="sky_retro_icon_live">★</string>
  <string name="sky_retro_icon_movies">▶</string>
  <string name="sky_retro_icon_series">♛</string>
  <string name="sky_retro_icon_playlists">▣</string>
  <string name="sky_retro_icon_settings">⚙</string>
  <string name="sky_retro_icon_reload">↻</string>
  <string name="sky_retro_icon_account">●</string>
  <string name="sky_retro_icon_exit">⇥</string>
</resources>
''', encoding="utf-8")
print("Applied IBO SKY RETRO Home overlay to OwnTV.")
