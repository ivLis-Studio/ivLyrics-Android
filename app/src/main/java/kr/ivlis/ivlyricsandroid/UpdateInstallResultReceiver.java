package kr.ivlis.ivlyricsandroid;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

/** Receives PackageInstaller callbacks without exposing MainActivity as an intent relay. */
public final class UpdateInstallResultReceiver extends BroadcastReceiver {
    static final String ACTION_UPDATE_INSTALL_RESULT =
            "kr.ivlis.ivlyricsandroid.UPDATE_INSTALL_RESULT";

    @Override
    public void onReceive(Context context, Intent callback) {
        if (context == null || callback == null
                || !ACTION_UPDATE_INSTALL_RESULT.equals(callback.getAction())) {
            return;
        }

        int status = callback.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmationIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmationIntent = callback.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmationIntent = callback.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmationIntent != null) {
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmationIntent);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Fall through to a local failure message.
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            return;
        }

        String detail = callback.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String message = "ivLyrics: update installation failed";
        if (detail != null && !detail.trim().isEmpty()) {
            message += " (" + detail.trim() + ")";
        }
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}
