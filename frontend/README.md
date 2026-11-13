# LandStack frontend

React 18 · TypeScript (strict) · Vite 5 · Tailwind 3 · React Router 6 · TanStack Query 5 · React Hook Form + Zod ·
Axios (single client in `src/lib/api.ts`) · Leaflet/React-Leaflet · Recharts · Lucide icons.

```bash
npm install
npm run dev          # http://localhost:5173, proxies /api -> http://localhost:8080 (LANDSTACK_BACKEND_URL to change)
npm run lint && npm run typecheck && npm test && npm run build
E2E_PASSWORD=<demo password> npm run e2e    # Playwright against a running stack
```
The API base URL defaults to the relative `/api/v1` so the session cookie stays same-origin; nginx
(`nginx.conf`) does the same in Docker. Role checks in the UI only hide controls; the backend authorises
every request. No business rules (normalisation/validation) live in the frontend — it renders backend results.
