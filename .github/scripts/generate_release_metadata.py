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


TEMPLATE_PATH = Path(".github/release-notes-template.md")


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


def compare_url(current_tag, previous):
    if previous:
        return f"https://github.com/ivLis-Studio/ivLyrics-Android/compare/{previous}...{current_tag}"
    return f"https://github.com/ivLis-Studio/ivLyrics-Android/commits/{current_tag}"


def commit_subjects(log_text):
    commits = [line for line in log_text.splitlines() if line.strip()]
    return [line.split("\t", 1)[-1] for line in commits] or ["Build and release APK assets."]


def markdown_bullets(values):
    items = [str(value).strip() for value in values if str(value).strip()]
    if not items:
        items = ["No notable changes."]
    return "\n".join(f"- {item}" for item in items)


def asset_downloads(assets, lang):
    if not assets:
        return ["APK assets were not found." if lang == "en" else "APK 파일을 찾지 못했습니다."]
    lines = []
    for asset in assets:
        name = asset["name"]
        if lang == "ko":
            note = "서명되지 않은 릴리즈 APK입니다." if "unsigned" in name else "설치 테스트용 디버그 APK입니다."
            lines.append(f"`{name}`: {note}")
        else:
            note = "Unsigned release APK." if "unsigned" in name else "Debug APK for install testing."
            lines.append(f"`{name}`: {note}")
    return lines


def checksum_lines(assets):
    if not assets:
        return ["No APK assets."]
    return [
        f"`{asset['name']}`: `{asset['sha256']}`"
        for asset in assets
    ]


def fallback_content(current_tag, previous, log_text, assets):
    subjects = commit_subjects(log_text)
    highlights = subjects[:6]
    fixes = subjects[6:] or ["APK build and release metadata generation."]
    return {
        "ko": {
            "summary": f"{current_tag} 릴리즈입니다. 이전 버전 대비 변경 사항과 APK 파일을 함께 제공합니다.",
            "highlights": highlights,
            "fixes": fixes,
        },
        "en": {
            "summary": f"{current_tag} release with APK assets and change notes compared with the previous version.",
            "highlights": highlights,
            "fixes": fixes,
        },
    }


def load_template():
    if TEMPLATE_PATH.exists():
        return TEMPLATE_PATH.read_text(encoding="utf-8")
    return textwrap.dedent("""
        # ivLyrics Android {tag}

        ## 한국어
        {ko_summary}

        {ko_highlights}

        ## English
        {en_summary}

        {en_highlights}

        **Full Changelog**: {compare_url}
    """).strip() + "\n"


def render_notes(current_tag, previous, version, assets, content):
    ko = content.get("ko") or {}
    en = content.get("en") or {}
    return load_template().format(
        tag=current_tag,
        version_name=version.get("versionName") or "unknown",
        version_code=version.get("versionCode") or "unknown",
        previous_tag=previous or "None",
        compare_url=compare_url(current_tag, previous),
        ko_summary=ko.get("summary") or "릴리즈 노트가 생성되었습니다.",
        ko_highlights=markdown_bullets(ko.get("highlights") or []),
        ko_fixes=markdown_bullets(ko.get("fixes") or []),
        ko_downloads=markdown_bullets(asset_downloads(assets, "ko")),
        en_summary=en.get("summary") or "Release notes were generated.",
        en_highlights=markdown_bullets(en.get("highlights") or []),
        en_fixes=markdown_bullets(en.get("fixes") or []),
        en_downloads=markdown_bullets(asset_downloads(assets, "en")),
        checksums=markdown_bullets(checksum_lines(assets)),
    )


def normalize_chat_url(base_url):
    base = (base_url or "").strip().rstrip("/")
    if not base:
        return ""
    if base.endswith("/chat/completions"):
        return base
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def parse_ai_json(text):
    value = (text or "").strip()
    value = re.sub(r"^```(?:json)?\s*", "", value, flags=re.IGNORECASE)
    value = re.sub(r"\s*```$", "", value)
    try:
        data = json.loads(value)
    except json.JSONDecodeError:
        return {}
    if not isinstance(data, dict):
        return {}
    ko = data.get("ko") if isinstance(data.get("ko"), dict) else {}
    en = data.get("en") if isinstance(data.get("en"), dict) else {}
    if not ko or not en:
        return {}
    return {"ko": normalize_note_section(ko), "en": normalize_note_section(en)}


def normalize_note_section(section):
    def text_value(key):
        return str(section.get(key) or "").strip()

    def list_value(key):
        value = section.get(key)
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str) and value.strip():
            return [value.strip()]
        return []

    return {
        "summary": text_value("summary"),
        "highlights": list_value("highlights"),
        "fixes": list_value("fixes"),
    }


def ai_release_content(current_tag, previous, version, log_text, stat_text, assets):
    api_key = os.environ.get("AI_API_KEY", "").strip()
    api_url = normalize_chat_url(os.environ.get("AI_BASE_URL", ""))
    model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    if not api_key or not api_url:
        return {}

    asset_text = "\n".join(
        f"- {asset['name']} ({asset['size']} bytes, sha256={asset['sha256']})"
        for asset in assets
    )
    prompt = textwrap.dedent(f"""
        You write bilingual GitHub release note content for an Android music lyrics app named ivLyrics Android.
        Return JSON only. Do not return Markdown.

        Current tag: {current_tag}
        Previous tag: {previous or "(none)"}
        Compare URL: {compare_url(current_tag, previous)}
        Android versionName: {version.get("versionName") or "(unknown)"}
        Android versionCode: {version.get("versionCode") or "(unknown)"}

        Output JSON schema:
        {{
          "ko": {{
            "summary": "Korean one-sentence summary",
            "highlights": ["Korean user-facing highlight", "..."],
            "fixes": ["Korean improvement or fix", "..."]
          }},
          "en": {{
            "summary": "English one-sentence summary",
            "highlights": ["English user-facing highlight", "..."],
            "fixes": ["English improvement or fix", "..."]
          }}
        }}

        Requirements:
        - Write both Korean and English.
        - Keep Korean and English sections semantically equivalent.
        - Compare this release against the previous tag.
        - Keep each bullet short and concrete.
        - Put major user-facing changes in highlights.
        - Put smaller polish, fixes, and maintenance changes in fixes.
        - Mention APK assets and clarify that release APK is unsigned if its filename says unsigned.
        - Do not invent changes not supported by the commit list.
        - Do not mention secrets, private URLs, or internal token endpoints.
        - Do not include a Full Changelog link; the template adds it.

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
    except urllib.error.HTTPError as exc:
        try:
            body = exc.read().decode("utf-8", errors="replace").strip()
        except Exception:
            body = ""
        if len(body) > 1200:
            body = body[:1200] + "...(truncated)"
        detail = f"HTTP {exc.code}: {exc.reason or ''}".strip()
        if body:
            detail += f" / {body}"
        print(f"AI release note generation failed: {detail}", file=sys.stderr)
        return {}
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"AI release note generation failed: {exc}", file=sys.stderr)
        return {}
    choices = data.get("choices") or []
    if not choices:
        return {}
    message = choices[0].get("message") or {}
    return parse_ai_json(message.get("content") or "")


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

    content = ai_release_content(current_tag, previous, version, log_text, stat_text, assets)
    if not content:
        content = fallback_content(current_tag, previous, log_text, assets)
    notes = render_notes(current_tag, previous, version, assets, content)

    metadata = {
        "tag": current_tag,
        "commit": current_sha,
        "previousTag": previous,
        "versionName": version.get("versionName"),
        "versionCode": version.get("versionCode"),
        "compareUrl": (
            compare_url(current_tag, previous)
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
