#!/usr/bin/env bash
# Run ON the droplet from the project directory after cloning.
set -euo pipefail

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example — edit API_KEY before going live."
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed. See docs/DROPLET_SETUP.md"
  exit 1
fi

docker compose pull || true
docker compose up -d --build
docker compose ps
echo
echo "Gateway:  http://$(hostname -I | awk '{print $1}')/"
echo "Health:   http://$(hostname -I | awk '{print $1}')/actuator/health"
echo "Create:   curl -X POST http://$(hostname -I | awk '{print $1}')/api/v1/urls -H 'Content-Type: application/json' -H \"X-API-Key: \$(grep API_KEY .env | cut -d= -f2)\" -d '{\"url\":\"https://example.com\"}'"
