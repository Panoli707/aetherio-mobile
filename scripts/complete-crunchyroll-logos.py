"""Complete pending anime logos with provider-published transparent key art.

The audit (scripts/audit-anime-logos.py) only inspects TMDB images; titles whose
covers ship a printed logo but no valid transparent TMDB logo stay pending. This
script completes them from the same provider-published source as the four
first-generation overrides (third_party/anime-logos/README.md): the Crunchyroll
series key art `content_logo_polychromatic`.

Identity never comes from image searching: aniListId, tmdbId and evidence travel
unchanged from docs/artwork/anime-logo-audit.json; the provider id must still be
corroborated by an exact normalized title match against the Crunchyroll series
sitemap (slug → series id), or by an explicit MANUAL_SLUGS entry.

Legacy matching report only; automatic asset registration is disabled by the one-logo-per-series policy.
Dependency: Pillow. Existing bundled logos are never overwritten.

Artwork rules are identical to the audit script: alpha with true transparency,
crop to opaque bounds, thumbnail to (1200, 600), minimum 200x30, then a 4 px margin.
"""
import difflib
import hashlib
import io
import json
import re
import sys
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'app/src/main/resources'
ASSETS = ROOT / 'app/src/main/assets/anime-logos'
REPORT = ROOT / 'docs/artwork/anime-logo-audit.json'
REGISTRY = RESOURCES / 'anime-logo-catalog.json'
MAPPINGS = RESOURCES / 'series-mappings.json'

SITEMAPS = 'https://www.crunchyroll.com/sitemaps/series/{letter}.xml?page=0'
KEYART = 'https://imgsrv.crunchyroll.com/cdn-cgi/image/fit=contain,format=png,width=1200/display/keyart/{id}-content_logo_polychromatic'
USER_AGENT = 'Aetherio-logos-complete/1.0'
ANILIST = 'https://graphql.anilist.co'

# Explicit provider identity for titles whose sitemap slug alone does not quote
# the audit title. Value: crunchyroll.com/series/<id>/<slug> verbatim.
MANUAL_SLUGS = {
    # '9969': 'https://www.crunchyroll.com/series/GY251MJ7Y/gintama-season-2',
}


def request(url, *, body=None):
    for attempt in range(4):
        headers = {'User-Agent': USER_AGENT}
        if body is not None:
            headers['Content-Type'] = 'application/json'
        try:
            with urlopen(Request(url, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers), timeout=60) as response:
                return response.read()
        except HTTPError as error:
            if error.code in (429, 500, 502, 503, 504) and attempt < 3:
                time.sleep(min(90, max(2, int(error.headers.get('Retry-After', '10')))))
                continue
            raise
    raise RuntimeError('Request retries exhausted')


ROMAN = {'i': '1', 'ii': '2', 'iii': '3', 'iv': '4', 'v': '5', 'vi': '6',
         'vii': '7', 'viii': '8', 'ix': '9', 'x': '10'}


def tokens_of(text):
    text = unicodedata.normalize('NFKD', text or '')
    text = ''.join(c for c in text if not unicodedata.combining(c)).casefold()
    return [token for token in re.split(r'[^0-9a-z]+', text) if token]


def join_tokens(tokens):
    return ' '.join(ROMAN.get(token, token) for token in tokens)


def keys_for(text):
    tokens = tokens_of(text)
    raw = ' '.join(tokens)
    folded = join_tokens(tokens)
    ampersanded = tokens_of(text.replace('&', ' and '))
    return {raw, folded, join_tokens(ampersanded)}


def load_sitemap():
    catalog = []
    for letter in ['#'] + [chr(65 + i) for i in range(26)]:
        path = '%23' if letter == '#' else letter
        xml = request(SITEMAPS.format(letter=path)).decode('utf-8', 'replace')
        for loc in re.findall(r'<loc>[^<]+</loc>', xml):
            match = re.match(r'<loc>https://www\.crunchyroll\.com/series/(GT[0-9]+)/([^<]+)</loc>', loc)
            if match:
                catalog.append((match.group(1), match.group(2)))
        time.sleep(1)
    return catalog


def anilist_titles(ids):
    titles = {}
    for index in range(0, len(ids), 50):
        chunk = ids[index:index + 50]
        data = json.loads(request(ANILIST, body={
            'query': 'query($ids:[Int]){Page(perPage:50){media(type:ANIME,id_in:$ids){id title{romaji english native}}}}',
            'variables': {'ids': [int(x) for x in chunk]}}))
        for media in data['data']['Page']['media']:
            titles[str(media['id'])] = media['title']
    return titles


def app_season(entry, mapping):
    """Same derivation the audit used before storing a registry row."""
    rows = mapping.get(entry['aniListId'], [])
    if len(rows) == 1 and entry['type'] == 'SERIES':
        return rows[0][4]
    return None if entry['type'] == 'MOVIE' else 1


def prepare():
    report = json.loads(REPORT.read_text(encoding='utf-8'))
    mapping = {}
    for row in json.loads(MAPPINGS.read_text(encoding='utf-8'))['rows']:
        mapping.setdefault(str(row[0]), []).append(row)
    registry = json.loads(REGISTRY.read_text(encoding='utf-8'))
    known = {str(row['aniListId']) for row in registry['logos']}
    pending = [entry for entry in report['entries']
               if entry['status'] == 'no-valid-transparent-logo' and str(entry['aniListId']) not in known]
    titles = anilist_titles([entry['aniListId'] for entry in pending])
    return report, mapping, registry, pending, titles


def flatten_sitemap(catalog):
    indexed = {}
    for gid, slug in catalog:
        for key in keys_for(slug.replace('-', ' ')):
            indexed.setdefault(key, set()).add((gid, slug))
    return indexed


def match(pending, titles, indexed):
    resolved, unresolved = {}, []
    for entry in pending:
        aid = str(entry['aniListId'])
        variants = [entry['title']] + [value for value in titles.get(aid, {}).values() if value]
        hits = set()
        for variant in variants:
            for key in keys_for(variant):
                hits |= indexed.get(key, set())
        if len(hits) == 1:
            resolved[aid] = next(iter(hits))
        else:
            unresolved.append((entry, variants, hits))
    return resolved, unresolved


def keyart_urls(crunchyroll_id):
    cid = crunchyroll_id[1:]  # GT00385792 -> T00385792
    for locale in ('-en-US', ''):
        yield KEYART.format(id=cid) + locale


def groom(png_bytes):
    image = Image.open(io.BytesIO(png_bytes)).convert('RGBA')
    alpha = image.getchannel('A')
    bounds = alpha.getbbox()
    if not bounds or alpha.getextrema()[0] != 0:
        return None
    image = image.crop(bounds)
    image.thumbnail((1200, 600), Image.Resampling.LANCZOS)
    if image.width < 200 or image.height < 30:
        return None
    padded = Image.new('RGBA', (image.width + 8, image.height + 8))
    padded.paste(image, (4, 4))
    return padded


def bundle(resolved, pending, mapping, registry):
    by_id = {str(entry['aniListId']): entry for entry in pending}
    added, skipped = [], []
    for aid in sorted(resolved, key=int):
        entry = by_id[aid]
        crunchyroll_id, slug = resolved[aid]
        data = None
        url = None
        for candidate in keyart_urls(crunchyroll_id):
            try:
                data = request(candidate)
                url = candidate
                break
            except (HTTPError, URLError):
                continue
        if data is None:
            skipped.append((entry, 'key art not published'))
            continue
        padded = groom(data)
        if padded is None:
            skipped.append((entry, 'image without valid transparency'))
            continue
        asset = f'anilist_{aid}.png'
        destination = ASSETS / asset
        if destination.exists():
            print(f'Refusing to overwrite {asset}', file=sys.stderr)
            skipped.append((entry, 'asset already present'))
            continue
        padded.save(destination, optimize=True)
        row = {'aniListId': aid, 'tmdbId': str(entry['tmdbId']), 'type': entry['type'],
               'season': app_season(entry, mapping), 'asset': asset, 'source': url,
               'identityEvidence': entry['identityEvidence'],
               'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}
        registry['logos'].append(row)
        added.append(row)
    return added, skipped


def main():
    if '--apply' in sys.argv:
        raise SystemExit('Disabled: per-season Crunchyroll assets conflict with the generic one-logo-per-series policy.')
    report, mapping, registry, pending, titles = prepare()
    catalog = load_sitemap()
    indexed = flatten_sitemap(catalog)
    resolved, unresolved = match(pending, titles, indexed)
    aid_width = max(len(str(entry['aniListId'])) for entry in pending) if pending else 1
    print(f'Pending {len(pending)}; sitemap entries {len(catalog)}; exact matches {len(resolved)}')
    for aid, (gid, slug) in sorted(resolved.items(), key=lambda x: int(x[0])):
        entry = next(entry for entry in pending if str(entry['aniListId']) == aid)
        season = app_season(entry, mapping)
        print(f'  {aid:>{aid_width}}  {entry["type"]:<6} season {season!s:<4} {gid}  {slug}')
    if unresolved:
        print('Unresolved (change one slug resolution per title, or confirm not offered):')
        for entry, variants, hits in unresolved:
            title = entry['title']
            extra = '' if not hits else f'  AMBIGUOUS: {sorted(hits)}'
            print(f'  {str(entry["aniListId"]):>{aid_width}}  {title}{extra}')
            close = difflib.get_close_matches(
                ' '.join(tokens_of(variants[0])), [slug.replace('-', ' ') for gid, slug in catalog], n=4, cutoff=0.5)
            for one in close:
                print(f'      ~ {one}')
    if not apply_mode:
        print('Matching report only; automatic per-season registration is disabled.')
        return
    added, skipped = bundle(resolved, pending, mapping, registry)
    registry['logos'] = sorted(registry['logos'], key=lambda row: int(row['aniListId']))
    registry['schemaVersion'] = 1
    REGISTRY.write_text(json.dumps(registry, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'added': len(added), 'skipped': [sk[1] for sk in skipped]}))
    for row in added:
        print(f'  + {row["aniListId"]:<7} {row["asset"]}  {row["source"]}')
    for entry, reason in skipped:
        print(f'  - {str(entry["aniListId"]):>{aid_width}}  {entry["title"]}  [{reason}]')


if __name__ == '__main__':
    main()
