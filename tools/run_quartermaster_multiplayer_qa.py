#!/usr/bin/env python3
"""Launch two isolated real Minecraft clients against one loopback-only packaged Forge server."""
import argparse
import json
import shutil
import socket
import subprocess
import time
import uuid
from pathlib import Path
import run_village_compatibility as qa
from run_quartermaster_acceptance import prepare, JAVA


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--observer',type=Path,default=Path('build/quartermaster-qa/vgg-quartermaster-probe.jar'))
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--accept-eula',action='store_true')
    args=parser.parse_args()
    if not args.accept_eula:parser.error('--accept-eula required')
    with qa.workload_lock():
        qa.clean_environment();out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
        server=out/'server';prepare(server,args.jar,args.observer)
        shutil.copytree('examples/quartermaster-trades',server/'world/datapacks/quartermaster-trades-example')
        with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        properties=server/'server.properties';properties.write_text(properties.read_text().replace('server-port=0','server-port='+str(port))+'enforce-secure-profile=false\n')
        commands={'server':[str(JAVA),'-Xmx2G','-Dvgg.acceptance.mode=multiplayer','-Dvgg.acceptance.output='+str(out/'multiplayer-result.json'),'@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt','nogui']}
        hashes={'server':{p.name:qa.sha(p) for p in (server/'mods').glob('*.jar')}}
        runtime=qa.runtime_info({'_base':str(Path.cwd()),'runtime':{'minecraftDirectory':str(Path.home()/'Library/Application Support/minecraft'),'java':str(JAVA),'version':'1.20.1-forge-47.4.10','forgeVersion':'47.4.10'}})
        for role,name in [('A','VggOwner'),('B','VggPeer')]:
            run=out/role;game=run/'client';game.mkdir(parents=True);shutil.copytree(server/'mods',game/'mods')
            (game/'options.txt').write_text('renderDistance:3\nsimulationDistance:3\nmaxFps:30\npauseOnLostFocus:false\nonboardAccessibility:false\nguiScale:2\ntutorialStep:none\n')
            providers={key:str(j.resolve()) for j in (game/'mods').glob('*.jar') for row in qa.jar_metadata(j) for key in row['mods']};providers['vggcompatprobe']=str((game/'mods'/args.observer.name).resolve())
            command=qa.launch_arguments(runtime,{'providers':providers},{},run)
            command=[a for a in command if not a.startswith('-Dvgg.compat.')]
            i=command.index('--quickPlaySingleplayer');del command[i:i+2]
            for flag,value in [('--username',name),('--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,name).hex),('--width','960'),('--height','720')]:command[command.index(flag)+1]=value
            command=[a.replace('-Xmx4G','-Xmx2G') for a in command]
            command[1:1]=['-Dvgg.multiplayer.role='+role,'-Dvgg.multiplayer.shared='+str(out),'-Dvgg.multiplayer.address=127.0.0.1:'+str(port)]
            commands[role]=command;hashes[role]={p.name:qa.sha(p) for p in (game/'mods').glob('*.jar')}
        manifest={'commands':commands,'jars':hashes,'passed':False};(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        processes={};logs=[]
        try:
            for role in ('server','A','B'):
                log=(out/(role+'.log')).open('w');logs.append(log)
                processes[role]=subprocess.Popen(commands[role],cwd=server if role=='server' else out/role/'client',stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
                if role=='server':
                    deadline=time.monotonic()+90
                    while 'For help, type "help"' not in (out/'server.log').read_text(errors='replace'):
                        if processes[role].poll() is not None or time.monotonic()>deadline:raise RuntimeError('Server startup failed')
                        time.sleep(.5)
            deadline=time.monotonic()+360
            while any(p.poll() is None for p in processes.values()) and time.monotonic()<deadline:time.sleep(.5)
            result=json.loads((out/'multiplayer-result.json').read_text()) if (out/'multiplayer-result.json').exists() else {'passed':False,'failure':'No server completion marker'}
            unchanged=all(hashes[role]=={p.name:qa.sha(p) for p in ((server if role=='server' else out/role/'client')/'mods').glob('*.jar')} for role in processes)
            manifest.update(result=result,exits={r:p.poll() for r,p in processes.items()},jarsUnchanged=unchanged,passed=result['passed'] and unchanged and all(p.poll()==0 for p in processes.values()))
            (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps({'passed':manifest['passed'],'result':result}),flush=True)
            return 0 if manifest['passed'] else 1
        finally:
            for p in processes.values():
                if p.poll() is None:
                    p.terminate()
                    try:p.wait(timeout=20)
                    except subprocess.TimeoutExpired:p.kill();p.wait()
            for log in logs:log.close()

if __name__=='__main__':raise SystemExit(main())
