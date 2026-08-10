package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InAppBrowserUrlPolicyTest {
    @Test
    public void normalBrowserOnlyAcceptsHttpsCreatorProfiles() {
        assertTrue(InAppBrowserUrlPolicy.isAllowedInitialUrl("https://lyrics.ivl.is/@creator", false));
        assertFalse(InAppBrowserUrlPolicy.isAllowedInitialUrl("http://lyrics.ivl.is/@creator", false));
        assertFalse(InAppBrowserUrlPolicy.isAllowedInitialUrl("https://lyrics.ivl.is.evil.example/@creator", false));
        assertFalse(InAppBrowserUrlPolicy.isAllowedInitialUrl("javascript:alert(1)", false));
    }

    @Test
    public void loginBrowserUsesOnlyTheOAuthHostSet() {
        assertTrue(InAppBrowserUrlPolicy.isAllowedInitialUrl("https://discord.com/oauth2/authorize", true));
        assertTrue(InAppBrowserUrlPolicy.isAllowedCreatorLoginUrl("https://lyrics.api.ivl.is/auth/callback"));
        assertFalse(InAppBrowserUrlPolicy.isAllowedInitialUrl("https://example.com/oauth", true));
    }

    @Test
    public void externalNavigationOnlyAllowsHttpSchemes() {
        assertTrue(InAppBrowserUrlPolicy.isExternalHttpUrl("https://example.com/path"));
        assertTrue(InAppBrowserUrlPolicy.isExternalHttpUrl("http://example.com/path"));
        assertFalse(InAppBrowserUrlPolicy.isExternalHttpUrl("intent://example.com"));
        assertFalse(InAppBrowserUrlPolicy.isExternalHttpUrl("data:text/html,hello"));
    }
}
