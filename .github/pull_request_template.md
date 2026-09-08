<!--
  Thanks for opening a PR!

  Please fill in the sections below. Delete any that don't apply.
  For website-sync guidance, see AGENTS.md § "Website Sync Check" and
  plans/website-full-plan.md §16.1.
-->

## Summary

<!-- 1–3 sentences on what this PR changes and why. -->

## Feature Decision Filter

<!--
  Required for any non-trivial change. See AGENTS.md § "Feature Decision Filter"
  and plans/product-principles.md.
-->

1. **Which principle does this serve?**
2. **What user behaviour does it change?** (awareness / spending / saving / investing / none)
3. **What is the honest downside or failure mode?**
4. **How is success observable to the user?**

## Website Sync Check

<!--
  Required for any Android change that could be user-visible.
  See AGENTS.md § "Website Sync Check" and plans/website-full-plan.md §16.1
  for the trigger table.
-->

Pick exactly one:

- [ ] Site update required — included in this PR.
- [ ] Site update required — filed as `#<issue-number>` and linked below.
- [ ] Site update NOT required because: <!-- explain -->

If the site was touched:

- [ ] Kiswahili mirror (`/sw/*`) reviewed for the four mirrored pages: `/`, `/how-it-works`, `/privacy`, `/faq`.
- [ ] `pnpm --dir website build` succeeds locally.
- [ ] `factsheet.json` still accurate (`currentVersion`, `supportedSenders`, `features.internetPermission`).

## Docs & release notes

- [ ] `_docs/implementation-status.md` updated (see AGENTS.md § Auto-Update Rule).
- [ ] `_docs/releases.md` updated if this is a release PR.

## Verification

<!--
  What did you run to make sure this works?
  Prefer: `./gradlew lint` and `./gradlew test` for Android;
  `pnpm --dir website build` for site changes.
-->
