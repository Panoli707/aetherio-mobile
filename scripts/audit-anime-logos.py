"""Audit current anime home rails, the full releasing season and recent airing history.

Uses public AniList/Jikan metadata and a local TMDB read token (never written).
Exact existing ID mappings win; new matches require an exact original title and year.
Run from the repository root: python scripts/audit-anime-logos.py
Dependency: Pillow. Existing bundled logos are never overwritten.
"""
import hashlib
import io
import json
import os
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import quote

from PIL import Image, UnidentifiedImageError

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'app/src/main/resources'
ASSETS = ROOT / 'app/src/main/assets/anime-logos'
REPORT = ROOT / 'docs/artwork/anime-logo-audit.json'
REGISTRY = RESOURCES / 'anime-logo-catalog.json'
EXISTING = {'213068', '200294', '179876', '205909'}


def request(url, *, token=None, body=None):
    for attempt in range(4):
        headers = {'User-Agent': 'Aetherio-artwork-audit/1.0'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if body:
            headers['Content-Type'] = 'application/json'
        try:
            with urlopen(Request(url, data=json.dumps(body).encode() if body else None, headers=headers), timeout=45) as response:
                return response.read()
        except HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504) or attempt == 3:
                raise
            time.sleep(min(90, max(2, int(error.headers.get('Retry-After', '10')))))
    raise RuntimeError('Request retries exhausted')


def main():
    token = os.environ.get('TMDB_READ_ACCESS_TOKEN', '')
    if not token:
        for line in (ROOT / 'local.properties').read_text(encoding='utf-8').splitlines():
            if line.startswith('tmdb.readAccessToken='):
                token = line.split('=', 1)[1].strip()
    if not token:
        raise RuntimeError('Configure a local TMDB read token')
    now = datetime.now(timezone.utc)
    jst = now.astimezone(timezone(timedelta(hours=9)))
    season = ['WINTER', 'SPRING', 'SUMMER', 'FALL'][(jst.month - 1) // 3]
    fields = 'id idMal title{romaji english native} format startDate{year} isAdult'
    entries, scopes, unavailable_scopes = {}, [], []

    def graphql(query, variables=None):
        data = json.loads(request('https://graphql.anilist.co', body={'query': query, 'variables': variables or {}}))
        if data.get('errors'):
            raise RuntimeError('AniList rejected audit query')
        return data['data']['Page']

    def collect(media, scope):
        for item in media:
            if not item or item.get('isAdult'):
                continue
            key = str(item['id'])
            entries.setdefault(key, {'media': item, 'scopes': []})
            if scope not in entries[key]['scopes']:
                entries[key]['scopes'].append(scope)

    # Same three native home feeds; all pages for the calendar and recent row.
    for sort in ['POPULARITY_DESC', 'TRENDING_DESC', 'SCORE_DESC']:
        scope = 'home:' + sort
        collect(graphql('query{Page(page:1,perPage:25){media(type:ANIME,isAdult:false,sort:' + sort + '){' + fields + '}}}')['media'], scope)
        scopes.append(scope)
    scope = f'season:{season}:{jst.year}'
    for page in range(1, 101):
        data = graphql('query{Page(page:' + str(page) + ',perPage:50){pageInfo{hasNextPage}media(type:ANIME,isAdult:false,status:RELEASING,season:' + season + ',seasonYear:' + str(jst.year) + '){' + fields + '}}}')
        collect(data['media'], scope)
        if not data['pageInfo']['hasNextPage']:
            break
    else:
        raise RuntimeError('Incomplete seasonal pagination')
    scopes.append(scope)
    start = int(jst.replace(hour=0, minute=0, second=0, microsecond=0).timestamp()) - 29 * 86400
    scope = 'recent:30-JST-days'
    for page in range(1, 101):
        data = graphql('query{Page(page:' + str(page) + ',perPage:50){pageInfo{hasNextPage}airingSchedules(airingAt_greater:' + str(start - 1) + ',airingAt_lesser:' + str(int(now.timestamp()) + 1) + ',sort:TIME_DESC){media{' + fields + '}}}}')
        collect([row['media'] for row in data['airingSchedules']], scope)
        if not data['pageInfo']['hasNextPage']:
            break
    else:
        raise RuntimeError('Incomplete recent pagination')
    scopes.append(scope)
    # Jikan-backed home rails (the application loads the first page of each rail).
    mal_ids = set()
    jikan_paths = ['recommendations/anime', 'top/anime?filter=favorite', 'top/anime?filter=bypopularity',
                   'seasons/upcoming?sfw=true', 'top/anime?type=movie&filter=bypopularity']
    jikan_paths += [f'anime?start_date={year}-01-01&end_date={year + 9}-12-31&order_by=score&sort=desc&min_score=7&sfw=true&limit=25' for year in [1990, 2000, 2010]]
    for path in jikan_paths:
        try:
            rows = json.loads(request('https://api.jikan.moe/v4/' + path))['data']
        except (URLError, TimeoutError):
            unavailable_scopes.extend('home:jikan:' + pending for pending in jikan_paths[jikan_paths.index(path):])
            print('Jikan unavailable; recording unaudited rails and auditing AniList equivalents.', flush=True)
            break
        for row in rows[:25]:
            for item in row.get('entry', [row]) if isinstance(row.get('entry', [row]), list) else [row['entry']]:
                if item.get('mal_id'):
                    mal_ids.add(item['mal_id'])
        scopes.append('home:jikan:' + path)
        time.sleep(.4)
    for name, filters in [('favorites', 'sort:FAVOURITES_DESC'), ('upcoming', 'status:NOT_YET_RELEASED,sort:POPULARITY_DESC'),
                          ('movies', 'format:MOVIE,sort:POPULARITY_DESC')] + [
                              (f'decade:{year}', f'startDate_greater:{year}0100,startDate_lesser:{year + 10}0101,sort:SCORE_DESC') for year in [1990, 2000, 2010]]:
        scope = 'home-equivalent:anilist:' + name
        collect(graphql('query{Page(perPage:25){media(type:ANIME,isAdult:false,' + filters + '){' + fields + '}}}')['media'], scope)
        scopes.append(scope)
    ids = sorted(mal_ids)
    for i in range(0, len(ids), 50):
        data = graphql('query($ids:[Int]){Page(perPage:50){media(type:ANIME,isAdult:false,idMal_in:$ids){' + fields + '}}}', {'ids': ids[i:i + 50]})
        collect(data['media'], 'home:jikan')
    print(f'Audit inventory: {len(entries)} distinct anime', flush=True)
    mapping = {}
    for row in json.loads((RESOURCES / 'series-mappings.json').read_text(encoding='utf-8'))['rows']:
        mapping.setdefault(str(row[0]), []).append(row)
    old_registry = json.loads(REGISTRY.read_text(encoding='utf-8'))['logos'] if REGISTRY.exists() else []
    results = {str(row['aniListId']): row for row in old_registry}

    def normalize(text):
        return ''.join(c for c in unicodedata.normalize('NFKC', text or '').casefold() if c.isalnum())

    def resolve(pair):
        aid, entry = pair
        media = entry['media']
        report = {'aniListId': aid, 'title': media['title']['english'] or media['title']['romaji'],
                  'type': 'MOVIE' if media['format'] == 'MOVIE' else 'SERIES', 'scopes': entry['scopes']}
        if aid in EXISTING or aid in results:
            return report | {'status': 'already-bundled'}, results.get(aid)
        try:
            rows = mapping.get(aid, [])
            if len(rows) == 1 and report['type'] == 'SERIES':
                _, tid, tmdb_season, _, app_season = rows[0]
                # An existing identity/season table is stronger evidence than search strings.
                path = f'tv/{tid}' if tmdb_season == 1 else f'tv/{tid}/season/{tmdb_season}'
                identity = 'existing-series-ID-and-season-mapping'
            else:
                path_type = 'movie' if report['type'] == 'MOVIE' else 'tv'
                year = media['startDate']['year']
                names = {normalize(n) for n in media['title'].values() if n}
                matches = {}
                for title in dict.fromkeys(n for n in [media['title']['native'], media['title']['english'], media['title']['romaji']] if n):
                    candidates = json.loads(request('https://api.themoviedb.org/3/search/' + path_type + '?query=' + quote(title), token=token))['results']
                    for candidate in candidates:
                        date = candidate.get('release_date' if path_type == 'movie' else 'first_air_date', '')
                        original = candidate.get('original_title' if path_type == 'movie' else 'original_name')
                        languages = candidate.get('original_language') in ('ja', 'zh', 'ko')
                        if year and date[:4] == str(year) and normalize(original) in names and languages and 16 in candidate.get('genre_ids', []):
                            matches[candidate['id']] = candidate
                    if len(matches) == 1:
                        break
                if len(matches) != 1:
                    return report | {'status': 'identity-not-verified', 'matchingCandidates': len(matches)}, None
                tid = next(iter(matches))
                path = f'{path_type}/{tid}'
                app_season = 1 if path_type == 'tv' else None
                identity = 'exact-original-title-year-animation-and-language'
            report |= {'tmdbId': str(tid), 'identityEvidence': identity, 'imageEndpoint': 'https://api.themoviedb.org/3/' + path + '/images'}
            images = json.loads(request(report['imageEndpoint'], token=token)).get('logos', [])
            images.sort(key=lambda x: ({'es': 0, 'en': 1, None: 2}.get(x.get('iso_639_1'), 3), -x.get('width', 0)))
            for candidate in images:
                if candidate.get('width', 0) < 300:
                    continue
                url = 'https://image.tmdb.org/t/p/original' + candidate['file_path']
                try:
                    image = Image.open(io.BytesIO(request(url))).convert('RGBA')
                except UnidentifiedImageError:
                    continue  # SVG/unsupported/corrupt candidates must not hide a later valid PNG.
                alpha = image.getchannel('A')
                bounds = alpha.getbbox()
                if not bounds or alpha.getextrema()[0] != 0:
                    continue
                image = image.crop(bounds)
                image.thumbnail((1200, 600), Image.Resampling.LANCZOS)
                if image.width < 200 or image.height < 30:
                    continue
                padded = Image.new('RGBA', (image.width + 8, image.height + 8))
                padded.paste(image, (4, 4))
                asset = f'anilist_{aid}.png'
                destination = ASSETS / asset
                if destination.exists():
                    raise RuntimeError('Refusing to overwrite existing asset')
                padded.save(destination, optimize=True)
                row = {'aniListId': aid, 'tmdbId': str(tid), 'type': report['type'], 'season': app_season,
                       'asset': asset, 'source': url, 'identityEvidence': identity,
                       'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}
                return report | {'status': 'added', 'asset': asset, 'source': url}, row
            return report | {'status': 'no-valid-transparent-logo'}, None
        except (URLError, ValueError, OSError, RuntimeError) as error:
            # Do not turn failed requests into a verified "no logo" conclusion.
            return report | {'status': 'lookup-failed', 'errorType': type(error).__name__}, None

    ASSETS.mkdir(parents=True, exist_ok=True)
    reports = []
    with ThreadPoolExecutor(max_workers=3) as pool:
        for index, (report, logo) in enumerate(pool.map(resolve, sorted(entries.items(), key=lambda x: int(x[0]))), 1):
            reports.append(report)
            if logo:
                results[report['aniListId']] = logo
            if index % 25 == 0:
                print(f'Checked {index}/{len(entries)}, catalog logos: {len(results)}', flush=True)
    REGISTRY.write_text(json.dumps({'schemaVersion': 1, 'logos': sorted(results.values(), key=lambda x: int(x['aniListId']))}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    summary = {status: sum(r['status'] == status for r in reports) for status in sorted({r['status'] for r in reports})}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps({'schemaVersion': 1, 'generatedAt': now.isoformat(), 'scope': scopes, 'unavailableScopes': unavailable_scopes,
                                 'scopeLimit': 'Current home first pages, full current releasing season and 30 Japanese days of recent broadcasts; not the entire historical anime catalog.',
                                 'total': len(reports), 'summary': summary, 'entries': reports}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary), flush=True)


if __name__ == '__main__':
    main()
