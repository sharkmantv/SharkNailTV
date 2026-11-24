from pathlib import Path
p=Path("app/src/main/java/com/example/xtreamtv/PlaybackSettings.kt")
s=p.read_text()
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected buffer source: "+a[:120]
    return s.replace(a,b,1)
a=s.index("    val memory = context.getSystemService(Context.ACTIVITY_SERVICE)")
b=s.index("    val load = if (preview)",a)
assert "val min=if(lowMemory)" in s[a:b]
s=s[:a]+s[b:]
a=s.index("        // Automatic IPTV buffer policy; keep preview policy separate.")
b=s.index("\n    }\n    return ExoPlayer.Builder",a)
assert ".setBufferDurationsMs(min, max, startMs, rebufferMs)" in s[a:b]
s=s[:a]+"        // Native Media3 default load control for ordinary IPTV playback.\n        DefaultLoadControl.Builder().build()"+s[b:]
s=swap(s,'''                Text("Buffer management · Automatic",color=Color.White)
                Text("Balances startup, recovery and available device memory.",color=Color.LightGray,fontSize=12.sp)
''','')
s=swap(s,'Text("Fallback: ', 'Text("Decoder Fallback: ')
assert s.count(".setBufferDurationsMs(")==1
assert ".setBufferDurationsMs(6_000, 15_000, 1_000, 2_500)" in s
assert "R.layout.shark_sdr_player" in s
assert "EXTENSION_RENDERER_MODE_ON" in s
p.write_text(s)
p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'orElse("40006")','orElse("40007")')
s=swap(s,'orElse("4.0.6")','orElse("4.0.7")')
p.write_text(s)
print("Native Media3 fullscreen buffer defaults restored; tested preview policy, SDR and decoder fallback retained.")
