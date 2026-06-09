#!/usr/bin/env python3
import hashlib
import json
import os
import re
import subprocess
import sys
import textwrap
import urllib.error
import urllib.request
from pathlib import Path


def run_git(args, allow_fail=False):
    result = subprocess.run(
        ["git", *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if result.returncode != 0 and not allow_fail:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout.strip()


def version_key(tag):
    value = tag[1:] if tag.startswith("v") else tag
    parts = []
    for chunk in re.split(r"[^0-9A-Za-z]+", value):
        if not chunk:
            continue
        if chunk.isdigit():
            parts.append((0, int(chunk)))
        else:
            parts.append((1, chunk.lower()))
    return parts


def previous_tag(current_tag):
    tags = [
        tag
        for tag in run_git(["tag", "--list", "v*"]).splitlines()
        if tag and tag != current_tag
    ]
    older = []
    current_key = version_key(current_tag)
    for tag in tags:
        key = version_key(tag)
        if key < current_key:
            older.append(tag)
    if older:
        return sorted(older, key=version_key)[-1]
    return ""


def commit_range(previous, current):
    if previous:
        return f"{previous}..{current}"
    return current


def resolve_commit(ref):
    output = run_git(["rev-parse", "--verify", f"{ref}^{{commit}}"], allow_fail=True)
    if output:
        return output.splitlines()[0]
    return run_git(["rev-parse", "HEAD"])


def resolve_range_ref(ref):
    if run_git(["rev-parse", "--verify", f"{ref}^{{commit}}"], allow_fail=True):
        return ref
    return "HEAD"


def git_log(range_spec):
    output = run_git([
        "log",
        "--no-merges",
        "--pretty=format:%h%x09%s",
        range_spec,
    ], allow_fail=True)
    return output


def git_diff_stat(range_spec):
    return run_git(["diff", "--stat", range_spec], allow_fail=True)


def read_gradle_version():
    gradle = Path("app/build.gradle")
    text = gradle.read_text(encoding="utf-8") if gradle.exists() else ""
    code_match = re.search(r"versionCode\s+([0-9]+)", text)
    name_match = re.search(r'versionName\s+"([^"]+)"', text)
    return {
        "versionCode": int(code_match.group(1)) if code_match else None,
        "versionName": name_match.group(1) if name_match else "",
    }


def apk_assets(apk_dir):
    assets = []
    for path in sorted(Path(apk_dir).glob("*.apk")):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assets.append({
            "name": path.name,
            "path": str(path),
            "size": path.stat().st_size,
            "sha256": digest,
        })
    return assets


def fallback_notes(current_tag, previous, log_text, assets):
    compare = f"{previous}...{current_tag}" if previous else current_tag
    lines = [
        f"## ivLyrics Android {current_tag}",
        "",
    ]
    if previous:
        lines.append(f"Changes since `{previous}`.")
    else:
        lines.append("Initial tagged release.")
    lines.extend(["", "### Changes"])
    commits = [line for line in log_text.splitlines() if line.strip()]
    if commits:
        lines.extend(f"- {line.split(chr(9), 1)[-1]}" for line in commits)
    else:
        lines.append("- Build and release APK assets.")
    lines.extend(["", "### APK files"])
    for asset in assets:
        lines.append(f"- `{asset['name']}` ({asset['size']} bytes, SHA-256 `{asset['sha256']}`)")
    lines.extend(["", f"**Full Changelog**: https://github.com/ivLis-Studio/ivLyrics-Android/compare/{compare}"])
    return "\n".join(lines) + "\n"


def normalize_chat_url(base_url):
    base = (base_url or "").strip().rstrip("/")
    if not base:
        return ""
    if base.endswith("/chat/completions"):
        return base
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def ai_release_notes(current_tag, previous, version, log_text, stat_text, assets):
    api_key = os.environ.get("AI_API_KEY", "").strip()
    api_url = normalize_chat_url(os.environ.get("AI_BASE_URL", ""))
    model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    if not api_key or not api_url:
        return ""

    asset_text = "\n".join(
        f"- {asset['name']} ({asset['size']} bytes, sha256={asset['sha256']})"
        for asset in assets
    )
    compare_url = (
        f"https://github.com/ivLis-Studio/ivLyrics-Android/compare/{previous}...{current_tag}"
        if previous
        else f"https://github.com/ivLis-Studio/ivLyrics-Android/commits/{current_tag}"
    )
    prompt = textwrap.dedent(f"""
        You write GitHub release notes for an Android music lyrics app named ivLyrics Android.
        Write in Korean. Be concise, concrete, and user-facing.

        Current tag: {current_tag}
        Previous tag: {previous or "(none)"}
        Compare URL: {compare_url}
        Android versionName: {version.get("versionName") or "(unknown)"}
        Android versionCode: {version.get("versionCode") or "(unknown)"}

        Requirements:
        - Compare this release against the previous tag.
        - Group notes into short sections.
        - Mention APK assets and clarify that release APK is unsigned if its filename says unsigned.
        - Do not invent changes not supported by the commit list.
        - Do not mention secrets, private URLs, or internal token endpoints.
        - End with a Full Changelog link.

        Commits:
        {log_text or "(no commit log)"}

        Diff stat:
        {stat_text or "(no diff stat)"}

        APK assets:
        {asset_text or "(no APK assets)"}
    """).strip()
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "You generate accurate, concise GitHub release notes from git metadata only.",
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.25,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"AI release note generation failed: {exc}", file=sys.stderr)
        return ""
    choices = data.get("choices") or []
    if not choices:
        return ""
    message = choices[0].get("message") or {}
    return (message.get("content") or "").strip()


def main():
    current_tag = os.environ.get("RELEASE_TAG", "").strip() or run_git(["describe", "--tags", "--exact-match"], allow_fail=True)
    if not current_tag:
        current_tag = run_git(["rev-parse", "--short", "HEAD"])
    current_sha = resolve_commit(current_tag)
    previous = previous_tag(current_tag)
    range_spec = commit_range(previous, resolve_range_ref(current_tag))
    log_text = git_log(range_spec)
    stat_text = git_diff_stat(range_spec)
    version = read_gradle_version()
    assets = apk_assets(os.environ.get("APK_DIR", "release-apks"))

    notes = ai_release_notes(current_tag, previous, version, log_text, stat_text, assets)
    if not notes:
        notes = fallback_notes(current_tag, previous, log_text, assets)
    if "**Full Changelog**" not in notes:
        compare = f"{previous}...{current_tag}" if previous else current_tag
        notes += f"\n\n**Full Changelog**: https://github.com/ivLis-Studio/ivLyrics-Android/compare/{compare}\n"

    metadata = {
        "tag": current_tag,
        "commit": current_sha,
        "previousTag": previous,
        "versionName": version.get("versionName"),
        "versionCode": version.get("versionCode"),
        "compareUrl": (
            f"https://github.com/ivLis-Studio/ivLyrics-Android/compare/{previous}...{current_tag}"
            if previous
            else f"https://github.com/ivLis-Studio/ivLyrics-Android/commits/{current_tag}"
        ),
        "apks": assets,
    }

    out_dir = Path(os.environ.get("RELEASE_METADATA_DIR", "release-metadata"))
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "release-notes.md").write_text(notes.strip() + "\n", encoding="utf-8")
    (out_dir / f"ivLyrics-Android-{current_tag}-version.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"previous_tag={previous}")
    print(f"notes={out_dir / 'release-notes.md'}")
    print(f"version_file={out_dir / f'ivLyrics-Android-{current_tag}-version.json'}")


if __name__ == "__main__":
    main()
