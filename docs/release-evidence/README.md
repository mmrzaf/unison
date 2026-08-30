# Release evidence

Keep one concise evidence record for every public beta, RC, and stable release.

The purpose of these files is to retain **human release evidence** that is not already captured better by
GitHub Actions or generated release artifacts. They are intentionally not a second copy of the CI log.

For each version:

1. copy `TEMPLATE.md` to `<version>.md` before tagging;
2. fill the version/tag and any pre-tag human qualification already completed;
3. push the immutable release tag and let the full release workflow run;
4. after publication, install/smoke-test the exact published APK;
5. add the GitHub Release/Actions reference, physical-device result, accepted known issues, and final
   reviewer/date.

Machine-generated evidence remains authoritative in its original location:

- automated tests and gates → GitHub Actions run;
- artifact hashes → `SHA256SUMS.txt`;
- commit/signing details → `release-info.txt`;
- build provenance → GitHub attestation.

Do not manually duplicate those values unless there is a concrete investigation or archival reason.
Never claim an exact-artifact test using a debug, local, or different release build.
