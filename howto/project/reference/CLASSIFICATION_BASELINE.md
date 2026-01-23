***REMOVED*** Scanium – First-Shot Classification Baseline

**Status:** Locked / Active
**Owner:** Product
**Applies to:** Android app, backend reasoning layer, AI pipeline
**Last updated:** 2026-01-21

---

***REMOVED******REMOVED*** 1. Purpose of this document

This document defines the **authoritative baseline** for how Scanium must behave
when classifying an object from the **first picture** taken by the user.

It exists to:
- maximize trust on first interaction
- eliminate "joke" or embarrassing classifications
- create a clear **wow factor**
- support Scanium's core positioning: **Sell faster**

This is a **product contract**, not an implementation detail.
All future AI, UX, and backend decisions must align with it.

---

***REMOVED******REMOVED*** 2. Product positioning (context)

Scanium is **not** a generic object catalog.

Its primary job-to-be-done is:

> **Help users sell items faster and with less effort.**

Classification therefore exists to:
- accelerate listing creation
- reduce cognitive load
- feel intelligent and reasonable immediately

Perfect accuracy is **less important** than perceived correctness and usefulness.

---

***REMOVED******REMOVED*** 3. Key insight: first picture decides trust

User feedback indicates:
- wrong classifications are perceived as jokes
- early mistakes break trust instantly
- later corrections do not recover confidence

Therefore:

> **If the first picture feels smart, users forgive everything else.
> If it feels wrong, nothing else matters.**

---

***REMOVED******REMOVED*** 4. Definition of "wow" first-shot classification

A "wow" first shot is **not**:
- exhaustive
- overly specific
- technically perfect
- conservative to the point of uselessness

A "wow" first shot **is**:
- reasonable
- helpful
- resale-oriented
- confidence-aware
- easy to correct without friction

---

***REMOVED******REMOVED*** 5. AI architecture (locked)

Scanium uses a **two-stage AI pipeline by design**.

***REMOVED******REMOVED******REMOVED*** 5.1 Stage 1 – Perception (Google Vision)

Responsible for:
- object detection
- labels
- bounding boxes
- OCR (brand/model hints)
- dominant colors
- logos (when available)

This stage answers:
> "What do we see in the image?"

Raw perception output is **never shown directly to users**.

---

***REMOVED******REMOVED******REMOVED*** 5.2 Stage 2 – Reasoning (OpenAI)

Responsible for:
- interpreting perception signals
- ranking plausible, sellable hypotheses
- resolving ambiguity
- generating human-readable explanations
- deciding fallback and refinement behavior

This stage answers:
> "What is the most reasonable and useful interpretation for selling?"

The reasoning layer is **product-aware**, not vision-pure.

---

***REMOVED******REMOVED*** 6. First-shot UX contract (locked)

After the first picture, Scanium MUST show:

- a **ranked list of 3 likely matches**
- no auto-selection
- probabilistic, human language
- one-tap confirmation
- an obvious refinement path

***REMOVED******REMOVED******REMOVED*** 6.1 Example (conceptual)

Most likely this is:

1. Robot vacuum
   _Round shape, floor sensors visible_

2. Smart speaker
   _Compact speaker-like form factor_

3. Air purifier
   _Air intake vents detected_

Add another photo to refine

---

***REMOVED******REMOVED******REMOVED*** 6.2 UX rules (non-negotiable)

| Rule | Decision |
|----|----|
| Result type | Ranked hypotheses |
| Tone | Probabilistic ("Most likely…") |
| Visible options | 3 (no scrolling) |
| Auto-selection | Never |
| User confirmation | Always explicit |

---

***REMOVED******REMOVED*** 7. Reasoning output contract (conceptual)

The reasoning layer must return:

- 3–5 ranked hypotheses
- confidence bands (high / medium / low)
- short, human-readable explanations
- a global confidence score
- an explicit refinement signal if needed

The UI will show only the **top 3**.

---

***REMOVED******REMOVED*** 8. Bias and sellability rules

When uncertain, the reasoning layer MUST:

- strongly bias toward **common, sellable household categories**
- avoid obscure or niche interpretations
- prefer usefulness over theoretical correctness

Example:
- Prefer "Kitchen appliance" over "Sous-vide circulator" on first shot
- Precision can be refined later

---

***REMOVED******REMOVED*** 9. Confidence & fallback behavior (locked)

***REMOVED******REMOVED******REMOVED*** 9.1 Auto-progress rule
- Allowed only if global confidence ≥ **70%**
- Still requires user confirmation (never silent)

***REMOVED******REMOVED******REMOVED*** 9.2 Below threshold behavior
- Show ranked list
- Show "Add another photo to refine"
- Never show error or "I don't know" state

***REMOVED******REMOVED******REMOVED*** 9.3 Forced refinement
A second photo is requested only when:
- top hypotheses are too close to distinguish visually

---

***REMOVED******REMOVED*** 10. Handling wrong classifications

Wrong classifications are **not failures**.

They are:
> **Training moments and collaboration opportunities.**

***REMOVED******REMOVED******REMOVED*** 10.1 UX behavior

Instead of:
> "Wrong category"

Show:
> **"Help me improve — what is it?"**

Tone is cooperative, not apologetic.

---

***REMOVED******REMOVED******REMOVED*** 10.2 Learning rule (v1)

- Corrections are remembered **locally on device**
- They influence future classifications for similar visuals
- No global cross-user learning yet (privacy + control)

Users should feel:
> "The app is learning from me."

---

***REMOVED******REMOVED*** 11. What success means (explicit)

First-shot success is defined as:

> **Being reasonable and helpful on the first picture.**

Not:
- maximum accuracy
- minimum uncertainty
- silence on ambiguity

---

***REMOVED******REMOVED*** 12. Scope exclusions (explicit)

This baseline does NOT define:
- exact ML models
- pricing logic
- marketplace posting flows
- global learning mechanisms

Those are layered **on top** of this contract.

---

***REMOVED******REMOVED*** 13. Authority

This document is **authoritative**.

If:
- implementation,
- AI prompts,
- UX decisions,
- or future features

conflict with this document,

➡️ **this document wins unless explicitly revised.**

---
