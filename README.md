# URL Shortener (Spring Boot + DigitalOcean)

Production-oriented URL shortener with:

- **Spring Cloud Gateway** (edge routing, rate limiting, Bloom filter rejection)
- **Spring Boot service** (create / redirect / metadata)
- **MongoDB** unique `shortCode` for atomic custom-alias claims
- **Redis** cache-aside for hot redirects + shared Bloom filter bits
- **DOKS-ready** manifests and Docker images

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/urls` | `X-API-Key` | Create short URL (optional `customAlias`) |
| `GET` | `/{shortCode}` | none | `302` redirect (cache-aside) |
| `GET` | `/api/v1/urls/{shortCode}` | `X-API-Key` | Metadata |

### Create examples

```bash
# Auto Base62 code
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-me" \
  -d '{"url":"https://example.com/very/long/path"}'

# Custom alias (atomic — concurrent claims → one 201, one 409 ALIAS_TAKEN)
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-me" \
  -d '{"url":"https://shop.example.com/sale","customAlias":"blackfriday"}'
```

## Architecture highlights

- **Race-safe custom aliases**: `insert` only + Mongo unique index. No check-then-insert.
- **Cache-aside**: Redis `url:{code}` → on miss load Mongo → populate Redis.
- **Bloom filter at gateway**: never-created codes rejected with `404` before the service/DB (cache penetration protection). Service updates Redis key `url:bloom` and publishes `url:bloom:reload`.
- **Thread-safe clicks**: Mongo `$inc` via async `ClickCountService`.

## Local run

```bash
docker compose up --build
# API on http://localhost:80  (gateway maps host 80 → container 8080)
```

## DigitalOcean Droplet (your IP)

Full steps: [`docs/DROPLET_SETUP.md`](docs/DROPLET_SETUP.md)

CI/CD with GitHub Actions: [`docs/GITHUB_ACTIONS.md`](docs/GITHUB_ACTIONS.md)

```bash
ssh root@168.144.158.115
# install Docker → copy project → cp .env.example .env → docker compose up -d --build
```

Or with local Mongo/Redis (dev without Docker apps):

```bash
export PATH="/workspaces/urlshortner/.tools/maven/bin:$PATH"
mvn -q test
```

## DigitalOcean deploy

1. Create **DOKS**, **Managed MongoDB**, **Managed Caching (Redis)** in the same region/VPC.
2. Build & push images to **DO Container Registry**:

```bash
docker build -f service/Dockerfile -t registry.digitalocean.com/<registry>/url-shortener-service:1.0.0 .
docker build -f gateway/Dockerfile -t registry.digitalocean.com/<registry>/url-shortener-gateway:1.0.0 .
doctl registry login
docker push registry.digitalocean.com/<registry>/url-shortener-service:1.0.0
docker push registry.digitalocean.com/<registry>/url-shortener-gateway:1.0.0
```

3. Edit secrets/config in [`k8s/00-namespace-secrets.yaml`](k8s/00-namespace-secrets.yaml), image names in the Deployments, then:

```bash
kubectl apply -f k8s/
```

4. Point your domain at the DO Load Balancer created by the gateway `Service` (`type: LoadBalancer`).

## Module layout

```
gateway/   Spring Cloud Gateway + BloomFilterGatewayFilter
service/   Create / redirect / metadata + Base62 + Redis + Mongo
k8s/       DOKS Deployments, Services, HPA, DO LB annotations
```
