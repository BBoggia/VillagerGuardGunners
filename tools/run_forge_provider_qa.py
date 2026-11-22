"""Run a reobfuscated provider test mod in a fresh Forge 47.4.10 server."""
import argparse
from pathlib import Path
import shutil
import subprocess
import time


def launch(directory, java, suite, marker, log_name):
    log = directory / log_name
    args = [java, '-Xmx2G', '-Dvgg.qa.suite=' + suite,
            '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt', 'nogui']
    with log.open('w') as output, subprocess.Popen(args, cwd=directory, stdin=subprocess.PIPE,
            stdout=output, stderr=subprocess.STDOUT, text=True) as server:
        try:
            deadline = time.monotonic() + 360
            while server.poll() is None and time.monotonic() < deadline:
                lines = log.read_text(errors='replace')
                if marker in lines or '_QA_FAIL case=' in lines:
                    server.stdin.write('stop\n')
                    server.stdin.flush()
                    break
                time.sleep(0.5)
            else:
                if server.poll() is None:
                    raise TimeoutError(log)
            code = server.wait(timeout=45)
        finally:
            if server.poll() is None:
                server.kill()
        lines = log.read_text(errors='replace')
        if (code or marker not in lines or '_QA_FAIL case=' in lines
                or any('/ERROR]' in line and ('co.ga.vi.' in line or 'villagerguardgunners:' in line)
                       for line in lines.splitlines())):
            raise RuntimeError(f'Forge {suite} failed. See {log}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=['cgm', 'pointblank', 'jelly', 'none', 'smoke'], required=True)
    parser.add_argument('--libraries', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--jar', type=Path, action='append', required=True,
                        help='One installed jar. Include the built provider probe for combat suites.')
    parser.add_argument('--java', default='java', help='Java 17 executable')
    parser.add_argument('--accept-eula', action='store_true', required=True)
    args = parser.parse_args()
    directory = args.output.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    mods = directory / 'mods'
    mods.mkdir()
    for jar in args.jar:
        if (mods / jar.name).exists():
            raise ValueError(f'Duplicate jar filename {jar.name}')
        shutil.copy2(jar, mods)
    (directory / 'libraries').symlink_to(args.libraries.resolve(), target_is_directory=True)
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
            'level-type=minecraft:normal\ngenerate-structures=false\nspawn-animals=false\nspawn-monsters=false\n'
            'view-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')
    if args.suite == 'none':
        launch(directory, args.java, args.suite, 'VGG_NO_GUN_QA_PASS', 'guard.log')
        return
    if args.suite == 'smoke':
        launch(directory, args.java, args.suite, 'For help, type "help"', 'startup.log')
        return
    prefix = 'VGG_CGM_QA_' if args.suite == 'cgm' else 'VGG_PB_QA_'
    launch(directory, args.java, args.suite, prefix + 'READY_TO_RESTART', 'combat.log')
    launch(directory, args.java, args.suite, prefix + 'RESTART_PASS', 'restart.log')


if __name__ == '__main__':
    main()
