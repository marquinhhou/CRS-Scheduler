package dev.marquinhhou.crsscheduler.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;

/**
 * Popped up from the widget's Form 5 button when nothing's uploaded yet -- a small
 * dialog-themed Activity (same pattern as WidgetSaveActivity and WidgetActionActivity, since
 * widgets can't show a normal AlertDialog), prompting for a PDF right there instead of pulling
 * the user into the full Configure screen. Once a Form 5 is already on file, the widget's
 * button opens it directly and never reaches this Activity.
 */
public class WidgetForm5PromptActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> form5Picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) onForm5Picked(uri); else finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theming.applyDialogTheme(this);
        setContentView(Theming.pick(this,
                R.layout.activity_widget_form5_ge, R.layout.activity_widget_form5_ne, R.layout.activity_widget_form5_adaptive));

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_upload_form5).setOnClickListener(v -> form5Picker.launch(new String[]{"application/pdf"}));
    }

    private void onForm5Picked(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support a persistable grant -- it'll still work for this
            // session, but may need re-picking after the app restarts.
        }
        SettingsStore.setForm5Uri(this, uri);
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Form 5 saved.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
