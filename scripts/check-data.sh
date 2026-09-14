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
expected_identity = digest(append_all(entity_hashes))
assert schema['identityHash'] == expected_identity, schema['identityHash']
assert schema['setupQueries'][-1].endswith(f"'{expected_identity}')"), schema['setupQueries'][-1]

tracks = next(entity for entity in schema['entities'] if entity['tableName'] == 'tracks')
fields = {field['columnName'] for field in tracks['fields']}
assert {'searchText', 'sortTitle', 'sortArtist', 'sortAlbum', 'recentSortAt'} <= fields
index_names = {index['name'] for index in tracks['indices']}
expected_indexes = {
    'index_tracks_recentSortAt_trackId',
    'index_tracks_sortTitle_trackId',
    'index_tracks_sortArtist_sortTitle_trackId',
    'index_tracks_sortAlbum_sortTitle_trackId',
}
assert index_names == expected_indexes, index_names

connection = sqlite3.connect(':memory:')
connection.execute(tracks['createSql'].replace('${TABLE_NAME}', 'tracks'))
for index in tracks['indices']:
    connection.execute(index['createSql'].replace('${TABLE_NAME}', 'tracks'))

rows = [
    (
        'a' * 64, 1234, 'audio/mpeg', 180000, 'The Loneliest', 'Måneskin', 'Rush!',
        'track.mp3', 'the loneliest måneskin rush! track.mp3', 'the loneliest', 'måneskin',
        'rush!', 10, 10, None,
    ),
    (
        'b' * 64, 1234, 'audio/mpeg', 180000, None, None, None,
        'Fallback.mp3', 'fallback.mp3', 'fallback.mp3', '', '', 20, 20, None,
    ),
]
connection.executemany(
    'INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', rows
)

row = connection.execute(
    'SELECT title, artist, sizeBytes, searchText, sortTitle, sortArtist, sortAlbum, recentSortAt '
    'FROM tracks WHERE trackId = ?',
    ('a' * 64,),
).fetchone()
assert row == (
    'The Loneliest', 'Måneskin', 1234, 'the loneliest måneskin rush! track.mp3',
    'the loneliest', 'måneskin', 'rush!', 10,
), row

browse_queries = {
    'recent': (
        'SELECT * FROM tracks ORDER BY recentSortAt DESC, trackId DESC LIMIT 60 OFFSET 0',
        'index_tracks_recentSortAt_trackId',
    ),
    'title': (
        'SELECT * FROM tracks ORDER BY sortTitle ASC, trackId ASC LIMIT 60 OFFSET 0',
        'index_tracks_sortTitle_trackId',
    ),
    'artist': (
        'SELECT * FROM tracks ORDER BY sortArtist ASC, sortTitle ASC, trackId ASC LIMIT 60 OFFSET 0',
        'index_tracks_sortArtist_sortTitle_trackId',
    ),
    'album': (
        'SELECT * FROM tracks ORDER BY sortAlbum ASC, sortTitle ASC, trackId ASC LIMIT 60 OFFSET 0',
        'index_tracks_sortAlbum_sortTitle_trackId',
    ),
}
for name, (query, expected_index) in browse_queries.items():
    plan = ' | '.join(row[3] for row in connection.execute('EXPLAIN QUERY PLAN ' + query))
    assert expected_index in plan, (name, plan)
    assert 'TEMP B-TREE FOR ORDER BY' not in plan, (name, plan)
    assert connection.execute(query).fetchall(), name

search_rows = connection.execute(
    "SELECT trackId FROM tracks WHERE searchText LIKE '%' || ? || '%' ESCAPE '!' "
    'ORDER BY sortTitle ASC, trackId ASC LIMIT 60',
    ('loneliest',),
).fetchall()
assert search_rows == [('a' * 64,)], search_rows

# RECENT has one persisted ordering key; marking a track played must update both the public
# timestamp and the key used by the index-backed browse query.
connection.execute(
    'UPDATE tracks SET lastPlayedAt = ?, recentSortAt = ? WHERE trackId = ?',
    (99, 99, 'a' * 64),
)
recent = connection.execute(
    'SELECT trackId, lastPlayedAt, recentSortAt FROM tracks '
    'ORDER BY recentSortAt DESC, trackId DESC'
).fetchall()
assert recent[0] == ('a' * 64, 99, 99), recent

print('DATA_SCHEMA_OK')
PY
