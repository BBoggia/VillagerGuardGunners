#!/usr/bin/env python3
"""Packaged Quartermaster upgrade and scale acceptance in isolated installed Forge servers."""
import argparse
import json
import shutil
import subprocess
from pathlib import Path
import run_village_compatibility as qa

JAVA = Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java')
BASE = Path('build/release-1.8-server-smoke')


def prepare(out, jar, observer, source=None):
    out.mkdir(parents=True, exist_ok=False)
    (out / 'mods').mkdir()
    for dep in (BASE / 'mods').glob('*.jar'):
        if any(k in row['mods'] for row in qa.jar_metadata(dep) for k in ('guardvillagers', 'tacz', 'fzzy_config', 'kotlinforforge')):
            shutil.copy2(dep, out / 'mods' / dep.name)
    shutil.copy2(jar, out / 'mods' / jar.name)
    shutil.copy2(observer, out / 'mods' / observer.name)
    (out / 'libraries').symlink_to((BASE / 'libraries').resolve(), target_is_directory=True)
    if source:
        for folder in ('world', 'config', 'tacz'):
            if (source / folder).exists():
                shutil.copytree(source / folder, out / folder)
        shutil.copy2(source / 'legacy-state.snbt', out / 'legacy-state.snbt')
    (out / 'eula.txt').write_text('eula=true\n')
    (out / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains","structure_overrides":[]}\n'
        'generate-structures=false\nspawn-animals=false\nspawn-monsters=false\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')


def run_server(out, mode, timeout=240, properties=()):
    hashes={p.name:qa.sha(p) for p in (out/'mods').glob('*.jar')}
    result_path=out/(mode+'-result.json')
    command=[str(JAVA),'-Xmx2G','-Dvgg.acceptance.mode='+mode,
             '-Dvgg.acceptance.output='+str(result_path),*properties,
             '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt','nogui']
    timed_out=False
    with (out/(mode+'-console.log')).open('w') as log:
        process=subprocess.Popen(command,cwd=out,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
        try:
            code=process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out=True
        finally:
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=20)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            code=process.returncode
    result=json.loads(result_path.read_text()) if result_path.exists() else {'passed':False,'failure':'Missing completion marker'}
    passed=not timed_out and code==0 and result['passed'] and hashes=={p.name:qa.sha(p) for p in (out/'mods').glob('*.jar')}
    manifest={'command':command,'jars':hashes,'exitCode':code,'timedOut':timed_out,'passed':passed,'result':result}
    (out/(mode+'-manifest.json')).write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'mode':mode,'passed':passed,'cases':len(result.get('cases',[])),'failure':result.get('failure','')}),flush=True)
    if not passed:raise RuntimeError('Acceptance failed: '+str(result_path))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--legacy-jar',type=Path,default=Path('build/distributions/release-candidate/1.8/villagerguardgunners-1.8.jar'))
    parser.add_argument('--observer',type=Path,default=Path('build/quartermaster-qa/vgg-quartermaster-probe.jar'))
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--scenario',choices=['upgrade','performance'],default='upgrade')
    parser.add_argument('--sizes',type=int,nargs='+',choices=[25,100],default=[25,100],help='Guard populations for performance checks')
    parser.add_argument('--accept-eula',action='store_true')
    args=parser.parse_args()
    if not args.accept_eula:parser.error('--accept-eula required')
    with qa.workload_lock():
        qa.clean_environment();out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
        if args.scenario=='upgrade':
            seed=out/'released';prepare(seed,args.legacy_jar,args.observer);run_server(seed,'legacy')
            before={str(p.relative_to(seed/'world')):qa.sha(p) for p in (seed/'world').rglob('*') if p.is_file()}
            upgraded=out/'upgraded';prepare(upgraded,args.jar,args.observer,seed)
            run_server(upgraded,'upgrade');run_server(upgraded,'upgrade-restart')
            if before!={str(p.relative_to(seed/'world')):qa.sha(p) for p in (seed/'world').rglob('*') if p.is_file()}:raise AssertionError('Released source world changed')
        else:
            for size in dict.fromkeys(args.sizes):
                trial=out/str(size);prepare(trial,args.jar,args.observer)
                run_server(trial,'performance',timeout=2200,properties=['-Dvgg.acceptance.guards='+str(size)])

if __name__=='__main__':main()
