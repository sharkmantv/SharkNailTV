from pathlib import Path
root = Path("app/src/main/java/com/example/xtreamtv")
def swap(s, old, new):
    assert s.count(old) == 1, "Unexpected source: " + old[:150]
    return s.replace(old, new, 1)

p = root / "MainActivity.kt"
s = p.read_text()
s = swap(s, '"Refresh Sports","Clear Memory Cache","Playback & Display"', '"Refresh Sports","Playback & Display"')
s = swap(s, '                                                            "Clear Memory Cache" -> cleanAppCache()\n', '')
assert 'FocusAction("Shark Optimiser · Clear Cache") { cleanAppCache() }' in s
start = s.index('            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End, verticalAlignment=Alignment.CenterVertically) {\n                Box(Modifier.size(8.dp).background(if(connected)')
end = s.index('            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical=spacing)', start)
assert '"Connected"' in s[start:end]
s = s[:start] + '            Spacer(Modifier.height(16.dp))\n' + s[end:]
s = swap(s, 'Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {\n                    Box(Modifier.fillMaxWidth().height(previewHeight)', 'Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {\n                    Box(Modifier.fillMaxWidth().height(previewHeight)')
s = swap(s, 'Text(current?.title.orEmpty(),modifier=Modifier.heightIn(min=22.dp)', 'Text(current?.title.orEmpty(),modifier=Modifier.height(36.dp)')
lines = s.splitlines(keepends=True)
first = [i for i,l in enumerate(lines) if 'upcoming.take(2).forEach' in l]
last = [i for i,l in enumerate(lines) if 'upcoming.drop(2).forEach' in l]
assert len(first) == len(last) == 1
i,j = first[0],last[0]
del lines[j]
lines[i] = '                    Column(Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {\n' + '    ' + lines[i].replace('upcoming.take(2).forEach','upcoming.forEach') + '                    }\n'
s = ''.join(lines)
s = swap(s, '''                    val now=System.currentTimeMillis()
                    val upcoming=listings.filter { (parseEpgTime(it.end) ?: Long.MAX_VALUE)>now }.sortedBy { parseEpgTime(it.start) ?: Long.MAX_VALUE }.take(8)''', '''                    var guideNow by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) { while(true) { guideNow=System.currentTimeMillis(); delay(60_000) } }
                    val upcoming=remember(listings,guideNow) { listings.filter { (parseEpgTime(it.end) ?: Long.MAX_VALUE)>guideNow }.sortedBy { parseEpgTime(it.start) ?: Long.MAX_VALUE }.take(8) }''')
start = s.index('                val preferSdr = playbackPrefs(context).getString("dynamicRange", "Auto")')
end = s.index('                val direct = eligible.filter',start)
s = s[:start] + s[end:]
s = swap(s, 'val direct = eligible.filter', 'val direct = ranked.filter')
s = swap(s, '''    LaunchedEffect(sportsKey,sportsRefresh) {
        var forceRefresh=sportsRefresh > 0''','''    var sportsRequested by remember { mutableStateOf(false) }
    LaunchedEffect(sportsKey,sportsRefresh,sportsRequested) {
        if (!sportsRequested && sportsRefresh == 0) return@LaunchedEffect
        var forceRefresh=sportsRefresh > 0''')
s = swap(s, '''    var section by remember { mutableStateOf("Home") }
    val catalogueKind''','''    var section by remember { mutableStateOf("Home") }
    LaunchedEffect(section) { if (section == "Sports Guide") sportsRequested = true }
    val catalogueKind''')
p.write_text(s)
p = root / "PlaybackSettings.kt"
s = p.read_text()
s = swap(s, '''                        // Do not let a known HDR decoder configure a surface in strict SDR mode.
                        // Home playback can recover using its existing source failover.
                        if (hdr) throw java.io.IOException("HDR source blocked by SDR mode; select an SDR source")''', '''                        if (hdr && android.os.Build.VERSION.SDK_INT >= 31) {
                            val mediaFormat = configuration.mediaFormat
                            val key = android.media.MediaFormat.KEY_COLOR_TRANSFER_REQUEST
                            val previous = if (mediaFormat.containsKey(key)) mediaFormat.getInteger(key) else null
                            mediaFormat.setInteger(key, android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                            try {
                                return delegate.createAdapter(configuration)
                            } catch (error: Exception) {
                                android.util.Log.w("SharkPlayback", "SDR conversion request rejected; retrying original format")
                                if (previous == null) mediaFormat.removeKey(key) else mediaFormat.setInteger(key, previous)
                            }
                        }''')
s = swap(s, 'Auto follows the stream. Prefer SDR blocks detected HDR and Dolby Vision video instead of silently switching the TV to HDR. HDR-only titles may not play in SDR mode. Restart playback after changing.', 'Auto follows the stream. Prefer SDR requests HDR-to-SDR conversion on supported Android 12+ decoders and prefers SDR alternatives. Unsupported devices may still display HDR. Prefer HDR favours HDR sources where available. Restart playback after changing.')
p.write_text(s)
p = Path("app/build.gradle.kts")
s = p.read_text()
s = swap(s, 'orElse("40001")', 'orElse("40002")')
s = swap(s, 'orElse("4.0.1")', 'orElse("4.0.2")')
p.write_text(s)
print("4.0.2 changes applied; Performance Hub clear-cache action retained.")
