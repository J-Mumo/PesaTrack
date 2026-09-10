# `src/legacy/` — Archived Daraja/M-PESA v1 code

This folder contains the original Daraja (M-PESA STK Push) backend that shipped in `backend/` before **AI Pro Phase 1**.

## Why it's here (not deleted)

Per [AGENTS.md](../../../AGENTS.md), the Daraja code was **never consumed by the shipped Play Store Android app**. It was a `plans/daraja-production-migration.md` deliverable that the Android app has since diverged from. Rather than delete it, we archive it under `src/legacy/` for:

- Git-diff clarity — future contributors can see the shape of the old backend without spelunking history.
- Reference — if the M-PESA STK-Push flow is ever revived (e.g. `plans/stk-push-removal-plan.md` reversal), this is the starting point.
- Aligns with the `backend/src/legacy/` layout called out in [plans/ai-pro-phase1-spec.md §4.1](../../../plans/ai-pro-phase1-spec.md).

## Status

- **Not wired.** None of these files are `require()`'d from the new `src/index.js`.
- **Not built.** They will not be executed at runtime, but they will be included in `docker build` context. Consider adding `src/legacy/` to `.dockerignore` if image size becomes a concern.
- **Not tested.** No CI runs on this code.

## Contents (mapped from the pre-Phase-1 layout)

| Legacy path here                              | Original path                                |
|-----------------------------------------------|----------------------------------------------|
| `routes-callback.js`                          | `src/routes/callback.js`                     |
| `routes-payment.js`                           | `src/routes/payment.js`                      |
| `darajaService.js`                            | `src/services/darajaService.js`              |
| `databaseService.js`                          | `src/services/databaseService.js`            |
| `paymentService.js`                           | `src/services/paymentService.js`             |
| `config-daraja.js`                            | `src/config/daraja.js`                       |
| `middleware-validation.js`                    | `src/middleware/validation.js`               |

## When to delete

Once **AI Pro Phase 2 (Coach Insights)** has been in production for ≥ 90 days without needing to reference this code, delete the folder. Git history preserves it forever.
