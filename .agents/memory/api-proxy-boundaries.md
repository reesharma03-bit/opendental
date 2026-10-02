---
name: API proxy boundaries
description: Distinguish browser CORS on the dashboard proxy from connectivity to the separate Open Dental upstream.
---

For same-origin dashboard requests forwarded by Vite, strip the browser's `Origin` header before proxying to Spring rather than broadly allowing preview origins. The Java API and its Open Dental upstream are separate services: its Swagger page documents the Java API, while `localhost` in backend configuration resolves inside the Replit server, not on the user's computer.

**Why:** Patient saves first failed Spring CORS, then reached the Java API but failed with connection refused at the separately configured upstream.

**How to apply:** Keep browser calls on the same-origin API proxy. For real Open Dental writes, configure a base URL that the Replit backend can reach; do not substitute the Swagger UI URL or assume a user's `localhost` is reachable from Replit.