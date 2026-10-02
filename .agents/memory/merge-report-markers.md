---
name: Generated test report conflict checks
description: False conflict-marker detection in Spring Boot test output during project syncing.
---

The merge completion check can mistake Spring Boot's equals-sign banner in generated XML test reports for unresolved conflict markers.

**Why:** Accepting the main project's report still failed the marker check even though there were no actual merge markers.

**How to apply:** Preserve the chosen generated report rather than merging logs by hand. If the offending text is inside normal XML text, encode its equals signs as XML character entities so the parsed log is unchanged.