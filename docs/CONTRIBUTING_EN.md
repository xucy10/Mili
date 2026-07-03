Contributing to Mili
====================

**English** | [中文](./CONTRIBUTING.md)

Thanks for wanting to contribute! This document mirrors the Chinese guide and provides a concise, actionable contribution workflow.

Quick start
1. Fork the repository with your personal account and clone locally:

```bash
git clone https://github.com/<your>/Mili.git
cd Mili
```

2. Apply patch workspace:

```bash
./gradlew applyAllPatches
```

3. Work within the generated `*-api` / `*-server` directories and follow the patch workflow below.

Development environment
- `git`
- `JDK 21` or higher

Windows / long-path notes
Ensure system and Git long-path support are enabled:
- Windows: https://learn.microsoft.com/windows/win32/fileio/maximum-file-path-limitation
- Git for Windows: https://gitforwindows.org/faq.html#i-get-errors-trying-to-check-out-files-with-long-path-names

Patches model overview
-----------------------
Applying `applyAllPatches` creates paired directories in repo root, such as:

- `Mili-api`, `luminol-api`, `folia-api`, `paper-api` — API changes
- `Mili-server`, `luminol-server`, `folia-server`, `paper-server` — server patches

These are not independent git repositories: commits in these folders are represented as patches relative to upstream base.

Adding a new patch
------------------
1. Edit code in the appropriate `*-api` or `*-server` folder.
2. Stage changes: `git add <files>` (do not commit new auto-generated files directly).
3. Commit: `git commit -m "Describe change"`.
4. If you added new files, run: `./gradlew fixupPaperApiFilePatches`.
5. Convert commits to patches: `./gradlew rebuildAllServerPatches`.
6. Push and open a PR including the generated patch files.

Modifying an existing patch
---------------------------
1. Make changes at `HEAD`.
2. Create a fixup commit: `git commit -a --fixup <hash>` (or use `--squash` to edit message).
3. Rebase with autosquash: `git rebase -i --autosquash base`.
4. Run `./gradlew fixupPaperApiFilePatches` (if needed).
5. Run `./gradlew rebuildAllServerPatches`.
6. Push and update the PR.

FAQ
---
- Should I fork with an organization account?
    - No. PRs from organization forks cannot be edited by us easily and complicate the merge process.

- Build failures?
    - Run: `./gradlew assemble --stacktrace` and inspect errors.

- How to run tests?
    - Use `./gradlew test` or other test tasks defined in `build.gradle.kts`.

Need more help?
---------------
See the main README files for build, dependency and community information. When opening an issue, include build logs, JDK version and reproduction steps.


