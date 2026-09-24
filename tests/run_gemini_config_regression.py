#!/usr/bin/env python3
"""Exercise the production Gemini request builder without accounts or an Android device."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, classpath, java_tool, json_jar

work = REPORTS / "gemini-config"
work.mkdir(parents=True, exist_ok=True)
repository = (SHARED / "AiLyricsRepository.java").read_text()
start = repository.index("    private JSONObject geminiBody(")
methods = repository[start:repository.index("    private String callClaudeStream(", start)]
fixture = '''import org.json.*;
public class GeminiConfigRegression {
    static class AiLyricsSettings {
        static class Snapshot {
            String model;
            int maxTokens = 32768;
            float temperature = 1;
        }
    }
    // PRODUCTION_METHODS
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var builder = new GeminiConfigRegression();
        var settings = new AiLyricsSettings.Snapshot();
        for (String model : new String[]{"gemini-3.1-flash-lite", "models/gemini-3.5-flash-lite", "gemini-3.5-flash-lite-preview",
                "gemini-3-pro-preview", "gemini-3.7-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite",
                "gemini-2.5-pro", "gemini-2.0-flash", "gemma-3-27b-it"}) {
            settings.model = model;
            JSONObject body = builder.geminiBody("fixture", settings);
            JSONObject config = body.getJSONObject("generationConfig");
            JSONObject thinking = config.optJSONObject("thinkingConfig");
            if (model.contains("gemini-3")) {
                check(!thinking.has("thinkingBudget"), model + " must not send a zero budget");
                check(thinking.getString("thinkingLevel").equals(model.contains("flash-lite") ? "minimal" : "low"), model);
            } else if (model.startsWith("gemini-2.5-flash")) {
                check(thinking.getInt("thinkingBudget") == 0, model);
            } else {
                check(thinking == null, model + " must use API defaults");
            }
            check(config.getInt("maxOutputTokens") == 32768, "token limit retained");
            check(builder.geminiBody("research", settings, 65536).getJSONObject("generationConfig").getInt("maxOutputTokens") == 65536, "research override retained");
            check(body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text").equals("fixture"), "prompt retained");
        }
        System.out.println("Gemini request regression passed (10 model variants, normal and research bodies)");
    }
}
'''
source = work / "GeminiConfigRegression.java"
source.write_text(fixture.replace("    // PRODUCTION_METHODS", methods))
dependencies = classpath(work, json_jar())
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work), str(source)], check=True)
subprocess.run([java_tool("java"), "-cp", dependencies, "GeminiConfigRegression"], check=True)
