# Code Mode Rules

## Implementation Status Update Requirement

IMPORTANT: After implementing any feature, bug fix, or structural change, you MUST update `_docs/implementation-status.md` before marking the task as complete. Do not use `attempt_completion` until the implementation status document reflects all changes made.

### Checklist before completing any task

- [ ] Did I create, rename, or delete any files? → Update "Current File Structure" section
- [ ] Did I add a new feature? → Update Executive Summary table + Detailed Implementation tables + Phase 2 progress
- [ ] Did I fix a bug? → Add entry to "Bug Fixes & Improvements History" (continue numbering)
- [ ] Did I add a database migration? → Update Data Layer section
- [ ] Did I add/remove a dependency? → Update Dependencies list
- [ ] Did I add a new screen or route? → Update Navigation and Presentation Layer sections
- [ ] Did I change the architecture? → Update System Architecture diagram

If ANY of the above apply, update `_docs/implementation-status.md` before calling `attempt_completion`.
