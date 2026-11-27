import subprocess,time
deadline=time.monotonic()+35
while time.monotonic()<deadline:
 log=subprocess.check_output(['adb','logcat','-d','-s','SharkIntro:I'],text=True,timeout=15)
 if 'home revealed: completed' in log:
  assert 'prepared ' in log and 'started ' in log,log
  print('PASS: cinematic intro played and completed into prepared home')
  break
 if 'home revealed: error' in log or 'home revealed: timeout' in log:
  raise AssertionError(log)
 time.sleep(1)
else:
 raise AssertionError('Intro did not complete: '+log)
