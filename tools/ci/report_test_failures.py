#!/usr/bin/env python3
"""Expose failed JUnit cases in Actions annotations without requiring private log access."""
# created by chatgpt-codex
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def escape(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


reported = 0
for directory in ['app/build/test-results', 'app/build/outputs/androidTest-results']:
    for report in Path(directory).rglob('TEST-*.xml'):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            print('::warning::Unreadable JUnit report: ' + str(report))
            continue
        for case in root.iter('testcase'):
            failures = case.findall('failure') + case.findall('error')
            for failure in failures:
                title = case.get('classname', '') + '.' + case.get('name', '')
                message = failure.get('message', '') + '\n' + (failure.text or '')
                print('::error title=' + escape(title).replace(',', '%2C').replace(':', '%3A')
                      + '::' + escape(message[:4000]))
                reported += 1
if not reported:
    log = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    detail = '\n'.join(log.read_text(errors='replace').splitlines()[-45:]) if log and log.exists() else 'No JUnit failure reports were produced; inspect startup/build evidence.'
    print('::error title=Android check failed before a test failure report::' + escape(detail[:6000]))
