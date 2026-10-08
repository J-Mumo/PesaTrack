# Public-to-private transition — execution checklist

**Date opened:** 2026-10-05 · **Status:** In progress, NOT ready to switch visibility · **Base branch:** `main`

**Deployment hold:** The maintainer explicitly deferred deployment until additional bug fixes are ready. This change may be committed and pushed, but do not deploy the website, release the app or change repository visibility as part of this task.

Canonical public policy: https://pesatrack.jmumo.com/privacy  
Legacy URL embedded in installed versions: https://j-mumo.github.io/PesaTrack/privacy-policy.html

**Public listing check (2026-10-05):** Play Store HTML returned 200 and still contained the old GitHub Pages URL, not the website privacy URL. Public Data Safety mentions App activity and Device or other IDs; verify the complete current declaration in Play Console. The source repository is still public.

Check a box only after independently verifying the result; a local file edit is not a deployment or a Play Console change. Keep credentials, audit findings containing secrets, and Play Console screenshots in a restricted location, not in this repository. The detailed procedure is in [the transition plan](../plans/private-repo-transition-plan.md).

## 1. Local changes and privacy accuracy — maintainer

- [x] Start from `main` synced with `origin/main`; keep unrelated untracked files out of this change.
- [x] Route Android About and telemetry-consent links through one shared website privacy URL constant in local code.
- [x] Remove public website links that would point to the private repository or its issue tracker; provide public support alternatives in English and Kiswahili.
- [x] Configure website metadata and factsheet to point to the real domain and policy URL in local code.
- [x] Verify the website, Kiswahili policy and old GitHub Pages policy returned 200 over HTTPS on 2026-10-05 (this does **not** guarantee the legacy link will survive the visibility change).
- [ ] Reconcile the policy on `main` and the deployed website with the **actual current Play Store version and Data Safety form**. The live site last updated date is September 3, 2026; do not merge AI Pro disclosures from a feature branch unless that feature is shipped or about to ship. Record reviewer and date: __________.
- [ ] Verify policy canonical URL, English/Kiswahili parity and content after any corrections. Record URLs and date: __________.
- [x] Android `testDebugUnitTest lintDebug --offline` passed locally on `main` (2026-10-05).
- [x] Website `pnpm build` passed locally and generated `factsheet.privacyPolicyUrl = https://pesatrack.jmumo.com/privacy` with a matching canonical URL (2026-10-05).
- [ ] Resolve `git diff --check` (currently reports an extra blank line at the end of the Android telemetry consent sheet); click both app policy links on a device. Record results: __________.
- [x] Restore the website type-check gate: installed `@astrojs/check` 0.9.10 as a development dependency with the lockfile updated. Fixed the generated Pagefind bundle import; `pnpm check` reports 0 errors, 0 warnings and 0 hints across 46 files (2026-10-05).
- [ ] Review and commit *only* transition files from `main`, preferably through a short-lived branch based on `main` and a PR. Exclude local signing keys, configuration, and unrelated untracked files. Record commit/PR: __________.

## 2. Security and delivery readiness — repo/hosting admin

- [x] Inspect tracked sensitive-looking filenames (including history) without displaying content; only a sample environment template was found by filename. **This is not a secret scan.**
- [ ] Run a dedicated secret scanner over all Git history and review the working tree/CI logs; rotate or revoke any exposed credentials, including removed files. Store the report privately. Record reviewer/date: __________.
- [ ] Confirm website production deploy source and authenticated Git fetch/build/deploy procedure after visibility changes; `.github/workflows/website.yml` builds/tests, but does **not** deploy the site. Record deploy owner and tested method: __________.
- [ ] Confirm private-repo Actions availability/permissions, scheduled issue automation, rulesets, collaborators, deploy keys and webhooks. Record owner/date: __________.
- [ ] Back up the repository (including branches/tags), confirm recoverability, and inventory GitHub Pages ownership/settings. Record backup location privately and test date: __________.
- [ ] Decide on a permanently public host/redirect for the **exact legacy GitHub Pages URL**. Test anonymous access *after* repository privatization; do not assume GitHub Pages will continue serving it. Record the solution and rollback: __________.
- [ ] Acknowledge that making the repo private does not remove prior public clones or revoke permissions under the existing MIT license. Record legal/product decision: __________.

## 3. Publish public surfaces — website maintainer / Play Console owner

- [x] Deploy website changes independently of the GitHub visibility change; Hetzner `~/apps/pesatrack` fast-forwarded to `8b27c94` and rebuilt `website/docker-compose.yml` on 2026-10-08. Homepage, `/privacy` and `/factsheet.json` returned successfully; public feature/docs routes were generated. Follow up with the full logged-out link scan: __________.
- [ ] Update **Play Console → Store presence → Main store listing → Privacy policy** to https://pesatrack.jmumo.com/privacy; confirm it opens while logged out, and compare Data Safety with the current policy. Record operator/date: __________.
- [ ] Review Play Store **full description**, store support/developer website, and other public channels for “open source,” GitHub issue links, or other claims invalid after cutover; update or explicitly justify each claim. Record result/date: __________.
- [ ] Ship the Android URL change in a tested signed release when appropriate; until then, existing builds still open GitHub Pages. Record version code/track/date: __________.
- [ ] Retest legacy URL from a logged-out browser using an installed *older* app or its exact URL. Do not flip visibility if it has no independent public fallback. Record result/date: __________.

## 4. Visibility cutover — GitHub admin only (GO/NO-GO)

- [ ] **GO decision:** Sections 1–3 complete; site, Play listing and legacy policy link publicly reachable; security and hosting audit signed off. Record sign-off/date: __________.
- [ ] Switch `J-Mumo/PesaTrack` to private in GitHub Settings; record operator/time: __________.
- [ ] Logged-out checks: repository is private; new and legacy policy URLs and the public site still work. Record results: __________.
- [ ] Logged-in checks: authorized clone/fetch/PR, protected `main`, CI and authenticated website deployment work. Record results: __________.
- [ ] Review audit logs, rotate unused credentials, monitor website/policy availability, and update [implementation status](implementation-status.md) with **verified** results. Record final review: __________.

**Stop/rollback:** If the site or either privacy URL becomes unavailable, halt cutover, restore the last known-good public policy host/deployment, and update Play Console if necessary. Do not mark the transition complete merely because GitHub reports “private.”