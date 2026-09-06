package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class UpdatePackageVerifier {
    private static final String RELEASE_PATH_PREFIX =
            "/ivLis-Studio/ivLyrics-Android/releases/download/";

    private UpdatePackageVerifier() {
    }

    static URL requireReleaseAssetUrl(String value) throws IOException {
        URL url = parseHttpsUrl(value);
        if (!"github.com".equalsIgnoreCase(url.getHost())
                || !url.getPath().startsWith(RELEASE_PATH_PREFIX)) {
            throw new IOException("Update APK URL is not an ivLyrics GitHub release asset");
        }
        return url;
    }

    static void requireTrustedDownloadUrl(URL url) throws IOException {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Update download did not use HTTPS");
        }
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (!"github.com".equals(host) && !host.endsWith(".githubusercontent.com")) {
            throw new IOException("Update download left GitHub's release CDN");
        }
    }

    static void verify(Context context, File apkFile, UpdateChecker.UpdateInfo info) throws IOException {
        if (context == null || apkFile == null || !apkFile.isFile() || apkFile.length() <= 0L) {
            throw new IOException("Downloaded APK is empty");
        }
        if (info == null || info.apkSize <= 0L || info.apkSha256.trim().isEmpty()) {
            throw new IOException("Release metadata is missing APK integrity data");
        }
        if (apkFile.length() != info.apkSize) {
            throw new IOException("Downloaded APK size does not match release metadata");
        }

        String expectedSha256 = normalizeSha256(info.apkSha256);
        String actualSha256 = sha256(apkFile);
        if (!MessageDigest.isEqual(
                expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actualSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IOException("Downloaded APK SHA-256 does not match release metadata");
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo archive = packageInfoFromArchive(packageManager, apkFile);
        PackageInfo installed = installedPackageInfo(packageManager, context.getPackageName());
        if (archive == null || installed == null) {
            throw new IOException("Could not inspect update APK package information");
        }
        if (!context.getPackageName().equals(archive.packageName)) {
            throw new IOException("Update APK package name does not match ivLyrics");
        }

        long archiveVersion = longVersionCode(archive);
        long installedVersion = longVersionCode(installed);
        if (archiveVersion <= installedVersion) {
            throw new IOException("Update APK is not newer than the installed app");
        }
        if (info.latestVersionCode > 0 && archiveVersion != info.latestVersionCode) {
            throw new IOException("Update APK version code does not match release metadata");
        }
        if (!info.latestVersionName.isEmpty()
                && !info.latestVersionName.equals(archive.versionName)) {
            throw new IOException("Update APK version name does not match release metadata");
        }
        if (!hasMatchingSigner(installed, archive)) {
            throw new IOException("Update APK signing certificate does not match the installed app");
        }
    }

    private static URL parseHttpsUrl(String value) throws IOException {
        URL url = new URL(value == null ? "" : value.trim());
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Update APK URL must use HTTPS");
        }
        return url;
    }

    private static String normalizeSha256(String value) throws IOException {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace("sha256:", "");
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IOException("Release metadata contains an invalid APK SHA-256");
        }
        return normalized;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo packageInfoFromArchive(PackageManager manager, File apkFile) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return manager.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo installedPackageInfo(PackageManager manager, String packageName)
            throws IOException {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        try {
            return manager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("Installed ivLyrics package was not found", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static long longVersionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
    }

    private static boolean hasMatchingSigner(PackageInfo installed, PackageInfo archive) {
        List<byte[]> trusted = signerCertificates(installed, true);
        List<byte[]> candidate = signerCertificates(archive, false);
        if (trusted.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        for (byte[] candidateCertificate : candidate) {
            boolean matched = false;
            for (byte[] trustedCertificate : trusted) {
                if (MessageDigest.isEqual(candidateCertificate, trustedCertificate)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private static List<byte[]> signerCertificates(PackageInfo info, boolean includeHistory) {
        List<byte[]> certificates = new ArrayList<>();
        if (info == null) {
            return certificates;
        }
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) {
                return certificates;
            }
            signatures = includeHistory && signingInfo.hasPastSigningCertificates()
                    ? signingInfo.getSigningCertificateHistory()
                    : signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null) {
            return certificates;
        }
        for (Signature signature : signatures) {
            if (signature != null) {
                certificates.add(signature.toByteArray());
            }
        }
        return certificates;
    }
}
