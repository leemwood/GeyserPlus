"""Publish the four platform jars from a verified GitHub release to Modrinth.

Uses MODRINTH_TOKEN from the action environment; never stores credentials.
Existing versions are reused only when their artifact hashes match exactly.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request
import uuid


PROJECT = "MMHzAVoW"
GEYSER = "wKkoqHrH"
PLATFORMS = (
    ("Spigot / Paper / Purpur", "GeyserPlus-Spigot.jar", ["paper", "purpur", "spigot"]),
    ("Velocity", "GeyserPlus-Velocity.jar", ["velocity"]),
    ("BungeeCord / Waterfall", "GeyserPlus-BungeeCord.jar", ["bungeecord", "waterfall"]),
    ("Geyser Extension", "GeyserPlus-Extension.jar", ["geyser"]),
)


def request(path, token=None, data=None, content_type=None):
    headers = {"User-Agent": "leemwood/GeyserPlus-release (github.com/leemwood/GeyserPlus)"}
    if token:
        headers["Authorization"] = token
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request("https://api.modrinth.com/v2/" + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=90) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Avoid logging request headers or credential-bearing request objects.
        raise RuntimeError(f"Modrinth {path} returned HTTP {error.code}") from None


def multipart(metadata, name, artifact):
    boundary = "GeyserPlus-" + uuid.uuid4().hex
    prefix = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\n"
              "Content-Type: application/json\r\n\r\n" + json.dumps(metadata, ensure_ascii=False) + "\r\n"
              f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
              "Content-Type: application/java-archive\r\n\r\n").encode("utf-8")
    return prefix + artifact + f"\r\n--{boundary}--\r\n".encode(), "multipart/form-data; boundary=" + boundary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        parser.error("Expected a stable release tag such as v2.2.0")
    version = args.tag[1:]
    configured = re.search(r"(?m)^version\s*=\s*(\S+)", Path("gradle.properties").read_text())[1]
    if version != configured:
        parser.error("Tag does not match the checked-out source version")
    changelog = Path("CHANGELOG.md").read_text(encoding="utf-8")
    heading = re.search(r"(?m)^## " + re.escape(version) + r"\s+[^\n]*\n", changelog)
    if not heading:
        parser.error("Release notes missing from CHANGELOG.md")
    changes = re.split(r"(?m)^## ", changelog[heading.end():], maxsplit=1)[0].strip()
    prepared = []
    checksums = {}
    for line in (args.directory / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        checksums[name.lstrip(" *")] = digest
    for label, name, loaders in PLATFORMS:
        artifact = (args.directory / name).read_bytes()
        if hashlib.sha256(artifact).hexdigest() != checksums.get(name):
            raise RuntimeError("Artifact checksum mismatch: " + name)
        metadata = {
            "name": f"GeyserPlus {version} — {label}", "version_number": version,
            "changelog": changes,
            "dependencies": [] if loaders == ["geyser"] else [{"project_id": GEYSER, "dependency_type": "required"}],
            "game_versions": ["26.3"], "version_type": "release", "loaders": loaders,
            "featured": True, "project_id": PROJECT, "file_parts": ["file"], "primary_file": "file",
        }
        prepared.append((name, artifact, metadata))
    if args.dry_run:
        for name, artifact, metadata in prepared:
            print(json.dumps({"file": name, "size": len(artifact), "metadata": metadata}, ensure_ascii=False))
        return
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        raise RuntimeError("GitHub repository secret MODRINTH_TOKEN is missing")
    request("user", token)
    published = request(f"project/{PROJECT}/version", token)
    links = []
    for name, artifact, metadata in prepared:
        existing = [entry for entry in published if entry["version_number"] == version
                    and set(entry["loaders"]) == set(metadata["loaders"])]
        sha512 = hashlib.sha512(artifact).hexdigest()
        if existing:
            if len(existing) != 1 or not any(file["filename"] == name and file["hashes"]["sha512"] == sha512
                                            for file in existing[0]["files"]):
                raise RuntimeError("An existing Modrinth version has different content: " + name)
            result = existing[0]
        else:
            body, content_type = multipart(metadata, name, artifact)
            result = request("version", token, body, content_type)
        if not any(file["filename"] == name and file["hashes"]["sha512"] == sha512 for file in result["files"]):
            raise RuntimeError("Published artifact hash verification failed: " + name)
        link = "https://modrinth.com/plugin/geyserplus/version/" + result["id"]
        links.append(f"- {name}: {link}")
        print(link, flush=True)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"## GeyserPlus {version}\n\n" + "\n".join(links) + "\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Publish failed: {error}", file=sys.stderr)
        raise SystemExit(1)
