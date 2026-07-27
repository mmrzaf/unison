#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

python3 - <<'PY'
from pathlib import Path
import json
import sqlite3

source = Path('app/src/main/java/com/darius/unison/storage/Database.kt').read_text()
assert 'version = 1' in source
assert 'Migration(' not in source
assert '.addMigrations(' not in source

schema_path = Path('app/schemas/com.darius.unison.storage.UnisonDatabase/1.json')
schema = json.loads(schema_path.read_text())['database']
assert schema['version'] == 1
tracks = next(entity for entity in schema['entities'] if entity['tableName'] == 'tracks')
statements = [
    index['createSql'].replace('${TABLE_NAME}', 'tracks')
    for index in tracks['indices']
]
assert len(statements) == 6, statements

connection = sqlite3.connect(':memory:')
connection.execute('''
CREATE TABLE tracks (
    trackId TEXT NOT NULL PRIMARY KEY,
    sizeBytes INTEGER NOT NULL,
    mimeType TEXT,
    durationMs INTEGER NOT NULL,
    title TEXT,
    artist TEXT,
    album TEXT,
    originalFileName TEXT,
    createdAt INTEGER NOT NULL,
    lastPlayedAt INTEGER
)
''')
connection.execute(
    'INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
    ('a' * 64, 1234, 'audio/mpeg', 180000, 'The Loneliest', 'Måneskin', 'Rush!', 'track.mp3', 10, None),
)
for statement in statements:
    connection.execute(statement)

row = connection.execute('SELECT title, artist, sizeBytes FROM tracks WHERE trackId = ?', ('a' * 64,)).fetchone()
assert row == ('The Loneliest', 'Måneskin', 1234), row
indexes = {row[1] for row in connection.execute("PRAGMA index_list('tracks')")}
expected = {
    'index_tracks_createdAt',
    'index_tracks_lastPlayedAt',
    'index_tracks_title',
    'index_tracks_artist',
    'index_tracks_album',
    'index_tracks_originalFileName',
}
assert expected <= indexes, (expected, indexes)

# Exercise the shape of every paging/sort query against the migrated schema.
query = 'loneliest'
where = '''
WHERE ? = ''
   OR LOWER(COALESCE(title, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(artist, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(album, '')) LIKE '%' || LOWER(?) || '%'
   OR LOWER(COALESCE(originalFileName, '')) LIKE '%' || LOWER(?) || '%'
'''
params = (query,) * 5
orders = [
    'COALESCE(lastPlayedAt, createdAt) DESC, trackId ASC',
    "LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC",
    "LOWER(COALESCE(artist, '')) ASC, LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC",
    "LOWER(COALESCE(album, '')) ASC, LOWER(COALESCE(title, originalFileName, '')) ASC, trackId ASC",
]
for order in orders:
    rows = connection.execute(f'SELECT trackId FROM tracks {where} ORDER BY {order} LIMIT 60 OFFSET 0', params).fetchall()
    assert rows == [('a' * 64,)], (order, rows)
print('DATA_SCHEMA_OK')
PY
