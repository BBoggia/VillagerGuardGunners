#!/usr/bin/env python3
"""Run Quartermaster fixtures on an ordinary installed Forge dedicated server."""
import argparse
import json
import subprocess
from pathlib import Path
import run_guard_firing_qa as runtime
import run_village_compatibility as qa

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('jar', 'mods', 'libraries', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--observer', type=Path, default=Path('build/quartermaster-qa/vgg-quartermaster-probe.jar'))
    parser.add_argument('--java', type=Path, default=Path('/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java'))
    parser.add_argument('--timeout', type=int, default=180)
    parser.add_argument('--accept-eula', action='store_true')
    parser.add_argument('--extra-mod', type=Path, action='append', default=[],
                        help='Additional installed compatibility JAR; repeat for dependencies')
    parser.add_argument('--config-file', type=Path, action='append', default=[],
                        help='Copy a configuration file into the isolated config directory')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('--accept-eula is required for the isolated server')
    args.mode = 'quartermaster'
    with qa.workload_lock():
        qa.clean_environment()
        code = runtime.run(args)
        if code:
            return code
        return restart(args)

def restart(args):
    out = args.output.resolve()
    before = {p.name: qa.sha(p) for p in (out / 'mods').glob('*.jar')}
    command = [str(args.java), '-Xmx2G', '-Dvgg.quartermaster.restart=true',
               '-Dvgg.quartermaster.output=' + str(out / 'restart-result.json'),
               '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt', 'nogui']
    with (out / 'restart-console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=out, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=args.timeout)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    result = json.loads((out / 'restart-result.json').read_text())
    unchanged = before == {p.name: qa.sha(p) for p in (out / 'mods').glob('*.jar')}
    passed = code == 0 and unchanged and result['passed']
    (out / 'restart-manifest.json').write_text(json.dumps({'command': command, 'jars': before,
        'exitCode': code, 'jarsUnchanged': unchanged, 'passed': passed}, indent=2) + '\n')
    print(json.dumps({'restartPassed': passed, 'cases': len(result['cases']), 'failure': result['failure']}), flush=True)
    return 0 if passed else 1

if __name__ == '__main__':
    raise SystemExit(main())
