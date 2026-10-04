# Validation-only workspace

Purpose: compile and test proposed sources in an isolated audit workspace.
This is not an installer and must not write to the active src/, platform/, dist/ or server folders.
The source-application script was denied and remains absent. Do not work around that denial.
Only copies under this audit directory may be changed by validation steps.
No production startup, plugin deployment, source replacement, commit or release tag is authorized here.

Original source hashes and logs must be recorded before creating a test copy.
A passing copied-workspace build does not mean the active project was updated.
