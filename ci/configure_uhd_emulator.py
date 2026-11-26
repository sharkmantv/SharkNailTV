"""Set the emulator's physical framebuffer to UHD before boot (portrait hardware).
The app forces landscape orientation; wm reset then exposes 3840x2160, not
an oversized logical override on a 1080p framebuffer.
"""
import os
from pathlib import Path
root=Path(os.environ.get("ANDROID_AVD_HOME",str(Path.home()/".android/avd")))
config=root/"test.avd"/"config.ini"
if not config.is_file():
    ini=root/"test.ini"
    path=next((line.split("=",1)[1] for line in ini.read_text().splitlines() if line.startswith("path=")),None)
    assert path,ini
    config=Path(path)/"config.ini"
assert config.is_file(),config
settings={"hw.lcd.width":"2160","hw.lcd.height":"3840","hw.lcd.density":"320","skin.name":"2160x3840","skin.path":"_no_skin","showDeviceFrame":"no","hw.initialOrientation":"portrait"}
lines=[line for line in config.read_text().splitlines() if line.split("=",1)[0].strip() not in settings]
config.write_text("\n".join(lines+[f"{k}={v}" for k,v in settings.items()])+"\n")
print("Physical UHD emulator configuration:",config,settings)
