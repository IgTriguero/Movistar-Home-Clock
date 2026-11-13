package com.itriguero.homeclock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Recibe el resultado de PackageInstaller. Es una activity transparente para
 * lanzar el diálogo de confirmación cuando el usuario está usando la app.
 * Tras instalar intenta reabrir el reloj; Android 15 puede bloquear ese
 * arranque en segundo plano y dejar el launcher delante.
 */
public class UpdateResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(confirm);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } else {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show();
        }

        finish();
    }
}
