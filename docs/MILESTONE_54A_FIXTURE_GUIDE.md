# Milestone 54A backup fixtures

Milestone 54A adds archive-facing compatibility and corruption coverage without changing the
production export schema or the portable-backup contract. The owner-run device cases deferred from
Milestone 53 need two selectable files: one valid WorqOrder backup and one intentionally malformed
ZIP.

## Create the fixtures

1. Build/install the current debug app using the owner-run commands supplied for Milestone 54A.
2. Open **Settings → Backup & Restore → Create Backup** and save the file somewhere easy to locate,
   such as the Desktop. This is the valid fixture and is produced by WorqOrder itself, so it has a
   current manifest/data checksum and the exact two-entry contract.
3. From the repository root, run:

   ```powershell
   .\tools\Generate-Milestone54BackupFixtures.ps1 `
       -ValidBackupPath 'C:\Users\<you>\Desktop\WorqOrder_Backup_YYYY-MM-DD_HHmmss.zip'
   ```

   The script writes `m54-fixtures\WorqOrder_M54_valid.zip` and
   `m54-fixtures\WorqOrder_M54_malformed_truncated.zip`. It never edits the source backup.
4. Keep both files available to the emulator's document picker. The valid file is a real export;
   the malformed file is a truncated ZIP and must be rejected before any data mutation.

The fixture directory is local test material and should not be committed as an application backup.
If it is copied to a device, remove it after the owner-run import/restore exercises.

## Expected device outcomes

- Selecting the malformed fixture in **Import Backup** shows the invalid-backup error, leaves all
  current data unchanged, and does not enable Restore.
- Selecting the valid fixture reaches the replacement confirmation. Cancel leaves data and the
  rolling restore point unchanged; Continue performs the validated replacement and requires Google
  reauthorization before Google export.
- After a successful replacement, **Restore** becomes available. Confirming it swaps the previous
  state back and makes the displaced state the next restore point.

The malformed-import, valid-import, and Restore-swap exercises are owner-run Milestone 54 evidence;
the unit matrix in `PortableBackupMilestone54FixtureMatrixTest` covers the same archive rejection and
logical round-trip boundaries without touching the device database.
