# Unison uses generated serializers and generated Room adapters through static references. AndroidX
# dependencies ship their own consumer rules, so the application deliberately has no package-wide
# keep rules. If a future release needs a keep rule, add the narrowest rule for the exact reflected
# entry point rather than retaining an entire package.
