"""Download root-level TMDB candidates into ignored build outputs for human review.

No resources, identity mappings, credentials or user data are written by this tool.
Usage: python scripts/prepare-artwork-review.py tv:46260 tv:209867 tv:220542
"""
import concurrent.futures
import io
import json
import os
from pathlib import Path
import re
import sys
import urllib.request
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/build/artwork-review-0202"


def get(url, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
        return response.read()


def main():
    token = os.getenv("TMDB_READ_ACCESS_TOKEN", "")
    if not token:
        match = re.search(r"(?m)^tmdb.readAccessToken\s*=\s*(.+)$", (ROOT / "local.properties").read_text())
        token = match.group(1).strip() if match else ""
    if not token:
        raise SystemExit("TMDB credential unavailable")
    OUT.mkdir(parents=True, exist_ok=True)
    for identity in sys.argv[1:]:
        if not re.fullmatch(r"(tv|movie):[0-9]+", identity):
            raise SystemExit("Expected tv:ID or movie:ID")
        kind, number = identity.split(":")
        raw = json.loads(get(f"https://api.themoviedb.org/3/{kind}/{number}?append_to_response=images&include_image_language=es,en,null", token))
        (OUT / f"{kind}-{number}.json").write_text(json.dumps(raw, ensure_ascii=False, indent=2), encoding="utf-8")
        for field in ("posters", "backdrops", "logos"):
            rows = [row for row in raw.get("images", {}).get(field, []) if not row["file_path"].lower().endswith(".svg")]
            rows = sorted(rows, key=lambda r: (r.get("iso_639_1") is not None, -min(r.get("width", 0), 3840), -r.get("vote_average", 0)))[:12]
            if not rows:
                continue
            sheet = Image.new("RGB", (1200, 350 * ((len(rows) + 3) // 4)), "#333333")
            def load(row):
                return Image.open(io.BytesIO(get("https://image.tmdb.org/t/p/w500" + row["file_path"]))).convert("RGBA")
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                for i, (row, image) in enumerate(zip(rows, pool.map(load, rows))):
                    image.thumbnail((294, 296))
                    x, y = (i % 4) * 300, (i // 4) * 350
                    sheet.paste(image, (x + (300 - image.width) // 2, y), image)
                    ImageDraw.Draw(sheet).text((x + 4, y + 298), f"{i}: {row['width']}x{row['height']}\n{row['file_path']}", fill="white")
            sheet.save(OUT / f"{kind}-{number}-{field}.jpg")
            (OUT / f"{kind}-{number}-{field}.json").write_text(json.dumps(rows, indent=2))
        print(identity, raw.get("name", raw.get("title")), "review ready")


if __name__ == "__main__":
    main()
