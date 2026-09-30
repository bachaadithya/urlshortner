# GitHub Actions CI/CD — detailed setup

## What you get

| Workflow | Trigger | Action |
|----------|---------|--------|
| CI | Push / PR to `main` | Run Maven tests |
| CD | Push to `main` | SSH into droplet → `git pull` → rebuild Docker containers |

---

## Part A — Create an SSH key (on your laptop / WSL)

Run these on **your local machine** (not on the droplet):

```bash
cd ~
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ./do_deploy_key -N ""
```

This creates two files in your home directory:

| File | Purpose |
|------|---------|
| `~/do_deploy_key` | **Private** key → goes into GitHub secret `DROPLET_SSH_KEY` |
| `~/do_deploy_key.pub` | **Public** key → goes onto the droplet |

Show the public key (you will paste this on the droplet):

```bash
cat ~/do_deploy_key.pub
```

Example output:

```text
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... github-actions-deploy
```

---

## Part B — Allow that key on the droplet

### B1. SSH in with your normal login (password or existing key)

```bash
ssh root@168.144.158.115
```

### B2. Install the public key

Still **on the droplet**:

```bash
mkdir -p /root/.ssh
chmod 700 /root/.ssh
nano /root/.ssh/authorized_keys
```

- Paste the **entire** line from `do_deploy_key.pub` on a **new line** at the end of the file.
- Save and exit (`Ctrl+O`, Enter, `Ctrl+X` in nano).

Then:

```bash
chmod 600 /root/.ssh/authorized_keys
```

### B3. (Optional) Test the key from your laptop

Open a **new** local terminal (leave the droplet session alone):

```bash
ssh -i ~/do_deploy_key root@168.144.158.115
```

If you get a shell **without** a password prompt, the key works. Type `exit`.

---

## Part C — Make sure the app on the droplet is a git clone

CD does `git fetch` / `git reset --hard origin/main`. The project must live as a git repo.

**On the droplet:**

```bash
# If /opt/urlshortner already exists from scp and is NOT a git clone:
#   mv /opt/urlshortner /opt/urlshortner.bak

mkdir -p /opt
git clone https://github.com/bachaadithya/urlshortner.git /opt/urlshortner
cd /opt/urlshortner

cp .env.example .env
nano .env
```

Set at least:

```env
PUBLIC_BASE_URL=http://168.144.158.115
API_KEY=your-long-secret
```

Confirm Docker works:

```bash
docker --version
docker compose version
cd /opt/urlshortner
docker compose up -d --build   # first deploy once manually if needed
```

---

## Part D — Add GitHub Actions secrets

1. Open: https://github.com/bachaadithya/urlshortner  
2. **Settings** → **Secrets and variables** → **Actions**  
3. **New repository secret** for each:

### Secret 1 — `DROPLET_HOST`

```text
168.144.158.115
```

### Secret 2 — `DROPLET_USER`

```text
root
```

### Secret 3 — `DROPLET_SSH_KEY`

On your **laptop**:

```bash
cat ~/do_deploy_key
```

Copy **everything**, including:

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

Paste that into the GitHub secret value.  
**Do not** paste the `.pub` file.

### Secret 4 — `DROPLET_APP_PATH` (optional)

```text
/opt/urlshortner
```

If omitted, CD defaults to `/opt/urlshortner`.

---

## Part E — Push workflow files (if not already on GitHub)

On your machine / in the project:

```bash
git add .github docs/GITHUB_ACTIONS.md README.md scripts/deploy-droplet.sh
git commit -m "Add GitHub Actions CI and droplet CD"
git push origin main
```

Then open:  
https://github.com/bachaadithya/urlshortner/actions  

You should see:

1. **CI** — tests  
2. **CD** — deploy over SSH  

---

## Part F — What CD runs on the droplet

Each successful CD job SSHs in and runs roughly:

```bash
cd /opt/urlshortner
git fetch origin main
git reset --hard origin/main
docker compose build service
docker compose build gateway
docker compose up -d
curl http://127.0.0.1/actuator/health
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| CD: `Permission denied (publickey)` | Public key not in `/root/.ssh/authorized_keys`, or wrong private key in `DROPLET_SSH_KEY` |
| CD: `App directory missing` | Run `git clone ... /opt/urlshortner` on the droplet |
| CD: `git fetch` fails | Droplet needs outbound HTTPS; for private repos add a deploy key / token |
| Droplet OOM during build | Build one service at a time (already in CD); ensure swap exists (see droplet recovery notes) |
| Health check fails | `docker compose ps` and `docker compose logs gateway service` on the droplet |

---

## Security notes

- Never commit `do_deploy_key` (private key) to git.
- Prefer deleting local copies after adding to GitHub, or store them in a password manager.
- Keep port **22** open for SSH; keep **27017** / **6379** closed to the public internet.
