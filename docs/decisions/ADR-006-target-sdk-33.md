# ADR-006: targetSdk 33 for the 1.2 line

**Status:** Accepted for 1.2

Unison compiles against SDK 36 but intentionally keeps `targetSdk 33` through the 1.2 stabilization
line. API 30/33/36 behavior is qualified explicitly. A targetSdk upgrade changes Android runtime policy
and must be evaluated as a separate engineering/release cycle rather than mixed into lifecycle fixes.
