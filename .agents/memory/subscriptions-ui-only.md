---
name: Subscription screen scope
description: User's boundary for Open Dental Subscription screen work.
---

Keep the Subscription screen UI-only. Use fictional, in-memory preview records and local interactions; do not make API requests or change the Open Dental API for this screen.

**Why:** The user explicitly requested UI only and said not to change any API.

**How to apply:** Keep the current screen as a local preview. Only connect or change API behavior if the user later asks for it.