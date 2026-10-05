# PesaTrack — Public-to-Private Repository Transition Plan

> **Created:** 2026-10-05  
> **Status:** In progress  
> **Scope:** GitHub repository visibility, public-facing links, privacy-policy hosting, release operations, and access/security controls.  
> **Implementation branch:** `main` only

Track verified work and console handoffs in the [execution checklist](../_docs/private-repo-transition-checklist.md). Local edits alone do not satisfy the visibility go/no-go gate.

## 1. Objective

Transition `J-Mumo/PesaTrack` from public to private GitHub visibility without breaking:

- The Google Play Store privacy-policy requirement.
- The deployed website and its privacy-policy route.
- The Android app's in-app Privacy Policy link.
- Website deployment and GitHub Actions workflows.
- Release, support, and maintainer access processes.

The deployed website becomes the public source for the privacy policy. The confirmed target URL is:

`https://pesatrack.jmumo.com/privacy`

Before implementation, verify that the production domain and `/privacy` route resolve over HTTPS. If the deployed canonical URL differs, record the final URL in the implementation checklist and use that URL consistently.

## 2. Product and policy decisions

**Feature decision filter:** This serves privacy and honest communication by keeping the public policy/support available while changing source visibility. The intended user behavior is unchanged: users can inspect how their data is handled without a GitHub account. The honest downside is reduced public source auditability and a risk that the legacy GitHub Pages URL embedded in installed apps stops working. Success is observable when a logged-out user can open the policy from Google Play and from both old and new app versions, while repository source is inaccessible without authorization.

1. The Android repository becomes private; the public website remains publicly accessible.
2. The privacy policy must not depend on GitHub Pages or any GitHub repository being public.
3. The canonical public privacy-policy URL is the deployed website route, not the old GitHub Pages URL.
4. The Play Store listing and the Android About screen must point to the same deployed URL.
5. Existing users must retain access to the policy without installing an app update.
6. No raw SMS, transaction records, or PII may be exposed while auditing or migrating the repository.
7. This is an operational visibility change, not permission to remove privacy disclosures or change the app's data practices.
8. Previously published source and copies distributed under the existing MIT license remain subject to that license; making the repository private does not retract access already granted or erase forks/clones.

## 3. Current impact inventory

| Surface | Current state | Planned action |
|---|---|---|
| Android About screen and telemetry consent sheet | Both linked to GitHub Pages | Use one shared app constant for the deployed website URL (implemented locally on `main`; not yet released) |
| Play Store listing | Privacy URL is managed in Play Console | Replace with the deployed website URL |
| Legacy policy | `docs/privacy-policy.html`, historically hosted through GitHub Pages | Keep temporarily for history/reference; stop relying on it as the Play Store URL |
| Website policy | `website/src/pages/privacy.astro`, route `/privacy` | Confirm production deployment, HTTPS, canonical tags, and content parity |
| Kiswahili policy | `website/src/pages/sw/privacy.astro` | Confirm `/sw/privacy` remains reachable and linked from the English policy |
| Website deployment | GitHub Actions workflow under `.github/workflows/` | Verify private-repository Actions and hosting permissions before visibility change |
| Public GitHub links | README, website/support/schema, release/docs links may reference GitHub | Classify each as intentional private-maintainer link or replace with public website/support link |
| GitHub Pages | Old privacy URL still returned 200 on 2026-10-05 | Keep public until already-installed app versions that embed it are accounted for; an app update alone cannot fix those versions |

**Readiness finding (2026-10-05):** The public website's English and Kiswahili policies returned 200 over HTTPS, but the live English policy still says “Last updated: September 3, 2026.” `main` contains that same older policy; the separate AI Pro feature branch contains newer disclosures. Before deploying any newer app/website behavior, reconcile the policy with the **actually shipped** app and Play Data Safety declarations. Do not copy unmerged feature-branch policy language to `main` without checking release status.

## 4. Implementation sequence

### Phase 0 — Worktree and branch control

Perform all implementation work from `main`:

1. Stop any work on `feat/ai-pro-plan` for this change.
2. Save or commit unrelated local changes before switching branches; do not mix them into this transition.
3. Fetch remotes and check out `main`.
4. Fast-forward `main` from `origin/main` only after confirming the working tree is clean.
5. Create a short-lived implementation branch from `main` (recommended), for example `chore/private-repo-transition`, unless the maintainer explicitly requires direct commits to `main`.
6. Confirm the branch and base commit before editing.

Do not use the AI feature branch as the source for this work unless its changes have first been intentionally merged into `main`.

### Phase 1 — Pre-change inventory and security review

1. Export an inventory of:
   - GitHub Actions workflows, deployment credentials, environments, deploy keys, and webhooks.
   - Collaborators, teams, outside contributors, branch protections, rulesets, and required checks.
   - GitHub Pages settings and the current custom-domain/DNS configuration.
   - Play Console users and the account that will update the listing.
   - Website hosting provider and deployment source.
2. Search the full Git history and current tree for secrets, credentials, tokens, private keys, service-account JSON, `.env` files, and personal data.
3. Treat every credential that has ever existed in the public repository as exposed, even if it has since been deleted. Rotate or revoke it before or during the visibility transition.
4. Pay particular attention to Firebase configuration, backend deployment credentials, GitHub tokens, Play Console credentials, and domain/DNS credentials. Confirm which values are public client configuration versus secrets.
5. Confirm that ignored files and local-only files are not required by CI or the website build.
6. Record the result and any rotations in a restricted operational record; do not add secrets to this repository or to the plan.

### Phase 2 — Public URL and privacy-policy migration

1. Verify the production website:
   - `https://pesatrack.jmumo.com/privacy` returns HTTP 200.
   - The page is reachable without GitHub authentication.
   - HTTPS certificate, redirects, canonical URL, and `lastUpdated` are correct.
   - English and Kiswahili privacy pages are available.
2. Compare the deployed policy with the current Android policy content, including the opt-in analytics, subscription, and Coach Insights disclosures.
3. Update the About screen and telemetry consent-sheet privacy links to the verified production URL.
4. Keep the URL centralized as a named constant; do not duplicate hard-coded policy URLs in multiple Android screens.
5. Update any website factsheet, structured data, footer, support, README, or release documentation that identifies the privacy-policy URL.
6. Decide whether `docs/privacy-policy.html` remains as a repository reference. If retained, add a clear non-authoritative note or redirect strategy only if the hosting setup supports it. Do not remove it until historical links and release records have been checked.
7. Ensure the policy does not claim that the repository is public or that GitHub Pages is the canonical host.

### Phase 3 — Automated checks and deployment readiness

1. Confirm GitHub Actions workflows work for a private repository:
   - Checkout uses the repository token or an explicitly configured secret.
   - Website package installation can access all required dependencies.
   - Deployment credentials are stored as GitHub Actions secrets or environment secrets.
   - Pull-request workflows do not expose secrets to untrusted fork code.
   - Scheduled jobs and issue/comment automation have the required permissions.
2. Confirm the website hosting provider is not coupled to public GitHub access. If it is, migrate the deployment integration to an authenticated GitHub App, deploy hook, or independent CI deployment before changing visibility.
3. Confirm GitHub Pages is not the only host for the website or privacy policy. If it is still needed as a fallback, document the authenticated/private-repository limitation and migrate first.
4. Run the relevant validations from the repository root:
   - Android unit tests and lint from `android`.
   - Website build from `website`.
   - Link checks for the production privacy URL.
5. Build a debug APK and confirm the About screen opens the production policy URL.
6. An Android release is required for *installed apps* to use the new URL; coordinate it with the next planned release if the legacy URL can remain public meanwhile. Update release notes when that release is prepared. Do not disable the old URL solely because a newer build exists.

### Phase 4 — GitHub visibility transition

Perform this as a scheduled, reversible change with an administrator present:

1. Confirm backups/mirrors exist and that the repository can be restored if needed.
2. Confirm all intended maintainers have access to the private repository before changing visibility.
3. Confirm branch protections and required checks apply to `main` after the change.
4. **Go/no-go gate:** Do not change visibility until the new website/app links are deployed as applicable, the Play Console listing has been updated, the legacy URL's installed-app impact has a working public-hosting or redirect solution, current privacy disclosures have been reviewed, and the security/hosting audit is signed off.
5. Change repository visibility from public to private in GitHub settings.
6. Immediately verify:
   - Anonymous access to the repository is denied.
   - Authorized maintainers can clone, fetch, push, and open pull requests.
   - Actions workflows can run and deploy.
   - Website production remains available.
   - The privacy URL remains publicly reachable.
   - The old GitHub Pages URL is either intentionally redirected, intentionally retired, or documented as no longer authoritative.
7. Revoke unused collaborators, deploy keys, personal access tokens, webhooks, and GitHub Apps.
8. Review audit logs and workflow runs for unexpected access failures or credential exposure.

### Phase 5 — Play Console update

1. In Play Console, replace the old GitHub Pages privacy-policy URL with the verified deployed website URL:
   - `https://pesatrack.jmumo.com/privacy`
2. Save and verify the URL using Play Console's external-link preview/validation.
3. Check the public Store listing in an incognito browser or logged-out session.
4. Confirm the Data Safety form and privacy policy still describe the shipped app accurately; repository visibility itself does not change the app's data collection or sharing behavior.
5. Record the date, operator, and final URL in the release/operations record.

### Phase 6 — Documentation and status updates

1. Update `_docs/implementation-status.md` with:
   - The repository visibility change.
   - The canonical website privacy URL.
   - The Android and Play Console surfaces updated.
   - Website deployment verification.
   - Any GitHub Actions or GitHub Pages operational changes.
2. Update `_docs/releases.md` only if an Android release is made for the URL change.
3. Update README and website copy so public users are directed to the website, not private GitHub source or GitHub Pages.
4. Add the required website-sync statement to the change record: “Site update included — public support/source links and canonical metadata updated; the privacy policy text itself is unchanged.” Record the site build and production deployment validation separately.
5. Do not modify completed plan specifications; this plan is the operational source for the repository-visibility transition.

## 5. Verification checklist

### Before changing visibility

- [x] Implementation started from `main`; unrelated pre-existing untracked files remain outside this change.
- [x] `main` is synchronized with `origin/main`.
- [x] Production privacy URL is verified over HTTPS.
- [x] Android About and telemetry-consent policy links use the deployed website URL in local code (not yet released).
- [x] Website canonical URL defaults to `https://pesatrack.jmumo.com`.
- [x] Website build completes successfully.
- [ ] Website policy matches the current app behavior.
- [ ] Deployed website/Android changes and Play Console listing are updated and independently verified.
- [ ] Legacy URL remains publicly reachable for installed app versions after visibility changes (or verified redirect hosted outside the private repository).
- [ ] Git history and current tree were audited for secrets/PII.
- [ ] Exposed credentials were rotated or revoked.
- [ ] Website deployment works with a private repository or has been decoupled.
- [ ] GitHub Actions permissions and secrets are documented.
- [ ] Maintainers, backups, branch rules, and recovery steps are confirmed.

### After changing visibility

- [ ] Anonymous GitHub repository access is denied.
- [ ] Authorized maintainer access works.
- [ ] `main` protection and CI checks work.
- [ ] Website homepage and `/privacy` remain public and healthy.
- [ ] Android About screen opens the website policy URL.
- [ ] Play Store privacy URL points to the website policy.
- [ ] Old GitHub Pages links are handled intentionally.
- [ ] No workflow secrets or deployment credentials appear in logs.
- [ ] `_docs/implementation-status.md` is updated.
- [ ] Final URLs and rollback steps are recorded.

## 6. Rollback

If the website, Play Console link, or CI deployment breaks:

1. Keep the deployed website and privacy URL online; do not restore dependence on a public source repository.
2. Restore the affected GitHub Actions/hosting credential or deployment integration.
3. If required, temporarily restore repository visibility only through an administrator after assessing the exposure risk.
4. Re-run URL, website, Android, and Play Console verification.
5. Record the incident and corrective action before attempting the visibility change again.

## 7. Acceptance criteria

The transition is complete when the source repository is private, authorized maintainers can work from protected `main`, the website deploys independently or through authenticated CI, and both the Android app and Google Play listing point to a publicly reachable deployed privacy policy at the same verified URL. No user-facing privacy disclosure may be lost or made inaccessible as a result of the repository transition.
