package dev.marquinhhou.crsscheduler.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.SettingsStore;

/**
 * Popped up when a class row is tapped on either widget. Widgets can't show a
 * normal AlertDialog directly (no window of their own), so this is a small
 * dialog-themed Activity instead -- the standard way to get dialog-like
 * confirmation out of a RemoteViews tap.
 */
public class WidgetActionActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM = "extra_room";
    public static final String EXTRA_CLASS_NAME = "extra_class_name";

    private String room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_NothingSchedule_Dialog);
        setContentView(R.layout.activity_widget_action);

        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        room = getIntent().getStringExtra(EXTRA_ROOM);

        ((TextView) findViewById(R.id.dialog_class_name)).setText(className != null ? className : "Class");
        ((TextView) findViewById(R.id.dialog_room)).setText(room != null ? room : "");

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_open_maps).setOnClickListener(v -> openMaps());
    }

    private void openMaps() {
        if (room == null || room.trim().isEmpty()) {
            finish();
            return;
        }
        String campus = SettingsStore.getCampusHint(this);
        String query = campus != null && !campus.trim().isEmpty() ? room + " " + campus : room;
        try {
            Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
            startActivity(geo);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app found to handle this.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
