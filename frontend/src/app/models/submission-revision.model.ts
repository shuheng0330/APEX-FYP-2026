export interface RevisionState { revisionRequired?: boolean; }

// Persisted backend state is authoritative; forms that save on submit may include unsaved content changes.
export function canResubmit(record: RevisionState | null | undefined, hasContentChanges = false): boolean {
  return record?.revisionRequired !== true || hasContentChanges;
}
