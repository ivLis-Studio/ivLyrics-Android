package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateChecker {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/ivLis-Studio/ivLyrics-Android/releases/latest";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Context appContext;

    UpdateChecker(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    interface Callback {
        void onUpdateChecked(UpdateInfo info);

        void onUpdateCheckFailed(String message);
    }

    void checkLatest(Callback callback) {
        executor.execute(() -> {
            try {
                UpdateInfo info = loadLatestRelease();
                mainHandler.post(() -> callback.onUpdateChecked(info));
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = error.getClass().getSimpleName();
                }
                String safeMessage = message;
                mainHandler.post(() -> callback.onUpdateCheckFailed(safeMessage));
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private UpdateInfo loadLatestRelease() throws Exception {
        JSONObject release = new JSONObject(readUrl(LATEST_RELEASE_URL));
        String tag = release.optString("tag_name", "");
        String releaseUrl = release.optString("html_url", "");
        String releaseName = release.optString("name", tag);
        String body = release.optString("body", "");
        boolean prerelease = release.optBoolean("prerelease", false);

        List<Asset> assets = parseAssets(release.optJSONArray("assets"));
        Asset versionAsset = findVersionAsset(assets);
        Asset apkAsset = findBestApkAsset(assets);

        int latestVersionCode = -1;
        String latestVersionName = versionNameFromTag(tag);
        String sha256 = "";
        if (versionAsset != null && !versionAsset.downloadUrl.isEmpty()) {
            JSONObject version = new JSONObject(readUrl(versionAsset.downloadUrl));
            latestVersionCode = version.optInt("versionCode", -1);
            latestVersionName = version.optString("versionName", latestVersionName);
            sha256 = shaForAsset(version.optJSONArray("apks"), apkAsset == null ? "" : apkAsset.name);
        }

        int currentCode = currentVersionCode();
        String currentName = currentVersionName();
        boolean newer = latestVersionCode > currentCode;
        if (latestVersionCode <= 0) {
            newer = compareVersions(latestVersionName, currentName) > 0;
        }

        return new UpdateInfo(
                newer,
                currentCode,
                currentName,
                latestVersionCode,
                latestVersionName,
                tag,
                releaseName,
                releaseUrl,
                body,
                prerelease,
                apkAsset == null ? "" : apkAsset.name,
                apkAsset == null ? "" : apkAsset.downloadUrl,
                apkAsset == null ? 0L : apkAsset.size,
                sha256
        );
    }

    private List<Asset> parseAssets(JSONArray array) {
        List<Asset> assets = new ArrayList<>();
        if (array == null) {
            return assets;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }
            assets.add(new Asset(
                    object.optString("name", ""),
                    object.optString("browser_download_url", ""),
                    object.optLong("size", 0L)
            ));
        }
        return assets;
    }

    private Asset findVersionAsset(List<Asset> assets) {
        for (Asset asset : assets) {
            String name = asset.name.toLowerCase(Locale.ROOT);
            if (name.endsWith("-version.json") || name.endsWith("version.json")) {
                return asset;
            }
        }
        return null;
    }

    private Asset findBestApkAsset(List<Asset> assets) {
        Asset fallback = null;
        Asset debug = null;
        for (Asset asset : assets) {
            String name = asset.name.toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk") || name.contains("unsigned")) {
                continue;
            }
            if (name.contains("-release")) {
                return asset;
            }
            if (name.contains("-debug")) {
                debug = asset;
            } else if (fallback == null) {
                fallback = asset;
            }
        }
        return fallback == null ? debug : fallback;
    }

    private String shaForAsset(JSONArray array, String assetName) {
        if (array == null || assetName == null || assetName.isEmpty()) {
            return "";
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null || !assetName.equals(object.optString("name", ""))) {
                continue;
            }
            return object.optString("sha256", "");
        }
        return "";
    }

    private String readUrl(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ivLyrics-Android/" + currentVersionName());
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + trimForError(body)));
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String trimForError(String value) {
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160) + "...";
    }

    private String versionNameFromTag(String tag) {
        String value = tag == null ? "" : tag.trim();
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    private int currentVersionCode() {
        PackageInfo info = packageInfo();
        if (info == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return (int) Math.min(Integer.MAX_VALUE, info.getLongVersionCode());
        }
        return info.versionCode;
    }

    private String currentVersionName() {
        PackageInfo info = packageInfo();
        return info == null || info.versionName == null ? "" : info.versionName;
    }

    private PackageInfo packageInfo() {
        if (appContext == null) {
            return null;
        }
        try {
            return appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private int compareVersions(String left, String right) {
        List<String> leftParts = versionParts(left);
        List<String> rightParts = versionParts(right);
        int count = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < count; index++) {
            String a = index < leftParts.size() ? leftParts.get(index) : "0";
            String b = index < rightParts.size() ? rightParts.get(index) : "0";
            int result = compareVersionPart(a, b);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private List<String> versionParts(String value) {
        List<String> parts = new ArrayList<>();
        String safe = value == null ? "" : value;
        for (String part : safe.split("[^0-9A-Za-z]+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private int compareVersionPart(String left, String right) {
        boolean leftNumber = left.matches("\\d+");
        boolean rightNumber = right.matches("\\d+");
        if (leftNumber && rightNumber) {
            long a = Long.parseLong(left);
            long b = Long.parseLong(right);
            return Long.compare(a, b);
        }
        return left.compareToIgnoreCase(right);
    }

    static final class UpdateInfo {
        final boolean updateAvailable;
        final int currentVersionCode;
        final String currentVersionName;
        final int latestVersionCode;
        final String latestVersionName;
        final String tag;
        final String releaseName;
        final String releaseUrl;
        final String releaseNotes;
        final boolean prerelease;
        final String apkName;
        final String apkDownloadUrl;
        final long apkSize;
        final String apkSha256;

        UpdateInfo(
                boolean updateAvailable,
                int currentVersionCode,
                String currentVersionName,
                int latestVersionCode,
                String latestVersionName,
                String tag,
                String releaseName,
                String releaseUrl,
                String releaseNotes,
                boolean prerelease,
                String apkName,
                String apkDownloadUrl,
                long apkSize,
                String apkSha256
        ) {
            this.updateAvailable = updateAvailable;
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName == null ? "" : currentVersionName;
            this.latestVersionCode = latestVersionCode;
            this.latestVersionName = latestVersionName == null ? "" : latestVersionName;
            this.tag = tag == null ? "" : tag;
            this.releaseName = releaseName == null ? "" : releaseName;
            this.releaseUrl = releaseUrl == null ? "" : releaseUrl;
            this.releaseNotes = releaseNotes == null ? "" : releaseNotes;
            this.prerelease = prerelease;
            this.apkName = apkName == null ? "" : apkName;
            this.apkDownloadUrl = apkDownloadUrl == null ? "" : apkDownloadUrl;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256 == null ? "" : apkSha256;
        }

        String latestDisplayVersion() {
            if (!latestVersionName.isEmpty()) {
                return latestVersionName;
            }
            return tag.isEmpty() ? releaseName : tag;
        }
    }

    private static final class Asset {
        final String name;
        final String downloadUrl;
        final long size;

        Asset(String name, String downloadUrl, long size) {
            this.name = name == null ? "" : name;
            this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
            this.size = size;
        }
    }
}
