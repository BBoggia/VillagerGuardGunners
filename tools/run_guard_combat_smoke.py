#!/usr/bin/env python3
"""Check saved aiming settings and ordinary console-command combat without an observer mod."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import tomllib
import run_village_compatibility as qa


def run(args):
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
    (out/'mods').mkdir();(out/'libraries').symlink_to(args.libraries.resolve(),target_is_directory=True)
    expected={'guardvillagers','tacz','fzzy_config','kotlinforforge'};found=set()
    for jar in args.mods.glob('*.jar'):
        ids={key for row in qa.jar_metadata(jar) for key in row['mods']} & expected
        if ids:
            if found & ids:raise ValueError('Duplicate dependency')
            found.update(ids);shutil.copy2(jar,out/'mods'/jar.name)
    if found!=expected:raise ValueError('Missing dependency')
    shutil.copy2(args.jar,out/'mods'/args.jar.name)
    config=out/'config/villagerguardgunners/guard_gunners.toml';config.parent.mkdir(parents=True)
    text=args.config.read_text().replace('errorDegrees = 0.0','errorDegrees = 0.5')
    text=text.replace('aimPoint = "EYE_LEVEL"','aimPoint = "CENTER_MASS"').replace('overridePlayers = false','overridePlayers = true')
    text=text.replace('playerErrorDegrees = 2.0','playerErrorDegrees = 3.5')
    config.write_text(text);before=tomllib.loads(text)
    assert before['combat']['aiming']=={'errorDegrees':.5,'aimPoint':'CENTER_MASS','overridePlayers':True,'playerErrorDegrees':3.5,'playerAimPoint':'CENTER_MASS'}
    (out/'eula.txt').write_text('eula=true\n')
    (out/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\n')
    jars={p.name:qa.sha(p) for p in (out/'mods').glob('*.jar')}
    command=[str(args.java),'-Xmx2G','@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt','nogui']
    results=[]
    for iteration in range(2):
        logpath=out/f'boot-{iteration}.log'
        with logpath.open('w') as log:
            proc=subprocess.Popen(command,cwd=out,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            def send(command):proc.stdin.write(command+'\n');proc.stdin.flush()
            def wait_for(value,timeout=120):
                deadline=time.monotonic()+timeout
                while proc.poll() is None and time.monotonic()<deadline:
                    if value in logpath.read_text(errors='replace'):return
                    time.sleep(.25)
                raise RuntimeError('Missing server response: '+value)
            try:
                wait_for('For help, type "help"')
                if iteration==0:
                    send('gamerule doMobSpawning false');send('difficulty normal');send('forceload add -16 -16 32 16');time.sleep(2)
                    send('fill -4 79 -4 24 79 4 minecraft:stone')
                    send('summon guardvillagers:guard 0.5 80 0.5 {Tags:["qa_guard"],PersistenceRequired:1b,Invulnerable:1b,"villagerguardgunners:orders":{Version:1,Movement:"HOLD_POSITION",Engagement:"GUARD_TARGETS"},HandItems:[{id:"tacz:modern_kinetic_gun",Count:1b,tag:{GunId:"tacz:ak47",GunFireMode:"AUTO",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}},{}]}')
                    send('summon minecraft:husk 16.5 80 0.5 {Tags:["qa_enemy"],PersistenceRequired:1b,NoAI:1b}')
                    time.sleep(8)
                    send('execute unless entity @e[tag=qa_enemy] run say QA_OPEN_KILLED')
                    send('say QA_OPEN_AMMO_START');send('data get entity @e[tag=qa_guard,limit=1] HandItems[0].tag.GunCurrentAmmoCount');send('say QA_OPEN_AMMO_END')
                    wait_for('QA_OPEN_AMMO_END',10)
                    segment=logpath.read_text().split('QA_OPEN_AMMO_START',1)[1].split('QA_OPEN_AMMO_END',1)[0]
                    values=re.findall(r'following entity data: (\d+)',segment)
                    assert values and int(values[-1])<30,'Open combat did not consume ammo'
                    assert 'QA_OPEN_KILLED' in logpath.read_text(),'Guard did not defeat target'
                    send('data modify entity @e[tag=qa_guard,limit=1] HandItems[0].tag.GunCurrentAmmoCount set value 30')
                    send('data modify entity @e[tag=qa_guard,limit=1] HandItems[0].tag.HasBulletInBarrel set value 1b')
                    send('summon minecraft:villager 8.5 80 0.5 {Tags:["qa_ally"],PersistenceRequired:1b,NoAI:1b}')
                    send('summon minecraft:husk 16.5 80 0.5 {Tags:["qa_enemy"],PersistenceRequired:1b,NoAI:1b}')
                    time.sleep(5)
                    send('say QA_BLOCKED_START');send('data get entity @e[tag=qa_guard,limit=1] HandItems[0].tag.GunCurrentAmmoCount')
                    send('data get entity @e[tag=qa_ally,limit=1] Health');send('execute if entity @e[tag=qa_enemy] run say QA_BLOCKED_ENEMY_ALIVE');send('say QA_BLOCKED_END')
                    wait_for('QA_BLOCKED_END',10)
                    segment=logpath.read_text().split('QA_BLOCKED_START',1)[1].split('QA_BLOCKED_END',1)[0]
                    values=re.findall(r'following entity data: ([\d.]+)f?',segment)
                    assert len(values)==2 and list(map(float,values))==[30,20],('Blocked ammo/health',values)
                    assert 'QA_BLOCKED_ENEMY_ALIVE' in segment
                    send('kill @e[tag=qa_guard]');send('kill @e[tag=qa_ally]');send('kill @e[tag=qa_enemy]')
                send('stop');assert proc.wait(timeout=30)==0
                after=tomllib.loads(config.read_text());assert after==before,'Saved configuration changed on startup/restart'
                results.append({'boot':iteration,'settings':after['combat']['aiming'],'passed':True})
            finally:
                if proc.poll() is None:
                    proc.terminate()
                    try:proc.wait(timeout=15)
                    except subprocess.TimeoutExpired:proc.kill();proc.wait()
    assert jars=={p.name:qa.sha(p) for p in (out/'mods').glob('*.jar')}
    result={'passed':True,'observerInstalled':False,'jars':jars,'boots':results,'openCombatKilledTarget':True,'blockedAmmoUnchanged':True,'allyHealth':20}
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ('jar','mods','libraries','config','output'):p.add_argument('--'+key,type=Path,required=True)
    p.add_argument('--accept-eula',action='store_true')
    p.add_argument('--java',type=Path,default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    a=p.parse_args()
    if not a.accept_eula:p.error('--accept-eula is required')
    with qa.workload_lock():qa.clean_environment();run(a)


if __name__=='__main__':main()
