#!/usr/bin/env bash
set -euo pipefail
src=generated-video.mp4
test -s "$src"
mkdir -p app/src/main/res/raw
python3 - <<'PY'
import json,subprocess
r=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json','generated-video.mp4']))
v=next(s for s in r['streams'] if s['codec_type']=='video')
assert (v['width'],v['height'])==(768,448),v
assert 4.9<float(r['format']['duration'])<5.2
assert any(s['codec_type']=='audio' for s in r['streams'])
PY
master="$RUNNER_TEMP/shark-intro-ai.mkv"
python -u ci/enhance_intro_frames.py "$src" "$master" "$1"
ffmpeg -hide_banner -loglevel error -y -i "$master" -vf 'framerate=fps=60,scale=3840:2160:flags=lanczos,setsar=1' -map 0:v:0 -map 0:a:0 -c:v libx264 -preset fast -crf 20 -maxrate 35M -bufsize 35M -pix_fmt yuv420p -profile:v high -level:v 5.2 -c:a aac -b:a 160k -movflags +faststart app/src/main/res/raw/shark_intro_uhd.mp4
ffmpeg -hide_banner -loglevel error -y -i "$master" -vf 'scale=1920:1080:flags=lanczos,setsar=1' -map 0:v:0 -map 0:a:0 -c:v libx264 -preset fast -crf 20 -pix_fmt yuv420p -profile:v main -level:v 4.0 -c:a aac -b:a 128k -movflags +faststart app/src/main/res/raw/shark_intro_hd.mp4
python3 - <<'PY'
import json,subprocess
for name,w,h,rate in [('uhd',3840,2160,'60/1'),('hd',1920,1080,'24/1')]:
 p=f'app/src/main/res/raw/shark_intro_{name}.mp4'
 r=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',p]))
 v=next(s for s in r['streams'] if s['codec_type']=='video')
 assert (v['width'],v['height'],v['avg_frame_rate'])==(w,h,rate),v
 assert 4.8<float(r['format']['duration'])<5.2
 assert any(s['codec_type']=='audio' for s in r['streams'])
 print('PASS:',p,w,h,rate)
PY
