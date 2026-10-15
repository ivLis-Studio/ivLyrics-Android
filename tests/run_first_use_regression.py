#!/usr/bin/env python3
"""Replay production first-use credential/permission transitions with controlled token callbacks.

No Spotify traffic or Android runtime. Native layout/touch/persistence is checked separately by
FirstUseUiRegressionTest; this fixture exercises successful and failed asynchronous validation.
"""
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool


def declaration(source, signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]


source = (SHARED / "BaseLyricsActivity.java").read_text()
methods = "\n".join(declaration(source, signature) for signature in (
    "private void rememberSpotifySetupDraft()", "private void updateOnboardingActionState()",
    "private void handleOnboardingNextAction()", "private String onboardingNextButtonText()",
    "private void applySpotifySetupFromRequiredPanel()", "private boolean saveSpotifyCredentials(",
    "private void finishSpotifyCredentialsSave(", "private void setSpotifyValidationStatus(",
    "private void populateSpotifyCredentialInputs(", "private String textOf("))

fixture = r'''
package kr.ivlis.ivlyricsandroid;

public final class FirstUseRegression {
    static int checks;
    static void check(boolean condition) { if (!condition) throw new AssertionError("check " + checks); checks++; }
    static class TextView {
        String text = ""; boolean enabled = true; float alpha = 1;
        String getText() { return text; }
        void setText(String text) { this.text = text; }
        void setEnabled(boolean enabled) { this.enabled = enabled; }
        void setAlpha(float alpha) { this.alpha = alpha; }
    }
    static class EditText extends TextView {}
    static class AiLyricsSettings {
        Snapshot saved = new Snapshot("", ""); int saves;
        Snapshot snapshot() { return saved; }
        void setSpotifyApiCredentials(String id, String secret) { saves++; saved = new Snapshot(id, secret); }
        static class Snapshot {
            final String spotifyClientId, spotifyClientSecret;
            Snapshot(String id, String secret) { spotifyClientId = id; spotifyClientSecret = secret; }
            boolean hasSpotifyApiCredentials() { return !spotifyClientId.isEmpty() && !spotifyClientSecret.isEmpty(); }
        }
    }
    static class LyricsRepository {
        interface SpotifyTokenValidationCallback {
            void onSpotifyTokenValidated(long expiry);
            void onSpotifyTokenValidationFailed(String message);
            void onSpotifyTokenValidationLog(String message);
        }
        SpotifyTokenValidationCallback callback; String id, secret; int validations, cacheClears;
        void validateSpotifyCredentials(String id, String secret, SpotifyTokenValidationCallback callback) {
            validations++; this.id = id; this.secret = secret; this.callback = callback;
        }
        void clearCache() { cacheClears++; }
    }
    static class IvLyricsBridge { static boolean isEmbedded(Flow flow) { return flow.embedded; } }
    static class NowPlayingService { static boolean isNotificationAccessEnabled(Flow flow) { return flow.permission; } }
    static class Flow {
        static final int ONBOARDING_STEP_COUNT = 3;
        AiLyricsSettings aiLyricsSettings = new AiLyricsSettings();
        LyricsRepository lyricsRepository = new LyricsRepository();
        EditText spotifyClientIdInput, spotifyClientSecretInput;
        EditText spotifySetupClientIdInput = new EditText(), spotifySetupClientSecretInput = new EditText();
        TextView spotifySetupStatusView = new TextView(), aiSettingsStatusView = new TextView();
        TextView onboardingNextButton = new TextView();
        String onboardingClientIdDraft, onboardingClientSecretDraft, onboardingValidationStatus, toast;
        boolean spotifyCredentialsValidationInFlight, embedded, permission, setupHidden;
        int onboardingStep, openedPermissions, reloads;
        String ui(String key) { return key; }
        String uiFormat(String key, String detail) { return key + ": " + detail; }
        void showSavedToast(String message) { toast = message; }
        void appendLog(String message) {}
        void openMediaPermissionSettings() { openedPermissions++; }
        void showOnboardingStep(int phase) { onboardingStep = phase; updateOnboardingActionState(); }
        void updateSpotifySetupGate(boolean animate) {
            setupHidden = permission && aiLyricsSettings.snapshot().hasSpotifyApiCredentials();
        }
        void reloadCurrentLyricsFromSettings() { reloads++; }
        // PRODUCTION
    }
    public static void main(String[] args) {
        Flow flow = new Flow();
        flow.handleOnboardingNextAction();
        check(flow.onboardingStep == 1);
        flow.handleOnboardingNextAction();
        check(flow.openedPermissions == 1 && flow.onboardingStep == 1);
        flow.permission = true;
        flow.handleOnboardingNextAction();
        check(flow.onboardingStep == 2 && flow.onboardingNextButton.enabled);
        flow.handleOnboardingNextAction();
        check(flow.lyricsRepository.validations == 0 && flow.aiLyricsSettings.saves == 0);
        check(flow.onboardingValidationStatus.equals("toast.spotify_missing"));

        flow.spotifySetupClientIdInput.setText(" fixture-client ");
        flow.spotifySetupClientSecretInput.setText(" fixture-secret ");
        flow.handleOnboardingNextAction();
        check(flow.lyricsRepository.validations == 1 && flow.spotifyCredentialsValidationInFlight);
        check(flow.aiLyricsSettings.saves == 0 && !flow.setupHidden);
        check(flow.lyricsRepository.id.equals("fixture-client") && flow.lyricsRepository.secret.equals("fixture-secret"));
        check(!flow.onboardingNextButton.enabled && !flow.spotifySetupClientIdInput.enabled);
        flow.handleOnboardingNextAction();
        check(flow.lyricsRepository.validations == 1);
        flow.lyricsRepository.callback.onSpotifyTokenValidationFailed("401");
        check(!flow.spotifyCredentialsValidationInFlight && flow.onboardingNextButton.enabled);
        check(flow.aiLyricsSettings.saves == 0 && !flow.setupHidden);
        flow.populateSpotifyCredentialInputs(flow.aiLyricsSettings.snapshot());
        check(flow.spotifySetupClientIdInput.text.equals(" fixture-client "));
        check(flow.spotifySetupClientSecretInput.text.equals(" fixture-secret "));
        check(flow.spotifySetupStatusView.text.endsWith("401"));
        // Replace the form views after back/rotation: only the production draft/populate methods run.
        flow.rememberSpotifySetupDraft();
        flow.spotifySetupClientIdInput = new EditText(); flow.spotifySetupClientSecretInput = new EditText();
        flow.populateSpotifyCredentialInputs(flow.aiLyricsSettings.snapshot());
        check(flow.spotifySetupClientIdInput.text.equals(" fixture-client "));
        check(flow.spotifySetupClientSecretInput.text.equals(" fixture-secret "));
        flow.handleOnboardingNextAction();
        check(flow.lyricsRepository.validations == 2);
        // While validation runs, a back/forward trip must keep the in-flight lock.
        flow.onboardingStep = 1; flow.updateOnboardingActionState(); check(flow.onboardingNextButton.enabled);
        flow.onboardingStep = 2; flow.updateOnboardingActionState(); check(!flow.onboardingNextButton.enabled);
        flow.lyricsRepository.callback.onSpotifyTokenValidated(3600);
        check(!flow.spotifyCredentialsValidationInFlight && flow.onboardingNextButton.enabled);
        check(flow.aiLyricsSettings.saves == 1 && flow.setupHidden);
        check(flow.onboardingClientIdDraft.equals("fixture-client") && flow.onboardingClientSecretDraft.equals("fixture-secret"));
        check(flow.spotifySetupClientIdInput.text.equals("fixture-client"));
        check(flow.lyricsRepository.cacheClears == 1 && flow.reloads == 1);
        flow.handleOnboardingNextAction(); flow.lyricsRepository.callback.onSpotifyTokenValidated(3600);
        check(flow.lyricsRepository.cacheClears == 1 && flow.reloads == 1);

        Flow noPermission = new Flow(); noPermission.onboardingStep = 2;
        noPermission.spotifySetupClientIdInput.setText("id"); noPermission.spotifySetupClientSecretInput.setText("secret");
        noPermission.handleOnboardingNextAction(); noPermission.lyricsRepository.callback.onSpotifyTokenValidated(3600);
        check(noPermission.aiLyricsSettings.snapshot().hasSpotifyApiCredentials() && !noPermission.setupHidden);
        noPermission.permission = true; noPermission.updateSpotifySetupGate(false); check(noPermission.setupHidden);
        Flow unavailable = new Flow(); unavailable.onboardingStep = 2; unavailable.lyricsRepository = null;
        unavailable.spotifySetupClientIdInput.setText("id"); unavailable.spotifySetupClientSecretInput.setText("secret");
        unavailable.handleOnboardingNextAction();
        check(!unavailable.spotifyCredentialsValidationInFlight && unavailable.aiLyricsSettings.saves == 0);
        check(unavailable.onboardingValidationStatus.endsWith("spotify.error.repository_unavailable"));
        Flow embedded = new Flow(); embedded.embedded = true; embedded.onboardingStep = 2;
        embedded.handleOnboardingNextAction(); check(embedded.lyricsRepository.validations == 0);
        System.out.println("First-use permission/credential regression: " + checks + " checks passed");
    }
}
'''.replace("// PRODUCTION", methods)
work = REPORTS / "first-use"
work.mkdir(parents=True, exist_ok=True)
java = work / "FirstUseRegression.java"
java.write_text(fixture)
subprocess.run([java_tool("javac"), "-d", str(work), str(java)], check=True)
result = subprocess.run([java_tool("java"), "-cp", str(work), "kr.ivlis.ivlyricsandroid.FirstUseRegression"],
                        check=True, capture_output=True, text=True)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
