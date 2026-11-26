# AI Database Agent web client

React/Vite client for the AI Database Agent. During development, Vite proxies `/api` requests to the Spring Boot service on port `8083`.

```powershell
npm install
npm run dev
```

Quality checks:

```powershell
npm run lint
npm run build
```

The supported API contract is documented in `../docs/API.md`. Production images are built from the repository root `Dockerfile`; the compiled UI is packaged into the Spring Boot application.
