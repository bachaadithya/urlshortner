# Deploy on DigitalOcean Droplet

Your droplet: **168.144.158.115**

On one droplet you run everything with Docker Compose:

```
Internet → :80 → gateway → service → MongoDB + Redis
                 (Docker internal network only)
```

MongoDB and Redis are **not** exposed publicly. The Spring apps connect using Docker service names:

| App | Env var | Value inside Compose |
|-----|---------|----------------------|
| service | `MONGODB_URI` | `mongodb://mongodb:27017/urlshortener` |
| service + gateway | `REDIS_HOST` | `redis` |
| gateway | `SERVICE_URI` | `http://service:8081` |
| service | `PUBLIC_BASE_URL` | `http://168.144.158.115` |

---

## 1. SSH into the droplet

From your local machine:

```bash
ssh root@168.144.158.115
# or: ssh ubuntu@168.144.158.115
```

## 2. Install Docker (once)

```bash
apt-get update
apt-get install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker --version
docker compose version
```

## 3. Open firewall ports

In DigitalOcean → Droplet → **Networking** → **Firewalls** (or UFW on the box):

Allow inbound:

- **22** (SSH)
- **80** (HTTP – gateway)

Do **not** open 27017 (Mongo) or 6379 (Redis) to the world.

```bash
# Optional UFW example
ufw allow OpenSSH
ufw allow 80/tcp
ufw --force enable
```

## 4. Copy the project onto the droplet

**Option A – git** (if the repo is on GitHub):

```bash
cd /opt
git clone <YOUR_REPO_URL> urlshortner
cd urlshortner
```

**Option B – scp from your laptop** (from the machine that has the code):

```bash
scp -r /workspaces/urlshortner root@168.144.158.115:/opt/urlshortner
ssh root@168.144.158.115
cd /opt/urlshortner
```

## 5. Configure environment

```bash
cd /opt/urlshortner
cp .env.example .env
nano .env
```

Set at least:

```env
PUBLIC_BASE_URL=http://168.144.158.115
API_KEY=pick-a-long-random-secret
```

## 6. Build and start everything

```bash
chmod +x scripts/deploy-droplet.sh
./scripts/deploy-droplet.sh
# or:
docker compose up -d --build
docker compose ps
docker compose logs -f gateway service
```

Compose starts four containers:

1. `mongodb` – database  
2. `redis` – cache + bloom filter + rate limit  
3. `service` – Spring Boot URL shortener (`8081` internal)  
4. `gateway` – Spring Cloud Gateway mapped to host **port 80**

## 7. Verify

```bash
# Health through the public IP
curl http://168.144.158.115/actuator/health

# Create a short URL
curl -s -X POST http://168.144.158.115/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "X-API-Key: pick-a-long-random-secret" \
  -d '{"url":"https://example.com/hello"}'

# Custom alias
curl -s -X POST http://168.144.158.115/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "X-API-Key: pick-a-long-random-secret" \
  -d '{"url":"https://example.com/sale","customAlias":"blackfriday"}'

# Redirect (should 302)
curl -I http://168.144.158.115/blackfriday

# Metadata
curl -s http://168.144.158.115/api/v1/urls/blackfriday \
  -H "X-API-Key: pick-a-long-random-secret"
```

---

## How Spring connects (already wired)

You do **not** point the apps at `168.144.158.115` for Mongo/Redis. Inside Docker they use service DNS names from `docker-compose.yml`:

```yaml
# service container
MONGODB_URI: mongodb://mongodb:27017/urlshortener
REDIS_HOST: redis

# gateway container
REDIS_HOST: redis
SERVICE_URI: http://service:8081
```

`PUBLIC_BASE_URL` is the only setting that uses your public IP — it is used when building `shortUrl` in API responses.

---

## Useful commands

```bash
docker compose ps
docker compose logs -f service
docker compose logs -f gateway
docker compose restart service gateway
docker compose down          # stop
docker compose up -d --build # rebuild after code changes
```

---

## Next hardening (optional)

- Point a domain at `168.144.158.115` and set `PUBLIC_BASE_URL=https://your.domain`
- Put **Caddy** or **Nginx** in front for HTTPS (Let’s Encrypt)
- Change `API_KEY` regularly; keep `.env` off git (already in `.gitignore`)
