package kr.ivlis.ivlyricsandroid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Quality tiers, idle-release choices and display formatting of the Local AI provider. */
final class LocalAiModels {
    static final String MODEL_BASE_URL = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/";
    static final Tier LIGHT = new Tier("light", "local_ai.tier_light", "Hy-MT2-1.8B-Q4_K_M.gguf", 1_133_080_448L);
    static final Tier MEDIUM = new Tier("medium", "local_ai.tier_medium", "Hy-MT2-1.8B-Q6_K.gguf", 1_474_785_120L);
    static final Tier HIGH = new Tier("high", "local_ai.tier_high", "Hy-MT2-1.8B-Q8_0.gguf", 1_908_528_192L);
    /** Slider order, lightest first. */
    static final List<Tier> TIERS = Collections.unmodifiableList(Arrays.asList(LIGHT, MEDIUM, HIGH));
    /** Product decision for mobile: start with the lightest download (the desktop app defaults to High). */
    static final Tier DEFAULT_TIER = LIGHT;
    /** "Free memory when idle" choices in minutes; 0 keeps the model loaded. */
    static final int[] IDLE_UNLOAD_MINUTES = {10, 30, 60, 0};
    static final int DEFAULT_IDLE_UNLOAD_MINUTES = 10;
    /** Why a device cannot run the engine, as i18n keys shown in the current UI language. */
    static final String NEEDS_ANDROID_9 = "local_ai.needs_android9";
    static final String NEEDS_ARM64 = "local_ai.needs_arm64";
    /** What failed, as i18n keys shown in the current UI language; the technical detail goes to the log. */
    static final String ERROR_ENGINE = "local_ai.error_engine";
    static final String ERROR_LOAD = "local_ai.error_load";
    static final String ERROR_TRANSLATE = "local_ai.error_translate";
    static final String ERROR_DOWNLOAD = "local_ai.error_download";
    /** Its "{status}" placeholder takes the HTTP status code. */
    static final String ERROR_DOWNLOAD_HTTP = "local_ai.error_download_http";
    static final String ERROR_DELETE = "local_ai.error_delete";

    private LocalAiModels() {
    }

    static Tier tier(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (Tier tier : TIERS) {
            if (tier.id.equals(normalized)) {
                return tier;
            }
        }
        return DEFAULT_TIER;
    }

    static Tier tierAt(int index) {
        return TIERS.get(Math.max(0, Math.min(TIERS.size() - 1, index)));
    }

    /** A stored idle choice, or the default when the value is not one of the offered options. */
    static int idleUnloadMinutes(Object stored) {
        long minutes;
        if (stored instanceof Number) {
            double value = ((Number) stored).doubleValue();
            if (value != Math.rint(value)) {
                return DEFAULT_IDLE_UNLOAD_MINUTES;
            }
            minutes = (long) value;
        } else if (stored instanceof String) {
            try {
                minutes = Long.parseLong(((String) stored).trim());
            } catch (NumberFormatException invalid) {
                return DEFAULT_IDLE_UNLOAD_MINUTES;
            }
        } else {
            return DEFAULT_IDLE_UNLOAD_MINUTES;
        }
        for (int option : IDLE_UNLOAD_MINUTES) {
            if (option == minutes) {
                return option;
            }
        }
        return DEFAULT_IDLE_UNLOAD_MINUTES;
    }

    static String formatGigabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2fGB", bytes / 1e9);
    }

    static long megabytes(long bytes) {
        return Math.round(bytes / 1e6);
    }

    static int percent(long loaded, long total) {
        return total <= 0 ? 0 : (int) Math.floor(Math.min(1d, Math.max(0d, loaded / (double) total)) * 100);
    }

    /** Replaces {name} placeholders in one pass; unknown names stay visible, as in the desktop provider. */
    static String format(String template, String... namesAndValues) {
        String source = template == null ? "" : template;
        StringBuilder result = new StringBuilder(source.length() + 16);
        int index = 0;
        while (index < source.length()) {
            int open = source.indexOf('{', index);
            int close = open < 0 ? -1 : source.indexOf('}', open + 1);
            if (close < 0) {
                result.append(source, index, source.length());
                break;
            }
            String name = source.substring(open + 1, close);
            String value = null;
            for (int pair = 0; pair + 1 < namesAndValues.length; pair += 2) {
                if (namesAndValues[pair].equals(name)) {
                    value = namesAndValues[pair + 1];
                    break;
                }
            }
            result.append(source, index, open).append(value == null ? source.substring(open, close + 1) : value);
            index = close + 1;
        }
        return result.toString();
    }

    static final class Tier {
        final String id;
        final String labelKey;
        final String fileName;
        final long sizeBytes;

        Tier(String id, String labelKey, String fileName, long sizeBytes) {
            this.id = id;
            this.labelKey = labelKey;
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
        }

        String url() {
            return MODEL_BASE_URL + fileName;
        }
    }
}
