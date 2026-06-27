# Golden Infinity Deployment: GoDaddy + Free Cloud

## Target Setup

- `https://goldeninfinity.com` hosts the static frontend on GoDaddy Web Hosting Deluxe.
- `https://api.goldeninfinity.com` hosts the Spring Boot backend on a free cloud app service.
- Neon hosts PostgreSQL.
- Upstash hosts Redis.

## 1. Create Free Database Services

### Neon PostgreSQL

Create a Neon project and database, then copy:

- host
- database name
- user
- password

The backend JDBC URL should look like:

```text
jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require
```

### Upstash Redis

Create an Upstash Redis database, then copy:

- host
- port
- password

Set `REDIS_SSL_ENABLED=true`.

## 2. Deploy Backend

Use the root `render.yaml` blueprint or create a Docker web service manually.

Required environment variables:

```text
SPRING_PROFILES_ACTIVE=prod
CORS_ORIGINS=https://goldeninfinity.com,https://www.goldeninfinity.com
DB_URL=jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require
DB_APP_USER=YOUR_NEON_USER
DB_APP_PASSWORD=YOUR_NEON_PASSWORD
REDIS_HOST=YOUR_UPSTASH_REDIS_HOST
REDIS_PORT=6379
REDIS_PASSWORD=YOUR_UPSTASH_REDIS_PASSWORD
REDIS_SSL_ENABLED=true
PII_ENCRYPTION_KEY=CHANGE_TO_A_LONG_RANDOM_SECRET
RATE_LIMIT_ENABLED=true
APPOINTMENT_EMAIL_ENABLED=false
APPOINTMENT_STAFF_EMAIL=your-company-email@example.com
```

After deploy, test:

```text
https://YOUR_BACKEND_HOST/api/v1/health
```

Expected response:

```json
{"status":"UP"}
```

## 3. Point API Subdomain

In GoDaddy DNS:

- Add `api` as a CNAME to the cloud backend hostname if the cloud provider gives a hostname.
- Or add `api` as an A record if the provider gives a fixed IP.

Final backend URL should be:

```text
https://api.goldeninfinity.com
```

## 4. Configure Frontend

Edit:

```text
frontend/src/js/config.js
```

Set:

```js
const productionApi = 'https://api.goldeninfinity.com';
```

## 5. Upload Frontend To GoDaddy

In GoDaddy cPanel File Manager:

1. Open `public_html`.
2. Upload all files inside `frontend/src`.
3. Keep the same folder structure:
   - `index.html`
   - `login.html`
   - `register.html`
   - `admin-appointments.html`
   - `css/`
   - `js/`
   - `images/`

Do not upload the whole repository to `public_html`; upload only `frontend/src` contents.

## 6. Smoke Test

Test these in order:

1. Open `https://goldeninfinity.com`.
2. Register a user.
3. Login.
4. Submit an appointment.
5. Login as staff/admin.
6. Open `https://goldeninfinity.com/admin-appointments.html`.
7. Confirm the appointment appears.
8. Confirm live updates show as active.

## Notes

- Free cloud services may sleep or throttle.
- Use paid backend/database tiers before relying on this for critical daily company operations.
- Keep backend secrets only in the cloud environment variable dashboard.
