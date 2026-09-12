#!/usr/bin/env python3
"""Summarize one production matrix without treating partial evidence as release qualification."""
import argparse
import json
from pathlib import Path
from run_village_compatibility import atomic_json, evaluate, json_hash, sha, case_runs


def report(output, candidate):
    output = Path(output)
    prepared = json.loads((output / 'preflight.json').read_text())['cases']
    matrix_path = output / 'matrix.json'
    if not matrix_path.exists():
        return {'schema': 1, 'candidateSha256': candidate, 'allConfiguredCasesPassed': False,
                'cases': [], 'scopeError': 'Missing full matrix inventory; legacy preflight alone cannot establish completeness'}
    matrix = json.loads(matrix_path.read_text())
    if json_hash(matrix['cases']) != matrix['caseHash']:
        raise ValueError('Matrix inventory hash mismatch')
    rows = []
    for case in matrix['cases']:
        name = case['name']
        if name not in prepared:
            rows.append({'case': name, 'status': 'missing'})
            continue
        identity, digest = prepared[name]
        row = {'case': name, 'status': 'missing'}
        jars = identity['jars']
        source = jars['providers']['villagerguardgunners']
        hashes = [item['sha256'] for item in jars['files'] if item['source'] == source]
        if identity['case'] != case or hashes != [candidate] or json_hash(identity) != digest:
            row['status'] = 'candidate_mismatch'
            rows.append(row)
            continue
        for directory in case_runs(output, name):
            try:
                manifest = json.loads((directory / 'manifest.json').read_text())
            except (OSError, ValueError):
                row.update(status='unverified_evidence', run=str(directory.resolve()))
                break
            if manifest.get('identityHash') != digest:
                continue
            row['run'] = str(directory.resolve())
            row['status'] = manifest.get('status', 'incomplete')
            row['runStatus'] = row['status']
            if manifest.get('error'):
                row['errorType'] = manifest['error'].split(':', 1)[0]
            evidence = manifest.get('evidence', {})
            if manifest.get('identity') != identity or not all(
                    key in evidence for key in ('result.json', 'world-evidence.json', 'config-evidence.json')):
                row['status'] = 'unverified_evidence'
            elif not all(Path(key).name == key and (directory / key).is_file()
                         and sha(directory / key) == value for key, value in evidence.items()):
                row['status'] = 'changed_evidence'
            else:
                checked = evaluate(directory)
                row['failedAssertions'] = [item for item in checked['assertions'] if not item['passed']]
                if row['status'] == 'passed' and not checked['passed']:
                    row['status'] = 'failed'
                result = json.loads((directory / 'result.json').read_text())
                row.update(mods=result.get('mods', []), settings=result.get('effectiveSettings', {}),
                           classification=result.get('templateClassification', {}),
                           resourceProviders=result.get('resourceProviders', []),
                           issuance=result.get('diagnostics', {}).get('issuance', {}),
                           settledGuards=result.get('settled', {}).get('guards'),
                           workBudgets=result.get('workBudgets', {}))
            break
        rows.append(row)
    return {'schema': 1, 'candidateSha256': candidate,
            'scope': 'Configured production assertions only; not full behavior or release qualification.',
            'allConfiguredCasesPassed': bool(rows) and all(row['status'] == 'passed' for row in rows),
            'cases': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='Runner output directory')
    parser.add_argument('--candidate-sha256', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    result = report(args.output, args.candidate_sha256)
    atomic_json(args.report, result)
    print(json.dumps({row['case']: row['status'] for row in result['cases']}, indent=2))
    return 0 if result['allConfiguredCasesPassed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
