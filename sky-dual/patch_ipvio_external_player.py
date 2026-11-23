#!/usr/bin/env python3
"""Make a separate SKY / IPVIO Media3 player available to Android's video intent chooser.

Original IPTV players and Xtream login remain untouched.
"""
from pathlib import Path
root=Path("ipvio/app/src/main")
manifest=root/"AndroidManifest.xml"
s=manifest.read_text()
anchor='''        <activity
            android:name=".RestartActivity"'''
assert s.count(anchor)==1
s=s.replace(anchor,'''        <!-- This optional activity makes the existing IPVIO Media3 engine selectable
             as an external video player alongside VLC. -->
        <activity
            android:name=".SkyExternalPlayerActivity"
            android:exported="true"
            android:label="SKY Media3 1.11.1"
            android:configChanges="keyboardHidden|orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
                <data android:scheme="content" />
                <data android:mimeType="video/*" />
            </intent-filter>
        </activity>
'''+anchor,1)
manifest.write_text(s)
dst=root/"java/com/ipvio/stream/SkyExternalPlayerActivity.kt"
dst.write_text(Path("sky-dual/SkyExternalPlayerActivity.kt").read_text())
print("Added external ACTION_VIEW video handler with real IPVIO Media3 1.11.1 engine")
