package kr.ivlis.ivlyricsandroid;
import java.util.*;
public final class TrackProviderRegression {
    public static void main(String[] args) {
        Map<String, LyricsProviderSettings.ProviderConfig> configs = new LinkedHashMap<>();
        for (var provider : LyricsProviderSettings.PROVIDERS) configs.put(provider.id,
                new LyricsProviderSettings.ProviderConfig(provider, provider.defaultEnabled, false, true, false));
        var automatic = new LyricsProviderSettings.Snapshot(new ArrayList<>(configs.keySet()), configs, true, true);
        for (var provider : LyricsProviderSettings.PROVIDERS) {
            var selected = automatic.withSelectedProvider(provider.id);
            var plan = LyricsProviderSelectionPlan.create(selected, Set.of("lrclib", "paxsenix"));
            if (plan.providers.size() != 1 || !plan.providers.get(0).provider.id.equals(provider.id)) throw new AssertionError(provider.id);
            if (plan.attempts.isEmpty() || plan.attempts.stream().anyMatch(a -> !a.config.provider.id.equals(provider.id))) throw new AssertionError("fallback escaped selection");
            if (automatic.cacheKey().equals(selected.cacheKey())) throw new AssertionError("stale cache reused");
        }
        if (automatic.withSelectedProvider("") != automatic || automatic.withSelectedProvider("invalid") != automatic) throw new AssertionError("auto must restore original policy");
        if (automatic.config("unison").enabled || automatic.config("lrclib").karaoke) throw new AssertionError("global policy mutated");
        System.out.println("Track provider regression: explicit choices, disabled providers, cache isolation, automatic reset passed");
    }
}
