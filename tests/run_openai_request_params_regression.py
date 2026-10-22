#!/usr/bin/env python3
"""Exercise production OpenAI-compatible request parameters and unsupported-parameter retries.

Synthetic settings only. Requests go to an in-process loopback HTTP server; no provider,
account, API key, Android SDK or device is used.
"""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, classpath, java_tool, json_jar

work = REPORTS / "openai-request-params"
work.mkdir(parents=True, exist_ok=True)
repository = (SHARED / "AiLyricsRepository.java").read_text()


def section(start, end):
    begin = repository.index(start)
    return repository[begin:repository.index(end, begin)]


def unique(start):
    if repository.count(start) != 1:
        raise ValueError(f"Expected one source match: {start.strip()}")
    return repository.index(start)


def statement(start):
    begin = unique(start)
    return repository[begin:repository.index(";\n", begin) + 2]


def declaration(signature):
    begin = unique(signature)
    cursor = repository.index("{", begin) + 1
    depth = 1
    while depth:
        depth += (repository[cursor] == "{") - (repository[cursor] == "}")
        cursor += 1
    return "    " + repository[begin:cursor] + "\n"


methods = "\n".join([
    section("    private static final Pattern OPENAI_REASONING_MODEL_PATTERN", "    private final ExecutorService executor"),
    statement("    private static final int MAX_OPENAI_PARAMETER_ADJUSTMENT_ENTRIES"),
    statement("    private final Map<String, Integer> openAiParameterAdjustmentCache"),
    section("    void testConnection(", "    interface Callback {"),
    section("    private interface TextDeltaSink {", "    private interface StreamRowSink {"),
    section("    private interface SseDataHandler {", "    private static final class SupplementRequest {"),
    section("    private String callProviderRawOnce(", "    private String callGeminiStream("),
    section("    private JSONObject claudeBody(", "    private Map<String, String> claudeHeaders("),
    section("    private String callOpenAiCompatibleStream(", "    private JSONObject claudeWebSearchTool("),
    section("    private void applyOpenAiResearchOptions(", "    private String fetchPaxsenixWebResearch("),
    section("    private JSONObject openAiCompatibleBody(", "    private int resolveResearchMaxTokens("),
    section("    private String postJson(", "    private String buildTranslationPrompt("),
    section("    private static final class HttpStatusException", "    private static final class ResearchWebSearchException"),
    *(declaration(signature) for signature in (
        "private static String firstNonEmpty(String... values)",
        "private static String firstNonNull(String value)",
        "private List<String> parseApiKeys(String raw)",
        "private List<String> providerApiKeys(AiLyricsSettings.Snapshot settings)",
        "private String extractOpenAiContent(Object content)",
        "private String trimRight(String value, String suffix)",
    )),
])
source = work / "OpenAIRequestParamsRegression.java"
source.write_text((ROOT / "tests/OpenAIRequestParamsRegression.java.in").read_text().replace("    // PRODUCTION_METHODS", methods))
dependencies = classpath(work, json_jar())
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work), str(source), str(SHARED / "PaxsenixAiModels.java")], check=True)
subprocess.run([java_tool("java"), "-cp", dependencies, "kr.ivlis.ivlyricsandroid.OpenAIRequestParamsRegression"], check=True)
