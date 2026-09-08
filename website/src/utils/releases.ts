import fs from 'node:fs';
import path from 'node:path';

/**
 * Release-note utilities used at build time by /factsheet.json,
 * /changelog, and (indirectly) /llms-full.txt.
 *
 * Source of truth: `_docs/releases.md`. See plans/website-full-plan.md §16.1.
 */

export interface ReleaseSummary {
  /** Semantic version string, e.g. "1.4.1" */
  version: string;
  /** Play Store version code (integer) */
  versionCode?: number;
  /** ISO date string (YYYY-MM-DD) */
  date?: string;
  /** Track — "Production", "Closed Testing — PesaTrack Alpha", etc. */
  track?: string;
  /** Free-form status text taken verbatim from the release summary table. */
  status?: string;
}

const RELEASES_PATH = path.resolve(process.cwd(), '..', '_docs', 'releases.md');

let cached: ReleaseSummary[] | null = null;

function loadReleases(): ReleaseSummary[] {
  if (cached) return cached;
  if (!fs.existsSync(RELEASES_PATH)) {
    cached = [];
    return cached;
  }

  const raw = fs.readFileSync(RELEASES_PATH, 'utf8');
  const rows: ReleaseSummary[] = [];

  // Rows in the summary table look like:
  //   | **1.4.1** | 11 | 2026-06-24 | Closed Testing … | 🟡 Pending upload |
  //
  // We stop scanning at the first non-table line after the header, to avoid
  // matching version references embedded in the per-release prose.
  const tableRowRe =
    /^\|\s*\*\*(\d+\.\d+\.\d+(?:-[\w.]+)?)\*\*\s*\|\s*(\d+)?\s*\|\s*([\d-]+)?\s*\|\s*([^|]*?)\s*\|\s*([^|]*?)\s*\|\s*$/;

  let seenTable = false;
  for (const line of raw.split(/\r?\n/)) {
    const match = tableRowRe.exec(line);
    if (match) {
      seenTable = true;
      rows.push({
        version: match[1],
        versionCode: match[2] ? Number(match[2]) : undefined,
        date: match[3] || undefined,
        track: match[4]?.trim() || undefined,
        status: match[5]?.trim() || undefined,
      });
      continue;
    }
    // Once we've seen the table, stop as soon as we leave it (blank line or
    // non-pipe line).
    if (seenTable && !line.trim().startsWith('|')) break;
  }

  cached = rows;
  return cached;
}

/**
 * Latest release that shipped to Production (status contains "Published").
 * Falls back to the newest row in the table if nothing is marked Published.
 */
export function currentPublishedRelease(): ReleaseSummary | undefined {
  const rows = loadReleases();
  return (
    rows.find(
      (r) =>
        (r.status?.toLowerCase().includes('published') ?? false) &&
        (r.track?.toLowerCase().includes('production') ?? false)
    ) ?? rows[0]
  );
}

/** Convenience — just the version string. */
export function currentVersion(fallback = '0.0.0'): string {
  return currentPublishedRelease()?.version ?? fallback;
}
