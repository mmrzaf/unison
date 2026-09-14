# Local data contract

## 1.2 / Beta 7

`1.2.0-beta.7` is Unison's first supported local-data contract: **local data format v1**.
There is no shipped migration or compatibility layer for earlier development builds because Beta 7 is
the clean starting point.
Any device used with an earlier development build must uninstall Unison or clear its app data before the
first Beta 7 install; upgrade behavior from those unsupported builds is intentionally not defined.

The canonical Room database is `unison.db` at schema version 1. The canonical managed-track root is
`files/tracks`. Track rows persist normalized title, artist, and album sort keys plus the current RECENT
timestamp so paging can use composite SQLite indexes instead of rebuilding expression sorts on every
page.

Beta 7 and later 1.2 candidates must preserve this v1 data unless a future schema change is deliberately
versioned and migrated. Do not silently rename the database, reset current data, add destructive Room
fallbacks, or introduce a second local-data namespace.

## Wire protocol

Local-data format and networking are independent contracts. The 1.2 line uses one strict wire contract
named **Protocol 1**. There is no negotiation, fallback decoder, or legacy wire mode.
