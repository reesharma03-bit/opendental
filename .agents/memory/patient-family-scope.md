---
name: Patients and Families UI scope
description: User's scope constraint for the Patients & Families additions.
---

The user requested UI only for Patients & Families, Scheduling, Clinical Care, and Insurance & Billing, excluding Allergies, Allergy Defs, and Disease Defs. They want APIs for these preview screens later. The separate top-level Patients directory reads live Supabase data and has user-approved Add/Edit actions through Open Dental; it has no delete action.

**Why:** The user limited Patients & Families, Scheduling, Clinical Care, and Insurance & Billing previews to UI, then requested live Supabase patient records and explicitly approved Add/Edit actions that modify real Open Dental records.

**How to apply:** Keep the existing excluded screens unchanged. Keep the added Patients & Families, Scheduling, Clinical Care, and Insurance & Billing screens accessible as UI-only previews, with clearly labeled fictional demo data. The top-level Patients menu reads a minimal live projection from Supabase and supports add/edit through the Open Dental API; do not add delete controls. Preserve API sidebar groups up to and including Insurance & Billing only.