#!/usr/bin/env python3
"""Checks our translations against the words they borrow from TaCZ.

The language files borrow a few words instead of choosing their own, so that a player reads the same word in TaCZ and
here: "gun" is TaCZ's word in that language, "off hand" is Minecraft's. terms.json lists each borrowed word: the TaCZ
or Minecraft language key it comes from, which of our strings use it, and what that key said in each language when
those strings were written ("seen").

Run it after putting new TaCZ jars in libs/. It reports
  - a TaCZ word that has changed since (and our strings to look at again),
  - a language TaCZ has added or dropped, against our lang folder.
Once our strings are updated, --accept records TaCZ's current words as seen. Minecraft's words are fixed with
Minecraft 1.20.1, so they are recorded for reference and not checked. English and Simplified Chinese are written by
hand and borrow nothing.

    python tools/i18n/terms.py [--accept]
Exit status 1 when there is something to look at.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TERMS = Path(__file__).with_name('terms.json')
LANG = ROOT / 'src/main/resources/assets/taczspray/lang'
OWN = {'en_us', 'zh_cn'}
# the game's language codes; TaCZ also ships a stray "tr-TR.json", which the game never loads
CODE = re.compile(r'^[a-z]{2,3}_[a-z]{2,3}$')


def tacz_languages(jar):
    """{language: {key: string}} from one TaCZ jar."""
    out = {}
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            m = re.fullmatch(r'assets/tacz/lang/([^/]+)\.json', name)
            if m and CODE.match(m.group(1)):
                out[m.group(1)] = json.loads(z.read(name).decode('utf-8-sig'))
    return out


def main(accept):
    terms = json.loads(TERMS.read_text(encoding='utf-8'))
    jars = sorted((ROOT / 'libs').glob('tacz*.jar'))
    if not jars:
        sys.exit('no TaCZ jars in libs/')
    tacz = {jar.name: tacz_languages(jar) for jar in jars}
    ours = {p.stem for p in LANG.glob('*.json')}
    notes = []

    theirs = set().union(*(set(langs) for langs in tacz.values()))
    for lang in sorted(theirs - ours):
        notes.append(f'TaCZ has {lang}, we have no {lang}.json')
    for lang in sorted(ours - theirs):
        notes.append(f'we have {lang}.json, TaCZ has no {lang}')

    for name, term in terms.items():
        if term['from'] != 'tacz':
            continue
        key, seen = term['key'], term['seen']
        for lang in sorted((ours & theirs) - OWN):
            now = {jar: langs.get(lang, {}).get(key) for jar, langs in tacz.items()}
            values = set(now.values())
            if len(values) > 1:
                notes.append(f'{name} in {lang}: the TaCZ jars disagree: {now}')
                continue
            value = values.pop()
            if lang not in seen:
                notes.append(f'{name} in {lang}: not recorded; TaCZ says {value!r}')
            elif seen[lang] != value:
                notes.append(f'{name} in {lang}: TaCZ now says {value!r}, was {seen[lang]!r}; '
                             f'look at {", ".join(term["used_in"])}')
            else:
                continue
            if accept:
                seen[lang] = value

    print(f'TaCZ jars: {", ".join(tacz)}')
    for n in notes:
        print(' ', n)
    if accept:
        TERMS.write_text(json.dumps(terms, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
        print('recorded TaCZ\'s current words')
        return 0
    print('nothing to look at' if not notes else f'{len(notes)} to look at')
    return 1 if notes else 0


if __name__ == '__main__':
    sys.exit(main('--accept' in sys.argv[1:]))
