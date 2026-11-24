from pathlib import Path
import re
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected profile source: "+a[:140]
    return s.replace(a,b,1)
p=root/"PlaybackSettings.kt"
s=p.read_text()
s=swap(s,'    sharkMega: Boolean = false,\n','')
s=swap(s,'    val min=when(p.getString("buffer","Normal")){"Small"->10000;"Large"->50000;else->if (live && lowMemory) 12_000 else 25_000}', '    val min=if(lowMemory) 12_000 else 25_000')
a=s.index('        // A fixed 128 MB allocation on Shark Mega')
b=s.index('        DefaultLoadControl.Builder()',a)
s=s[:a]+'''        // Automatic IPTV buffer policy; keep preview policy separate.
        val max=if(lowMemory) 30_000 else 60_000
        val startMs=if(live) 1_000 else 1_500
        val rebufferMs=if(live) 3_000 else 3_500
'''+s[b:]
s=swap(s,'.setPrioritizeTimeOverSizeThresholds(!(live && lowMemory && p.getString("buffer","Normal") != "Large"))','.setPrioritizeTimeOverSizeThresholds(!lowMemory)')
s=swap(s,'    var smartMode by remember { mutableStateOf(p.getString("smartMode","Shark Smart").orEmpty()) }\n','')
s=swap(s,'    var buffer by remember { mutableStateOf(p.getString("buffer","Normal").orEmpty()) }\n','')
a=s.index('                SmartPlayback.modes.forEach')
b=s.index('                Text("Shark Player"',a)
s=s[:a]+s[b:]
a=s.index('                Text("Buffer",color=Color.White)')
b=s.index('                PremiumButton(compact=true,onClick={fallback=',a)
s=s[:a]+'''                Text("Buffer management · Automatic",color=Color.White)
                Text("Balances startup, recovery and available device memory.",color=Color.LightGray,fontSize=12.sp)
'''+s[b:]
s=swap(s,';smartMode="Shark Smart";buffer="Normal";',';')
assert "sharkMega" not in s and "smartMode" not in s
p.write_text(s)

p=root/"MainActivity.kt"
s=p.read_text()
a=s.index('        if (playback.contains("nuvioSharkMega")) {')
b=s.index('        if(server.isNotBlank()',a)
s=s[:a]+'''        playback.edit().remove("nuvioSharkMega").remove("sharkMega").remove("smartMode").remove("buffer").apply()
'''+s[b:]
a=s.index('    var sharkMega by remember {')
b=s.index('\n    }',a)+len('\n    }\n')
s=s[:a]+s[b:]
a=s.index('                val results = if (sharkMega)')
b=s.index('                val cm=',a)
s=s[:a]+s[b:]
s=swap(s,'                val rankingMode = playbackPrefs(context).getString("smartMode", "Shark Smart").orEmpty()\n','')
s=swap(s,'                    results, rankingMode, kbps,','                    matches, kbps,')
s, count = re.subn(r",\s*sharkMega\s*=\s*sharkMega\b(?!\s*,\s*live\s*=)", "", s)
assert count == 3, f"Expected three SafePlayer profile arguments, found {count}"
s=swap(s,',sharkMega:Boolean=false,onFirstFrame',',onFirstFrame')
s=swap(s,'remember(context, sharkMega, liveStream) { configuredPlayer(context, sharkMega = sharkMega, live = liveStream) }','remember(context, liveStream) { configuredPlayer(context, live = liveStream) }')
s=swap(s,'Profile: '+chr(36)+'{if(sharkMega) "Shark Mega" else "Standard"}','Profile: Automatic IPTV')
a=s.index('                            FocusAction("Playback profile')
b=s.index('                            FocusAction("Back to Settings")',a)
assert "Shark Mega starts playback" in s[a:b]
s=s[:a]+s[b:]
assert "Shark Mega" not in s
assert not any('sharkMega' in l for l in s.splitlines() if 'playback.edit().remove(' not in l)
p.write_text(s)
p=root/"SmartPlayback.kt"
s=p.read_text()
s=swap(s,'    val modes = listOf("Shark Turbo", "Shark Smart", "Shark Ultra 4K")\n','')
s=swap(s,'mode:String, kbps:Int','kbps:Int')
s=swap(s,'            mode == "Shark Turbo" -> 1080\n            mode == "Shark Ultra 4K" -> 2160\n','')
a=s.index('    fun preferHardwareDecoders(')
b=s.index('    fun height(',a)
s=s[:a]+s[b:]
p.write_text(s)
p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'orElse("40005")','orElse("40006")')
s=swap(s,'orElse("4.0.5")','orElse("4.0.6")')
p.write_text(s)
print("Obsolete profiles retired; automatic IPTV buffer policy, hardware decoding, SDR and preview fixes preserved.")
