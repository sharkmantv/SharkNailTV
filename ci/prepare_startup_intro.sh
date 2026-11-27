#!/usr/bin/env bash
set -euo pipefail
src=Sky_Shark_Intro_4K_Upload.mp4
test "$(git hash-object "$src")" = 552ab575336711315ec15168362219b4fb9fe3d6
mkdir -p app/src/main/res/raw
# Keep the exact approved 4K master, with no extra crop, AI pass or re-encoding.
cp "$src" app/src/main/res/raw/shark_intro_uhd.mp4
cmp "$src" app/src/main/res/raw/shark_intro_uhd.mp4
# A lighter H.264/AAC fallback for older TVs and sticks; retain the audio stream.
ffmpeg -hide_banner -loglevel error -y -i "$src" -vf 'fps=30,scale=1920:1080:flags=lanczos,setsar=1' -map 0:v:0 -map 0:a:0 -c:v libx264 -preset fast -crf 20 -pix_fmt yuv420p -profile:v main -level:v 4.0 -c:a copy -movflags +faststart app/src/main/res/raw/shark_intro_hd.mp4
python3 - <<'PY'
import json,subprocess
for name,w,h,rate in [('uhd',3840,2160,'60/1'),('hd',1920,1080,'30/1')]:
 p=f'app/src/main/res/raw/shark_intro_{name}.mp4'
 r=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',p]))
 v=next(s for s in r['streams'] if s['codec_type']=='video')
 assert (v['width'],v['height'],v['avg_frame_rate'],v['codec_name'])==(w,h,rate,'h264'),v
 assert 4.9<float(r['format']['duration'])<5.2
 assert any(s['codec_type']=='audio' and s['codec_name']=='aac' for s in r['streams'])
 print('PASS:',p,w,h,rate,'audio present')
PY
