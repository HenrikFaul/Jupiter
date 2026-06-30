Sift — Portable Duplicate & Index Intelligence
==============================================

HOW TO USE
----------
1. Keep "Sift.exe" and the "Sift-Data" folder together in the same folder.
2. Double-click Sift.exe. That's it — no install, no admin needed for normal use.
3. To move it to another PC: copy the WHOLE folder (Sift.exe + Sift-Data).
   Your entire index — including the history of drives that aren't even plugged
   in — travels with you, because it lives inside Sift-Data\index.sqlite.

WHERE YOUR DATA LIVES
---------------------
Everything Sift remembers is in the "Sift-Data" folder next to the EXE:
  Sift-Data\index.sqlite   ← the persistent index (your knowledge)
  Sift-Data\quarantine\    ← files moved aside (quarantine deletions)
  Sift-Data\exports\       ← CSV reports you export
Delete the Sift-Data folder to start completely fresh.

REQUIREMENTS ON THE TARGET PC
-----------------------------
* Windows 10 (21H2+) or Windows 11 — WebView2 runtime is already installed on these.
  On very old/clean Windows 10, install "Microsoft Edge WebView2 Runtime" (free) once.
* For the FAST drive-scan mode (NTFS MFT acceleration), run Sift.exe "as Administrator".
  Without admin it still works — it just uses the slower (but complete) folder walk.

SAFETY
------
* Sift never deletes anything on its own. You pick what to remove, preview it, and the
  engine validates it before anything happens.
* Default deletions go to the Recycle Bin (reversible). "Permanent" needs a 2nd confirm.
* Sift refuses to remove the last remaining copy of a file.
* Every removal is written to an append-only audit log (see Reports & Audit).
