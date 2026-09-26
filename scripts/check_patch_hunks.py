#!/usr/bin/env python3
"""Validate that every unified-diff hunk header matches its actual body size.

A hunk header looks like:

    @@ -oldStart,oldCount +newStart,newCount @@ optional context

If oldCount/newCount disagree with the number of context/removed/added lines that
follow, `git am` reports:

    error: corrupt patch at .git/rebase-apply/patch:<line>
    error: could not build fake ancestor

which is exactly what silently happens when someone edits a patch body by hand
(adds or removes a line) without updating the header.

Usage:
    python scripts/check_patch_hunks.py <dir-or-file> [<dir-or-file> ...]

With no arguments the minecraft/paper patch directories plus the paperweight
`build.gradle.kts.patch` are checked.

Two header dialects are accepted:

    @@ -oldStart,oldCount +newStart,newCount @@   standard unified diff
    @@ -oldStart,oldCount +_,newCount @@          paperweight: "_" means
                                                  "same as the other side"

`_` matters here: paperweight writes build.gradle.kts.patch with `+_,count`
headers, which plain `git apply` rejects. Validating the counts still catches the
real corruption, so the script understands both dialects.

Exit code 0 = all clean, 1 = at least one mismatch.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# The start line number may be "_" (paperweight's "same as the other side" placeholder).
HUNK_RE = re.compile(r"^@@ -(\d+|_)(?:,(\d+))? \+(\d+|_)(?:,(\d+))? @@")

# Markers that terminate a hunk body. Anything else belongs to the body, including
# lines that merely *look* like file headers ("--- ", "+++ ") — those can legitimately
# appear inside a body as removed/added content.
HUNK_TERMINATORS = (
    "diff --git ",
    "-- ",  # git format-patch trailer (signature separator)
)

NO_NEWLINE_MARKER = "\\ No newline at end of file"


def count_of(header_count: str | None) -> int:
    """A missing count in the header means exactly 1 line."""
    return 1 if header_count is None else int(header_count)


def check_file(path: Path) -> list[str]:
    try:
        raw_bytes = path.read_bytes()
    except OSError as exc:
        return [f"{path}: cannot read ({exc})"]

    problems: list[str] = []

    # git's patch parser requires the final line to be newline-terminated; a missing
    # trailing newline surfaces as "corrupt patch at <last line>".
    if raw_bytes and not raw_bytes.endswith(b"\n"):
        problems.append(
            f"{path}: file is not newline-terminated "
            f"(git will report 'corrupt patch' at the last line)"
        )

    raw = raw_bytes.decode("utf-8", errors="replace")
    lines = [l.rstrip("\r") for l in raw.split("\n")]
    i = 0
    while i < len(lines):
        m = HUNK_RE.match(lines[i])
        if not m:
            i += 1
            continue

        header_line_no = i + 1
        old_expected = count_of(m.group(2))
        new_expected = count_of(m.group(4))

        old_actual = 0
        new_actual = 0
        j = i + 1
        while j < len(lines):
            line = lines[j]
            if HUNK_RE.match(line):
                break
            if any(line.startswith(t) for t in HUNK_TERMINATORS):
                break
            if line == NO_NEWLINE_MARKER:
                # Annotation on the preceding line, not body content.
                j += 1
                continue

            if line.startswith("+"):
                new_actual += 1
            elif line.startswith("-"):
                old_actual += 1
            elif line.startswith(" "):
                old_actual += 1
                new_actual += 1
            elif line == "":
                if j == len(lines) - 1:
                    # Trailing newline artifact at EOF, not a context line.
                    break
                # Some tooling strips the single space of an empty context line.
                old_actual += 1
                new_actual += 1
            else:
                problems.append(
                    f"{path}:{j + 1}: unexpected line inside hunk: {line[:60]!r}"
                )
                break
            j += 1

        if old_actual != old_expected or new_actual != new_expected:
            # Rebuild the header with real line numbers, substituting the other side for "_".
            old_start = m.group(1)
            new_start = m.group(3)
            if old_start == "_" and new_start != "_":
                old_start = new_start
            elif new_start == "_" and old_start != "_":
                new_start = old_start
            problems.append(
                f"{path}:{header_line_no}: hunk header says "
                f"-{old_expected} +{new_expected} but body has "
                f"-{old_actual} +{new_actual}  ->  should be "
                f"@@ -{old_start},{old_actual} +{new_start},{new_actual} @@"
            )
        i = j

    return problems


def iter_patch_files(targets: list[str]) -> list[Path]:
    found: list[Path] = []
    for target in targets:
        p = Path(target)
        if p.is_dir():
            found.extend(sorted(p.rglob("*.patch")))
        elif p.is_file():
            found.append(p)
        else:
            print(f"warning: {target} does not exist", file=sys.stderr)
    return found


def main(argv: list[str]) -> int:
    targets = argv[1:] or [
        "mili-server/minecraft-patches",
        "mili-server/paper-patches",
        "mili-server/build.gradle.kts.patch",
    ]
    files = iter_patch_files(targets)
    if not files:
        print("no patch files found")
        return 0

    all_problems: list[str] = []
    for f in files:
        all_problems.extend(check_file(f))

    if all_problems:
        print(f"FOUND {len(all_problems)} problem(s) in {len(files)} patch file(s):\n")
        for problem in all_problems:
            print("  " + problem)
        return 1

    print(f"OK: {len(files)} patch file(s) checked, all hunk headers are consistent")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
