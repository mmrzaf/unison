#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

python3 - <<'PY'
from pathlib import Path
import hashlib
import json
import sqlite3

source = Path('app/src/main/java/com/darius/unison/storage/Database.kt').read_text()
assert 'version = 1' in source

schema_path = Path('app/schemas/com.darius.unison.storage.UnisonDatabase/1.json')
schema = json.loads(schema_path.read_text())['database']
assert schema['version'] == 1
assert {entity['tableName'] for entity in schema['entities']} == {'tracks', 'track_sources', 'playlists', 'playlist_entries'}

# Match Room's canonical SchemaIdentityKey construction. Keeping this check here prevents a
# hand-edited export from carrying a stale identity hash into a release.
separator = '?:?'

def digest(value: str) -> str:
    return hashlib.md5(value.encode()).hexdigest()

def append_all(values):
    return ''.join(f'{value}{separator}' for value in values)

def kotlin_list(values):
    return '[' + ', '.join(values) + ']'

def field_identity(field):
    value = f"{field['columnName']}-{field.get('affinity') or 'TEXT'}-{str(field.get('notNull', False)).lower()}"
    if field.get('defaultValue') is not None:
        value += f"-defaultValue={field['defaultValue']}"
    return value

def primary_key_identity(primary_key):
    return f"{str(primary_key.get('autoGenerate', False)).lower()}-{kotlin_list(primary_key.get('columnNames', []))}"

def index_identity(index):
    return f"{str(index.get('unique', False)).lower()}-{index['name']}-{','.join(index['columnNames'])}"

def foreign_key_identity(foreign_key):
    return (
        f"{foreign_key['table']}-{','.join(foreign_key['referencedColumns'])}-"
        f"{','.join(foreign_key['columns'])}-{foreign_key['onDelete']}-"
        f"{foreign_key['onUpdate']}-{str(foreign_key.get('deferred', False)).lower()}"
    )

def entity_identity(entity):
    values = [entity['tableName'], primary_key_identity(entity['primaryKey'])]
    values.extend(sorted((field_identity(field) for field in entity.get('fields', [])), key=str.lower))
    values.extend(sorted((index_identity(index) for index in entity.get('indices', [])), key=str.lower))
    values.extend(sorted((foreign_key_identity(key) for key in entity.get('foreignKeys', [])), key=str.lower))
    return digest(append_all(values))

entity_hashes = sorted((entity_identity(entity) for entity in schema['entities']), key=str.lower)
assert schema['identityHash'] == digest(append_all(entity_hashes)), schema['identityHash']
tracks = next(entity for entity in schema['entities'] if entity['tableName'] == 'tracks')
assert any(field['columnName'] == 'searchText' for field in tracks['fields'])
statements = [
    index['createSql'].replace('${TABLE_NAME}', 'tracks')
    for index in tracks['indices']
]
assert len(statements) == 7, statements

connection = sqlite3.connect(':memory:')
connection.execute(tracks['createSql'].replace('${TABLE_NAME}', 'tracks'))
connection.execute(
    'INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
    (
        'a' * 64,
        1234,
        'audio/mpeg',
        180000,
        'The Loneliest',
        'Måneskin',
        'Rush!',
        'track.mp3',
        'the loneliest måneskin rush! track.mp3',
        10,
        None,
    ),
)
for statement in statements:
    connection.execute(statement)

row = connection.execute(
    'SELECT title, artist, sizeBytes, searchText FROM tracks WHERE trackId = ?',
    ('a' * 64,),
).fetchone()
assert row == ('The Loneliest', 'Måneskin', 1234, 'the loneliest måneskin rush! track.mp3'), row
indexes = {row[1] for row in connection.execute("PRAGMA index_list('tracks')")}
expected = {
    'index_tracks_createdAt',
    'index_tracks_lastPlayedAt',
    'index_tracks_title',
    'index_tracks_artist',
    'index_tracks_album',
    'index_tracks_originalFileName',
    'index_tracks_searchText',
}
assert expected <= indexes, (expected, indexes)

# Exercise every paging/sort query shape against the release schema.
query = 'loneliest'
where = "WHERE ? = '' OR searchText LIKE '%' || ? || '%'"
params = (query, query)
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
