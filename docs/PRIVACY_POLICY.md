# Privacy policy

Unison operates locally and does not require an account.

## Data stored on the device

Unison may store imported audio, track metadata, playlists, a generated peer identity, display name, recent room diagnostic state, temporary received tracks, artwork cache, and local diagnostic logs. Managed audio is stored in the application's private directory. Android backups are disabled.

## Local communication

When a room is active, Unison advertises and discovers local services, exchanges peer names and local addresses, synchronizes room state, and transfers assigned audio directly between participating devices. This traffic stays on the private network selected by the user. Public network addresses are rejected.

## External services

Unison contains no cloud account, remote API, analytics, advertising, telemetry, billing, store delivery, or hosted Google runtime service integration. Android's `INTERNET` permission is required for private TCP sockets; it does not grant Unison a configured remote destination.

## User control

Users select imported files through Android's picker or share sheet, can keep or remove temporary tracks, can clear local temporary content, and can uninstall the application to remove app-private data. Files exported through Android's document UI are created only at the destination the user selects.
