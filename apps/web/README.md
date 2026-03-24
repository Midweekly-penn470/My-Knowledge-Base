# Web App

This package contains the React + Vite frontend for `My Knowledge Base`.
It is the browser-facing workbench for knowledge base selection, document
upload, ingestion tracking, and streaming QA.

## Scope

- workspace shell and navigation
- knowledge base document management
- QA/chat workspace
- responsive product UI and visual parity

## Local Run

```powershell
cd apps\web
npm install
npm run dev -- --host 0.0.0.0 --port 3001
```

## Verification

- `npm run lint`
- `npm run build`
- `npm run test:e2e`

## Environment

- frontend reads backend base URL from `VITE_API_BASE_URL`
- default local frontend port: `3001`
