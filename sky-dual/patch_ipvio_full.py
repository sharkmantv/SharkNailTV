#!/usr/bin/env python3
"""Wire all SKY IBO-style screens into IPVIO navigation, leaving its Media3 and Xtream engines unmodified."""
from pathlib import Path
root=Path("ipvio/app/src/main/java/com/ipvio/stream")
home=root/"HomeScreen.kt"
s=home.read_text()
anchor='''    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRaw = PlaylistStore.channels'''
assert s.count(anchor)==1,"HomeScreen entry must appear once"
replacement='''    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Use SKY RETRO for every primary area, not only Home. Preserve native onOpen/playback callbacks.
    if (tab == 3) {
        val source = PlaylistStore.channels
        val live = remember(source) { source.filter { it.kind == Kind.LIVE } }
        val grouped = remember(live) { live.groupBy { it.group } }
        val favs = remember(live, Library.favs.size) { live.filter { it.id in Library.favs } }
        SkyIboLive(live, grouped, favs, onOpen = onOpen, onHome = { onTab(0) })
        return
    }
    if (tab == 1 || tab == 2) {
        SkyIboCatalog(if (tab == 1) Kind.MOVIE else Kind.SERIES, onOpen) { onTab(0) }
        return
    }
    if (tab == MenuLogic.SETTINGS) {
        val reload: () -> Unit = {
            PlaylistStore.savedUrl(ctx)?.let { u ->
                scope.launch { PlaylistStore.load(ctx, u, false) }
            }
        }
        SkyIboSettings(onAdd, reload) { onTab(0) }
        return
    }
    val allRaw = PlaylistStore.channels'''
s=s.replace(anchor,replacement,1)
home.write_text(s)
# Match native full-screen player menus to the same SKY palette without replacing ExoPlayer.
player=root/"PlayerTheme.kt"
p=player.read_text()
for old,new in [
    ("val PanelBg = Color(0xF2111320)", "val PanelBg = Color(0xF2071B3E)"),
    ("val PanelBorder = DS.Overlay1", "val PanelBorder = Color(0xFF1683FF)"),
    ("val BtnBg = Color(0x24FFFFFF)", "val BtnBg = Color(0xA60A3573)"),
    ("val PanelRadius = 24.dp", "val PanelRadius = 2.dp")
]:
    assert old in p, old
    p=p.replace(old,new,1)
player.write_text(p)

# Settings tile colours are uniform SKY electric-blue/gold instead of rainbow.
settings=root/"SettingsHub.kt"
p=settings.read_text()
old='''    val h = (i * 360f / CATS.size + 18f) % 360f
    return Triple(Color.hsv(h, 0.62f, 0.34f), Color.hsv(h, 0.70f, 0.14f), Color.hsv(h, 0.45f, 1f))'''
new='''    return Triple(Color(0xFF0B3678), Color(0xFF061731), Color(0xFFFFD329))'''
assert old in p
settings.write_text(p.replace(old,new,1))
print("Home, IBO Live switcher/preview/EPG, VOD, Settings, and full-screen player chrome restyled")
