#!/usr/bin/env python3
"""Check P0 provenance and traceability; this does not test the application."""

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
    manifest = json.loads(read("docs/handoff/MANIFEST.json"))
    documents = manifest["documents"]
    require(len(documents) == 8, "Expected eight original handoff documents")
    paths = [entry["repository_path"] for entry in documents]
    require(len(set(paths)) == len(paths), "Duplicate document path")
    members = [entry["archive_member"] for entry in documents]
    require(len(set(members)) == len(members), "Duplicate archive member")
    for entry in documents:
        path = (ROOT / entry["repository_path"]).resolve()
        require(path.is_relative_to(ROOT), "Manifest path outside repository")
        data = path.read_bytes()
        require(len(data) == entry["size_bytes"], f"Size mismatch: {path.name}")
        require(
            hashlib.sha256(data).hexdigest() == entry["sha256"],
            f"Source document changed: {path.name}",
        )
    print("PASS: eight source documents match recorded sizes and SHA-256")

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
    require(len(source_rows) == 82, "Unexpected requirement baseline")
    require(len({row[0] for row in source_rows}) == 82, "Duplicate requirement ID")
    require(
        sum(priority == "MUST" for _, priority in source_rows) == 70,
        "Unexpected MUST count",
    )
    for line in matrix.splitlines():
        if re.match(pattern, line):
            require(line.endswith("| INCOMPLETE |"), "P0 cannot claim a product requirement DONE")
    print("PASS: all 66 FR and 16 NFR mapped, priorities intact, 70 MUST")

    screens = re.findall(r"^\| (S-\d+) \|", spec, re.M)
    require(screens == re.findall(r"^\| (S-\d+) \|", matrix, re.M), "Screen coverage differs")
    require(len(screens) == 22, "Expected 22 screens")
    scenarios = re.findall(r"^\d+\. \*\*(A\d+) —", spec, re.M)
    qa = read("docs/QA_TEST_PLAN.md")
    require(scenarios == re.findall(r"^\| (A\d+) \|", qa, re.M), "E2E coverage differs")
    require(len(scenarios) == 12, "Expected 12 E2E scenarios")
    checks = re.findall(
        r"^- \[ \] ([A-G]-\d+):",
        read("docs/checklists/ACCEPTANCE_CHECKLIST.md"),
        re.M,
    )
    require(len(checks) == len(set(checks)) == 52, "Unexpected checklist baseline")
    print("PASS: 22 screens, 12 planned E2E scenarios, 52 original acceptance checks")

    baseline_paths = set(git("ls-tree", "-r", "--name-only", baseline).decode().splitlines())
    require(baseline_paths == {"AGENTS.md", "README.md"}, "Baseline inventory differs")
    branch = git("branch", "--show-current").decode().strip()
    require(branch and branch not in {"main", "master"}, "Expected a working branch")
    require(
        subprocess.run(["git", "merge-base", "--is-ancestor", baseline, "HEAD"], cwd=ROOT).returncode == 0,
        "Working branch does not descend from recorded main",
    )
    print("PASS: baseline contains no legacy files; working branch descends from baseline")
    print("P0 verification passed. Android/backend builds and product tests: NOT RUN.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
