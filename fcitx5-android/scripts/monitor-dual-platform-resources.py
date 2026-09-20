#!/usr/bin/env python3
"""Read-only PAD/macOS sidecar. Never installs, restarts or changes input focus."""
import argparse
from collections import defaultdict
from datetime import datetime
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

p=argparse.ArgumentParser()
p.add_argument('--run',type=Path,required=True)
p.add_argument('--output',type=Path)
p.add_argument('--serial',default='192.168.3.75:5555')
p.add_argument('--interval',type=float,default=60)
p.add_argument('--vmmap-interval',type=float,default=300)
a=p.parse_args()
out=a.output or a.run/'dual-platform';out.mkdir(exist_ok=False)
adb=['adb','-s',a.serial]
exe='/Applications/KEMI远程办公.app/Contents/MacOS/KEMI远程办公'
started=time.monotonic(); start_wall=datetime.now().astimezone().isoformat()
last_cpu={};last_identity={};last_vmmap={};all_rows=[];events=[];seen_roles=set()


def run(cmd,timeout=30):
 return subprocess.check_output(cmd,text=True,stderr=subprocess.STDOUT,timeout=timeout).strip()


def write(name,row):
 with (out/name).open('a') as f:f.write(json.dumps(row,ensure_ascii=False)+'\n')


def event(kind,**fields):
 row=dict(at=datetime.now().astimezone().isoformat(),kind=kind,**fields)
 events.append(row);write('events.jsonl',row)


def cpu(key,total):
 now=time.monotonic();previous=last_cpu.get(key);last_cpu[key]=(now,total)
 return None if previous is None else round(100*(total-previous[1])/(now-previous[0]),3)


def identity(role,value):
 old=last_identity.get(role)
 if old is not None and old!=value:event('process_identity_changed',role=role,previous=old,current=value)
 last_identity[role]=value


def get(pattern,text):
 m=re.search(pattern,text,re.M)
 if not m:raise ValueError('missing metric '+pattern)
 return int(m.group(1))


def android(pkg):
 role='pad:'+pkg
 pid=run(adb+['shell','pidof',pkg])
 if not pid.isdigit():raise ValueError('expected one PID: '+pid)
 stat=run(adb+['shell','su','0','cat',f'/proc/{pid}/stat']).rsplit(')',1)[1].split()
 identity(role,pid+':'+stat[19])
 status=run(adb+['shell','su','0','cat',f'/proc/{pid}/status'])
 fd=run(adb+['shell','su','0','ls',f'/proc/{pid}/fd']).splitlines()
 mem=run(adb+['shell','dumpsys','meminfo',pkg])
 (out/(pkg+'-latest-meminfo.txt')).write_text(mem)
 native=re.search(r'^\s*Native Heap\s+(.+)$',mem,re.M).group(1).split()
 row=dict(platform='pad',role=role,pid=int(pid),pss_kb=get(r'TOTAL PSS:\s*(\d+)',mem),
          rss_kb=get(r'TOTAL RSS:\s*(\d+)',mem),native_pss_kb=int(native[0]),native_alloc_kb=int(native[6]),
          java_pss_kb=get(r'Java Heap:\s*(\d+)',mem),threads=get(r'^Threads:\s*(\d+)',status),
          fd_count=len(fd),cpu_pct_one_core=cpu(role+':'+pid,(int(stat[11])+int(stat[12]))/hz))
 ash=re.search(r'^\s*Ashmem\s+(\d+)\s+\d+\s+\d+\s+\d+\s+(\d+)',mem,re.M)
 if ash:row.update(ashmem_pss_kb=int(ash[1]),ashmem_rss_kb=int(ash[2]))
 return row


def mac_processes():
 entries=[]
 for line in run(['ps','-axo','pid=,ppid=,command=']).splitlines():
  m=re.match(r'\s*(\d+)\s+(\d+)\s+(.*)',line)
  if m:entries.append((int(m[1]),int(m[2]),m[3]))
 selected={pid for pid,ppid,cmd in entries if cmd==exe or cmd.startswith(exe+' ')}
 while True:
  expanded=selected|{pid for pid,ppid,cmd in entries if ppid in selected}
  if expanded==selected:break
  selected=expanded
 return [e for e in entries if e[0] in selected]


def seconds(value):
 parts=value.split(':');total=0
 for part in parts:total=total*60+float(part)
 return total


def footprint(text):
 m=re.search(r'^Physical footprint:\s*([\d.]+)([KMGT]?)',text,re.M)
 if not m:return None
 return round(float(m[1])*{'':1/1024,'K':1,'M':1024,'G':1024**2,'T':1024**3}[m[2]],3)


def mac(pid,ppid,cmd,force_vmmap):
 args=cmd[len(exe):].strip() if cmd.startswith(exe) else cmd
 role='mac:'+(args or 'main')
 ps=run(['ps','-p',str(pid),'-o','rss=,%cpu=,time=,lstart=']).split(maxsplit=3)
 identity(role,str(pid)+':'+ps[3])
 # macOS ps -M prints one header followed by one row per task thread.
 threads=len(run(['ps','-M','-p',str(pid)]).splitlines())-1
 fds=run(['/usr/sbin/lsof','-a','-p',str(pid),'-F','f'])
 row=dict(platform='mac',role=role,pid=pid,ppid=ppid,command=cmd,rss_kb=int(ps[0]),
          cpu_pct_ps=float(ps[1]),cpu_pct_one_core=cpu(role+':'+str(pid),seconds(ps[2])),
          fd_count=len(re.findall(r'^f\d+',fds,re.M)),threads=threads)
 if force_vmmap or pid not in last_vmmap or time.monotonic()-last_vmmap[pid]>=a.vmmap_interval:
  text=run(['vmmap','-summary',str(pid)],timeout=40)
  name=f'vmmap-{pid}-{int(time.monotonic()-started):06d}.txt';(out/name).write_text(text)
  row.update(physical_footprint_kb=footprint(text),vmmap_file=name)
  last_vmmap[pid]=time.monotonic()
 return row


def sample(force_vmmap=False):
 global seen_roles
 rows=[]
 for pkg in ['com.newlink.kemi.kboard','com.newlinksz.kemi.remote']:
  try:rows.append(android(pkg))
  except Exception as e:event('sample_error',role='pad:'+pkg,error=str(e))
 try:
  procs=mac_processes()
  if not procs:event('process_missing',role='mac:all')
  for pid,ppid,cmd in procs:
   try:rows.append(mac(pid,ppid,cmd,force_vmmap))
   except Exception as e:event('sample_error',role='mac:'+str(pid),error=str(e))
 except Exception as e:event('sample_error',role='mac:discovery',error=str(e))
 present={r['role'] for r in rows}
 for missing in seen_roles-present:event('process_missing',role=missing)
 seen_roles=present
 now=datetime.now().astimezone().isoformat()
 for row in rows:
  row.update(at=now,elapsed_s=round(time.monotonic()-started,3))
  all_rows.append(row);write('resources.jsonl',row)
 heart=dict(status='running',at=now,start=start_wall,elapsed_s=round(time.monotonic()-started,3),
            samples=len(all_rows),events=len(events),roles=sorted({r['role'] for r in rows}))
 (out/'heartbeat.json').write_text(json.dumps(heart,ensure_ascii=False,indent=2))


def summary():
 grouped=defaultdict(list)
 for r in all_rows:grouped[r['role']].append(r)
 metrics={}
 for role,rows in grouped.items():
  stats={}
  for name in ['pss_kb','rss_kb','native_pss_kb','native_alloc_kb','java_pss_kb','physical_footprint_kb',
               'cpu_pct_one_core','cpu_pct_ps','fd_count','threads','ashmem_pss_kb','ashmem_rss_kb']:
   pts=[(r['elapsed_s'],r[name]) for r in rows if isinstance(r.get(name),(int,float))]
   if not pts:continue
   vals=[y for x,y in pts];hours=(pts[-1][0]-pts[0][0])/3600
   xbar=statistics.mean(x for x,y in pts);ybar=statistics.mean(vals)
   den=sum((x-xbar)**2 for x,y in pts)
   slope=None if not den else sum((x-xbar)*(y-ybar) for x,y in pts)/den*3600
   stats[name]=dict(first=vals[0],last=vals[-1],min=min(vals),max=max(vals),median=statistics.median(vals),
                    delta=vals[-1]-vals[0],delta_per_hour=None if not hours else (vals[-1]-vals[0])/hours,
                    regression_per_hour=slope,samples=len(vals))
  metrics[role]=stats
 result=dict(status='completed',start=start_wall,end=datetime.now().astimezone().isoformat(),
             elapsed_s=round(time.monotonic()-started,3),metrics=metrics,events=events,
             note='Sidecar began after IME stress start; report exact coverage. macOS installed v1.4.120(227) predates autoreleasepool source fix.')
 (out/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
 (out/'heartbeat.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))


hz=int(run(adb+['shell','getconf','CLK_TCK']))
(out/'baseline-context.json').write_text(json.dumps(dict(start=start_wall,run=str(a.run),
 source='User requested read-only additional monitoring without restarting apps or interrupting stress',
 mac_binary='/Applications/KEMI远程办公.app',mac_version='1.4.120(227)',
 mac_fix_in_installed_binary=False,initial_manual_footprint_mb=617.0,
 parent_supplied_manual_footprint_mb=627.4,parent_supplied_peak_mb=641.7),ensure_ascii=False,indent=2))
while True:
 sample(force_vmmap=(a.run/'summary.json').exists())
 if (a.run/'summary.json').exists():break
 time.sleep(a.interval)
summary()
