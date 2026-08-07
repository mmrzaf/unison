#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command docker
ACTION="${1:-build}"
TAG="$(default_tag "${2:-}")"
PREFIX="${WAIO_IMAGE_PREFIX:-waiotech}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmjs.org/}"
APPS=(server dashboard admin)
[ -f "$ROOT_DIR/website/Dockerfile" ] && APPS+=(website)

for app in "${APPS[@]}"; do
  image="$PREFIX-$app:$TAG"
  case "$ACTION" in
    build)
      info "building $image"
      case "$app" in
        website)
          docker build \
            --build-arg NPM_REGISTRY="$NPM_REGISTRY" \
            --build-arg PUBLIC_SITE_URL="${PUBLIC_SITE_URL:-http://127.0.0.1:3002}" \
            --build-arg PUBLIC_DASHBOARD_URL="${PUBLIC_DASHBOARD_URL:-http://127.0.0.1:3000}" \
            --build-arg PUBLIC_CONTACT_EMAIL="${PUBLIC_CONTACT_EMAIL:-}" \
            --build-arg PUBLIC_STATUS_URL="${PUBLIC_STATUS_URL:-}" \
            --build-arg PUBLIC_ANDROID_URL="${PUBLIC_ANDROID_URL:-}" \
            --build-arg PUBLIC_ALLOW_INDEXING="${PUBLIC_ALLOW_INDEXING:-false}" \
            -f "$ROOT_DIR/website/Dockerfile" \
            -t "$image" \
            "$ROOT_DIR"
          ;;
        *)
          docker build -t "$image" "$ROOT_DIR/$app"
          ;;
      esac
      ;;
    push)
      info "pushing $image"
      docker push "$image"
      ;;
    *) die "Usage: $0 {build|push} [tag]" ;;
  esac
done

printf '%s complete for tag: %s\n' "$ACTION" "$TAG"
