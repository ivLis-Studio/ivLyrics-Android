#!/usr/bin/env python3
"""Exercise the production DeepL client and provider capability gates with synthetic data."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, classpath, java_tool, json_jar

work = REPORTS / 'deepl'
work.mkdir(parents=True, exist_ok=True)
source = (SHARED / 'AiLyricsSettings.java').read_text()
def section(start, end):
    return source[source.index(start):source.index(end, source.index(start))]
provider = section('    static final class Provider {', '    static final class ProviderProfile {')
providers = section('    static final List<Provider> PROVIDERS =', '    static final List<Provider> ALL_AI_PROVIDERS')
gates = section('        boolean hasKeylessTranslationProvider()', '        Snapshot forProvider(')
fixture = '''package kr.ivlis.ivlyricsandroid;
import java.util.*;
final class ProviderGates {
// PROVIDERS
// PROVIDER
static Provider aiProviderById(String id) { return PROVIDERS.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }
static class Snapshot {
    Map<String,Snapshot> profiles = new HashMap<>();
    Map<String,Boolean> aiProviderEnabled = new HashMap<>();
    List<String> aiProviderOrder = new ArrayList<>();
    boolean bingTranslateEnabled, googleTranslateEnabled;
    String apiKey = "", model = "";
    Snapshot forProvider(String id) { return profiles.get(id); }
    boolean hasApiKey() { return !apiKey.isEmpty(); }
    boolean hasModel() { return !model.isEmpty(); }
// GATES
}
}
'''.replace('// PROVIDERS', providers).replace('// PROVIDER', provider).replace('// GATES', gates)
(work / 'ProviderGates.java').write_text(fixture)
deps = classpath(work, json_jar())
sources = [SHARED / 'DeepLTranslationProvider.java', work / 'ProviderGates.java', ROOT / 'tests/kr/ivlis/ivlyricsandroid/DeepLRegression.java']
subprocess.run([java_tool('javac'), '-cp', deps, '-d', str(work), *map(str, sources)], check=True)
subprocess.run([java_tool('java'), '-cp', deps, 'kr.ivlis.ivlyricsandroid.DeepLRegression'], check=True)
