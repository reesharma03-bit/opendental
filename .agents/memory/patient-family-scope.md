---
name: Patients and Families UI scope
description: User's scope constraint for the Patients & Families additions.
---

The user requested UI only for Patients & Families, excluding Allergies, Allergy Defs, and Disease Defs. They want the UI first and will create APIs later. Do not remove the added UI screens or make them depend on a backend connection. Do not create new APIs for these additions.

**Why:** The user explicitly limited this work to the UI and repeated that they wanted UI, with APIs to be created later.

**How to apply:** Keep the existing excluded screens unchanged. Keep the added screens accessible as UI-only previews, with clearly labeled fictional demo data. Do not connect them to backend reads or writes unless the user requests integration separately.