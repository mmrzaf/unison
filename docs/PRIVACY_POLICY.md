# Unison privacy policy

**Effective date:** July 27, 2026

Unison is an offline synchronized-listening application. It does not require an account and does not operate a cloud service.

## Data Unison handles

Unison may process:

- the display name you choose;
- audio files and playlists you explicitly add or share to the app;
- track metadata such as title, artist, album, duration, file type, and SHA-256 identifier;
- temporary room information such as connected peer names, local-network addresses, queue state, and synchronization measurements;
- local diagnostic logs used to investigate application failures.

## Where data is stored

Music imported into Unison is stored in the app's private storage on your device. Music received from a room is temporary for 24 hours by default unless you choose to keep it. You can remove individual temporary tracks or clear temporary music from the Library screen.

Uninstalling Unison removes its app-private database, music copies, settings, and diagnostics under normal Android behavior.

## Local sharing

When you join a room, Unison communicates directly with devices on the same local Wi-Fi network or local-only hotspot. Room participants can receive audio files that are added to the shared queue. Control messages are authenticated, and transferred tracks are verified by SHA-256.

Audio file transfers remain on the local network. Unison does not upload room music to an internet server.

## Internet, analytics, and advertising

Unison does not include advertising, behavioral analytics, third-party tracking SDKs, cloud accounts, or remote telemetry. Android's `INTERNET` permission is used for local TCP sockets; the app rejects public internet addresses for room connections.

## Permissions

Unison may request nearby Wi-Fi or location permission when Android requires it to discover devices or create a local-only hotspot. File access is granted only through Android's system file and folder pickers or the share sheet. Unison does not request broad access to all files on the device.

## Your choices

You can:

- choose which music to add or share;
- keep or remove temporary room music;
- clear temporary music from the Library;
- leave a room at any time;
- clear all Unison data from Android settings or uninstall the app.

## Contact

Contact the developer through the support address or issue tracker published in Unison's GitHub repository.
