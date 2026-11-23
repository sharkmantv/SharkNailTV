#!/usr/bin/env python3
"""Sky retro UI test patch for public IPVIO Stream sources. Keep Media3, Xtream and zap logic untouched."""
from pathlib import Path
root=Path("ipvio")
pkg=root/"app/src/main/java/com/ipvio/stream"
home=pkg/"HomeScreen.kt"
s=home.read_text()
anchor='''    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
'''
assert s.count(anchor)==1
s=s.replace(anchor,'''    // SKY RETRO only replaces the home surface; native IPVIO Live TV, VOD and player remain intact.
    if (tab == 0) {
        SkyRetroHome(onTab = onTab, onAdd = onAdd)
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
''',1)
home.write_text(s)
look=pkg/"Look.kt"
s=look.read_text()
for old,new in [
('const val BRAND_GREEN = 0x4ADE80','const val BRAND_GREEN = 0x176CDE'),
('const val BRAND_GREEN_HI = 0x86EFAC','const val BRAND_GREEN_HI = 0xFFD329'),
('const val BRAND_NAVY = 0x0B0F19','const val BRAND_NAVY = 0x030B22'),
('BgPreset("IPVIO Navy", BRAND_NAVY, 0x151B2A, 0x1F2739)','BgPreset("IPVIO Navy", BRAND_NAVY, 0x091A38, 0x123266)')]:
  assert old in s,old
  s=s.replace(old,new,1)
look.write_text(s)
ui=pkg/"Ui.kt";s=ui.read_text()
for a,b in [('val RadiusXs = 8.dp','val RadiusXs = 2.dp'),('val RadiusS = 12.dp','val RadiusS = 2.dp'),('val RadiusM = 16.dp','val RadiusM = 2.dp'),('val RadiusL = 20.dp','val RadiusL = 2.dp'),('val RadiusXl = 24.dp','val RadiusXl = 2.dp')]:
  assert a in s,a
  s=s.replace(a,b,1)
ui.write_text(s)
grad=root/"app/build.gradle.kts";s=grad.read_text()
# Test build uses stable Android 36 SDK while leaving the Media3 player source unmodified.
assert "version = release(37)" in s
s=s.replace("version = release(37)", "version = release(36)", 1)
assert "targetSdk = 37" in s
s=s.replace("targetSdk = 37","targetSdk = 36",1)
assert 'applicationId = "com.ipvio.stream"' in s
s=s.replace('applicationId = "com.ipvio.stream"','applicationId = "tv.sharknail.skyretro.ipvio"',1)
s=s.replace('manifestPlaceholders["appLabel"] = "IPVIO Stream"','manifestPlaceholders["appLabel"] = "SKY RETRO IPVIO"')
grad.write_text(s)
print("Applied IPVIO SKY home + blue/gold square UI; native player code unchanged.")
