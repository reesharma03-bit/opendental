---
name: Browser-test capability
description: A mismatch between the local testing guide and runtime subagent capabilities.
---

The local testing guide can advertise a browser-testing subagent even when the runtime rejects the `testing` configuration kind.

**Why:** The runtime returned `Unknown config kind: testing` despite the local guide documenting it. A guide alone is not proof that the capability is enabled.

**How to apply:** Re-evaluate current capability availability on future work. If the runtime rejects it, do not repeatedly retry the same configuration or claim browser interactions were tested. Use supported unit, request, and screenshot checks, and distinguish those from interactive browser verification.