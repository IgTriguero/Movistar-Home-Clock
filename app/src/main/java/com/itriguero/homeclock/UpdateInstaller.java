package com.itriguero.homeclock;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Instala un APK descargado con PackageInstaller. Android muestra su propio
 * diálogo de confirmación: sin device owner no se puede evitar ese toque.
 */
final class UpdateInstaller {

    static final String ACTION_INSTALL_RESULT = "com.itriguero.homeclock.INSTALL_RESULT";

    private UpdateInstaller() {
    }

    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    static void openUnknownSourcesSettings(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    static void install(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            long size = apk.length();
            try (OutputStream out = session.openWrite("homeclock.apk", 0, size);
                 FileInputStream in = new FileInputStream(apk)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }

            Intent result = new Intent(context, UpdateResultActivity.class)
                    .setAction(ACTION_INSTALL_RESULT);
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions activityOptions = ActivityOptions.makeBasic();
                activityOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                options = activityOptions.toBundle();
            }
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    sessionId,
                    result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE,
                    options);
            session.commit(pending.getIntentSender());
        }
    }
}
