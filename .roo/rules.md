# PesaTrack Project Rules

## Implementation Status Document

The file `_docs/implementation-status.md` is the **single source of truth** for this project's implementation state. It tracks:

- Feature completion status (Executive Summary table)
- Detailed implementation tables (files, descriptions, line references)
- System architecture
- Current file structure
- Bug fixes & improvements history
- MVP checklist and Phase 2 progress
- Next steps / roadmap
- Brainstorm notes for future features

### When to READ this document

- At the start of any non-trivial task, read `_docs/implementation-status.md` to understand the current state of the project before making changes.
- When asked about project status, features, or architecture, reference this document.
- When planning new features, check the "Next Steps" and "Phase 2: Feature Progress" sections for context.

### When to UPDATE this document

After completing any of the following, update `_docs/implementation-status.md` to reflect the changes:

1. **New feature implemented** — Add to the Executive Summary table, Detailed Implementation tables, and Phase 2 progress. Update file structure if new files were created.
2. **Bug fixed** — Add to the "Bug Fixes & Improvements History" section.
3. **File created/renamed/deleted** — Update the "Current File Structure" section.
4. **Database migration added** — Update the Data Layer section and migration list.
5. **Dependency added/removed** — Update the Dependencies list and Android Configuration section.
6. **Component removed** — Add to the "Removed Components" table.
7. **Architecture changed** — Update the System Architecture diagram.
8. **Version released** — Update the Play Store Release section.
9. **New screen/route added** — Update Navigation and Presentation Layer sections.

### Update format rules

- Keep the existing markdown table format and section structure.
- Use relative file paths with line numbers in links: `[FileName.kt](../android/path/File.kt:LINE)`.
- Mark completed items with ✅, in-progress with 🟡, pending with ⏳.
- When adding to numbered lists (e.g., bug fixes), continue the existing numbering.
- Keep the "Current File Structure" tree accurate — add new files, remove deleted ones.

## Other Project Documentation

- `_docs/releases.md` — Version changelog. Update when a new version is released.
- `_docs/brainstorm.md` — Feature brainstorming notes.
- `plans/` directory — Feature implementation plans (read-only reference, don't modify completed plans).
