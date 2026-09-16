#!/usr/bin/env python3
"""Run repeatable guard gameplay scenarios using packaged mods and a real Forge client."""
import argparse
import json
import random
from pathlib import Path
import shutil
import socket
import subprocess
import time
import zipfile
import create_flat_sandbox
import run_village_compatibility as qa
from run_guard_firing_qa import test_guns

ROOT = Path(__file__).resolve().parents[1]


def scenarios(suite):
    rows = []
    def add(**kw):
        row = dict(gun='tacz:ak47', mode='AUTO', target='player', motion='stationary',
                   point='CENTER_MASS', attachment='', distance=16, guards=1, seed=1,
                   duration=160, range=32, error=0, override=True)
        row.update(kw)
        row['id'] = f'{suite}-{len(rows):03d}'
        rows.append(row)
    if suite == 'pilot':
        add(point='EYE_LEVEL')
        add(error=3)
        add(error=3, target='husk')
        add(error=3, motion='owner', duration=80)
        add(guards=3,error=2)
    elif suite == 'core':
        for seed in (11, 22, 33):
            for distance in (8,16,32):
                add(seed=seed,distance=distance,point='EYE_LEVEL')
                for error in (0,1,2,3):
                    add(seed=seed,distance=distance,error=error)
    elif suite == 'groups':
        for seed in (11,22,33):
            for count in (1,3,6):
                for error in (0,2):
                    add(seed=seed,guards=count,error=error)
        for seed in (11,22,33):
            for error in (0,3):
                add(seed=seed,target='husk',error=error)
    elif suite == 'protection':
        add(point='EYE_LEVEL')
        add(error=3)
        add(error=3,target='husk')
        for motion in ('owner','creative','spectator'):
            add(motion=motion,error=3,duration=80)
        for motion in ('creative-switch','spectator-switch'):
            add(motion=motion,gun='tacz:m16a4',mode='BURST',error=3,duration=80)
    elif suite == 'situations':
        for motion in ('strafe','approach','crouch','partial-cover','occlusion','elevated','guard-high'):
            for error in (0,3):
                add(motion=motion,error=error,distance=32 if motion=='approach' else 16)
        for motion in ('owner','creative','spectator'):
            add(motion=motion,error=3,duration=80)
        for motion in ('creative-switch','spectator-switch'):
            add(motion=motion,gun='tacz:m16a4',mode='BURST',error=3,duration=80)
        for motion in ('ally-beside','ally-crossing','ally-behind','ally-lane'):
            for error in (0,3):
                add(motion=motion,error=error,duration=100)
        for motion in ('live-config','hold-fire','disable'):
            add(motion=motion,error=3)
        add(motion='target-switch',target='husk',error=3,override=False)
        for gun,mode in [('glock_17','SEMI'),('m16a4','BURST'),('ump45','AUTO'),('m700','SEMI'),('aa12','AUTO'),('rpg7','SEMI')]:
            for error in (0,3):
                add(gun='tacz:'+gun,mode=mode,error=error,distance=24 if gun=='rpg7' else 16)
        add(gun='tacz:rpg7',mode='SEMI',motion='near-wall',distance=24,error=0,duration=100)
        for error in (0,3):
            add(error=error,attachment='tacz:scope_acog_ta31')
            add(error=error,distance=48,range=64)
            add(error=error,gun='vgg_aim_qa:charge',mode='SEMI')
    else:
        raise ValueError(suite)
    if suite in ('core','groups'): random.Random(7105).shuffle(rows)
    return rows


def run(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    rows = scenarios(args.suite)
    (out/'scenarios.json').write_text(json.dumps(rows,indent=2)+'\n')
    providers = {}
    expected = {'guardvillagers','tacz','fzzy_config','kotlinforforge'}
    for jar in args.mods.glob('*.jar'):
        for key in {key for row in qa.jar_metadata(jar) for key in row['mods']} & expected:
            if key in providers: raise ValueError('Duplicate dependency '+key)
            providers[key] = str(jar.resolve())
    if set(providers) != expected: raise ValueError('Missing dependencies')
    providers['villagerguardgunners'] = str(args.jar.resolve())
    observer = ROOT/'build/accuracy-qa/vgg-accuracy-probe.jar'
    providers['vggcompatprobe'] = str(observer)
    with zipfile.ZipFile(args.jar) as jar:
        if any(x.startswith('accuracyprobe/') for x in jar.namelist()): raise ValueError('Observer in production jar')
    dirs = [out/'client'] + ([out/'server'] if args.dedicated else [])
    for directory in dirs:
        (directory/'mods').mkdir(parents=True)
        for value in providers.values():
            path=Path(value);shutil.copy2(path,directory/'mods'/path.name)
        test_guns(Path(providers['tacz']),directory)
    create_flat_sandbox.create(args.template.resolve(),out/'client/saves/Compatibility')
    (out/'client/options.txt').write_text('renderDistance:3\nsimulationDistance:5\npauseOnLostFocus:false\nmaxFps:30\nenableVsync:false\nonboardAccessibility:false\nautoJump:false\n')
    runtime=qa.runtime_info({'_base':str(ROOT),'runtime':{'minecraftDirectory':str(args.minecraft.resolve()),
        'java':str(args.java),'version':'1.20.1-forge-47.4.10','forgeVersion':'47.4.10'}})
    command=qa.launch_arguments(runtime,{'providers':providers},{},out)
    command=[x for x in command if not x.startswith('-Dvgg.compat.')]
    properties=['-Dvgg.balance.dir='+str(out),'-Dvgg.balance.scenarios='+str(out/'scenarios.json')]
    command[1:1]=properties
    server_command=None
    if args.dedicated:
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        server=out/'server';(server/'libraries').symlink_to(args.libraries.resolve(),target_is_directory=True)
        (server/'eula.txt').write_text('eula=true\n')
        (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=4\nsimulation-distance=4\nmax-tick-time=120000\n')
        server_command=[str(args.java),'-Xmx2G',*properties,'@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt','nogui']
        i=command.index('--quickPlaySingleplayer');command[i:i+2]=['--quickPlayMultiplayer',f'127.0.0.1:{port}']
    hashes={p.name:qa.sha(p) for p in (out/'client/mods').glob('*.jar')}
    manifest={'mode':'dedicated' if args.dedicated else 'integrated','suite':args.suite,'expectedEncounters':len(rows),
        'jars':hashes,'clientCommand':command,'serverCommand':server_command,'runtime':runtime,
        'scenariosSha256':qa.sha(out/'scenarios.json'),'runnerSha256':qa.sha(Path(__file__))}
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    server_process=None;client_process=None
    server_log=(out/'server.log').open('w');client_log=(out/'client.log').open('w')
    try:
        if args.dedicated:
            server_process=subprocess.Popen(server_command,cwd=out/'server',stdout=server_log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True)
            deadline=time.monotonic()+120
            while time.monotonic()<deadline:
                if server_process.poll() is not None: raise RuntimeError('Server exited before ready')
                if 'For help, type "help"' in (out/'server.log').read_text(errors='replace'): break
                time.sleep(.5)
            else: raise TimeoutError('Server startup')
        client_process=subprocess.Popen(command,cwd=out/'client',stdout=client_log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+args.timeout
        while client_process.poll() is None and time.monotonic()<deadline: time.sleep(1)
        if client_process.poll() is None: raise TimeoutError('Client scenario suite')
        if server_process:
            server_process.stdin.write('stop\n');server_process.stdin.flush();server_process.wait(timeout=30)
        result=json.loads((out/'complete.json').read_text()) if (out/'complete.json').exists() else {}
        passed=client_process.returncode==0 and (server_process is None or server_process.returncode==0) and result.get('completed') and result.get('encounters')==len(rows)
        for directory in dirs:
            if hashes!={p.name:qa.sha(p) for p in (directory/'mods').glob('*.jar')}: passed=False
        manifest.update(passed=bool(passed),result=result,clientExitCode=client_process.returncode,
                        serverExitCode=server_process.returncode if server_process else None)
        (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        print(json.dumps({'passed':bool(passed),'encounters':result.get('encounters'),'output':str(out)}),flush=True)
        return 0 if passed else 1
    finally:
        for proc in (client_process,server_process):
            if proc and proc.poll() is None:
                proc.terminate()
                try:proc.wait(timeout=20)
                except subprocess.TimeoutExpired:proc.kill();proc.wait()
        client_log.close();server_log.close()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar',type=Path,required=True);p.add_argument('--mods',type=Path,required=True)
    p.add_argument('--template',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--suite',choices=['pilot','core','groups','protection','situations'],default='pilot')
    p.add_argument('--dedicated',action='store_true');p.add_argument('--accept-eula',action='store_true')
    p.add_argument('--libraries',type=Path);p.add_argument('--timeout',type=int,default=1800)
    p.add_argument('--minecraft',type=Path,default=Path.home()/'Library/Application Support/minecraft')
    p.add_argument('--java',type=Path,default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    args=p.parse_args()
    if args.dedicated and (not args.libraries or not args.accept_eula):p.error('Dedicated runs require --libraries and --accept-eula')
    with qa.workload_lock():
        qa.clean_environment()
        return run(args)


if __name__=='__main__':raise SystemExit(main())
