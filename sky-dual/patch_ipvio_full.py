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
print("Home + Live TV IBO-style categories/channel table/live video preview/EPG + Movies + Series + Settings all wired")
