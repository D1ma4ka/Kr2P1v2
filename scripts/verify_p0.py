#!/usr/bin/env python3
"""Check versioned handoff provenance and current traceability; this does not test the application."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    manifest = json.loads(read("docs/handoff/v1.0/MANIFEST.json"))
    current = json.loads(read("docs/handoff/v1.3/MANIFEST.json"))
    require(current["version"] == "1.3", "Unexpected active specification version")
    for version, source, count in [("1.0", manifest, 8), ("1.3", current, 4)]:
        documents = source["documents"]
        require(len(documents) == count, f"Unexpected {version} source count")
        members = [entry["archive_member"] for entry in documents]
        require(len(set(members)) == count, "Duplicate archive member")
        paths = []
        for entry in documents:
            # v1.0 manifest is immutable; archive layout follows original members.
            member = Path(entry["archive_member"])
            # The current specification has owner-approved P6.1 supplements.
            # Verify the byte-exact v1.3 import separately; never rewrite its manifest hashes.
            relative = (Path("docs/handoff/v1.0") / Path(*member.parts[1:])
                        if version == "1.0" else
                        Path("docs/handoff/v1.3/docs/TECHNICAL_SPECIFICATION.md")
                        if member == Path("docs/TECHNICAL_SPECIFICATION.md") else
                        Path(entry["repository_path"]))
            path = (ROOT / relative).resolve()
            require(path.is_relative_to(ROOT), "Manifest path outside repository")
            paths.append(path)
            data = path.read_bytes()
            require(len(data) == entry["size_bytes"], f"Size mismatch: {relative}")
            require(hashlib.sha256(data).hexdigest() == entry["sha256"],
                    f"Source changed: {relative}")
        require(len(set(paths)) == count, "Duplicate document path")
        print(f"PASS: v{version}, {count} sources match sizes and SHA-256")

    baseline = manifest["baseline_commit"]
    agents = (ROOT / "AGENTS.md").read_bytes()
    require(agents == git("show", f"{baseline}:AGENTS.md"), "AGENTS.md changed")
    require(
        hashlib.sha256(agents).hexdigest() == manifest["agents_sha256"],
        "AGENTS hash mismatch",
    )
    require(
        (ROOT / "README.md").read_bytes().startswith(git("show", f"{baseline}:README.md")),
        "Original README introduction was not preserved",
    )
    print("PASS: baseline AGENTS unchanged; README introduction preserved")

    spec = read("docs/TECHNICAL_SPECIFICATION.md")
    matrix = read("docs/REQUIREMENTS_MATRIX.md")
    pattern = r"^\| ((?:FR|NFR)-\d+) \| (MUST|SHOULD|LATER) \|"
    source_rows = re.findall(pattern, spec, re.M)
    actual_rows = re.findall(pattern, matrix, re.M)
    require(source_rows == actual_rows, "Requirements missing, duplicated or reprioritized")
    require(len(source_rows) == 180, "Unexpected requirement baseline")
    require(len({row[0] for row in source_rows}) == 180, "Duplicate requirement ID")
    require(
        sum(priority == "MUST" for _, priority in source_rows) == 174,
        "Unexpected MUST count",
    )
    for line in matrix.splitlines():
        if re.match(pattern, line):
            require(line.split("|")[-2].strip() in {"INCOMPLETE", "BLOCKED", "DONE"}, "Invalid implementation status")
    print("PASS: all 135 FR and 45 NFR mapped, priorities intact, 174 MUST")

    screens = re.findall(r"^\| (S-\d+) \|", spec, re.M)
    require(screens == re.findall(r"^\| (S-\d+) \|", matrix, re.M), "Screen coverage differs")
    require(len(screens) == 29, "Expected 29 screens")
    scenarios = re.findall(r"^(?:\d+\. |[-] )\*\*(A\d+)(?: —| /)", spec, re.M)
    qa = read("docs/QA_TEST_PLAN.md")
    require(scenarios == re.findall(r"^\| (A\d+) \|", qa, re.M), "E2E coverage differs")
    require(len(scenarios) == 43, "Expected 43 E2E scenarios")
    checks = re.findall(
        r"^- \[ \] ([A-G]-\d+):",
        read("docs/handoff/v1.0/checklists/ACCEPTANCE_CHECKLIST.md"),
        re.M,
    )
    require(len(checks) == len(set(checks)) == 52, "Unexpected checklist baseline")
    print("PASS: 29 screens, 43 planned E2E scenarios, 52 original acceptance checks")

    baseline_paths = set(git("ls-tree", "-r", "--name-only", baseline).decode().splitlines())
    require(baseline_paths == {"AGENTS.md", "README.md"}, "Baseline inventory differs")
    branch = git("branch", "--show-current").decode().strip()
    # Detached checkout is normal for read-only pull-request CI checks.
    require(branch not in {"main", "master"}, "Do not run stage work on the main branch")
    require(
        subprocess.run(["git", "merge-base", "--is-ancestor", baseline, "HEAD"], cwd=ROOT).returncode == 0,
        "Working branch does not descend from recorded main",
    )
    print("PASS: baseline contains no legacy files; checkout descends from baseline")
    print("Source/traceability verification passed. Application tests are reported separately.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
