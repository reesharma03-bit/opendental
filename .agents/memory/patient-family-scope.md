---
name: Patients and Families UI scope
description: User's scope constraint for the Patients & Families additions.
---

The user requested UI only for Patients & Families, excluding Allergies, Allergy Defs, and Disease Defs. Do not create new APIs for these additions.

**Why:** The user explicitly limited this work to the UI.

**How to apply:** Keep the existing excluded screens unchanged. Do not connect the new previews to backend writes unless the user requests integration separately.