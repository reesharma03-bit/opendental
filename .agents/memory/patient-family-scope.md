---
name: Patients and Families UI scope
description: User's scope constraint for the Patients & Families additions.
---

The user requested UI only for Patients & Families, Scheduling, Clinical Care, and Insurance & Billing, excluding Allergies, Allergy Defs, and Disease Defs. They want APIs for these preview screens later, and explicitly authorized connecting the top-level Patients directory to live Supabase data. Do not remove the added UI screens or connect previews to a backend. Keep the top-level Patients directory read-only and database-backed. In the API sidebar, keep groups through Insurance & Billing and remove every group after it.

**Why:** The user explicitly limited Patients & Families, Scheduling, Clinical Care, and Insurance & Billing previews to UI, then separately requested the top-level Patients directory to show live Supabase data.

**How to apply:** Keep the existing excluded screens unchanged. Keep the added Patients & Families, Scheduling, Clinical Care, and Insurance & Billing screens accessible as UI-only previews, with clearly labeled fictional demo data. The top-level Patients menu is a separate, read-only directory that should load live rows from the existing Supabase-backed repository. Preserve API sidebar groups up to and including Insurance & Billing only.