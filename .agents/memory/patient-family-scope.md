---
name: Patients and Families UI scope
description: User's scope constraint for the Patients & Families additions.
---

The user requested UI only for Patients & Families, Scheduling, and Clinical Care, excluding Allergies, Allergy Defs, and Disease Defs. They want the UI first and will create APIs later. Do not remove the added UI screens or make them depend on a backend connection. Do not create new APIs for these additions. In the API sidebar, keep groups through Insurance & Billing and remove every group after it.

**Why:** The user explicitly limited this work to the UI and repeated that they wanted UI, with APIs to be created later. They also specified which sidebar groups to keep and requested UI for every Scheduling and Clinical Care menu.

**How to apply:** Keep the existing excluded screens unchanged. Keep the added Patients & Families, Scheduling, and Clinical Care screens accessible as UI-only previews, with clearly labeled fictional demo data. Do not connect them to backend reads or writes unless the user requests integration separately. Preserve API sidebar groups up to and including Insurance & Billing only.