package dev.marquinhhou.crsscheduler.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/**
 * The full "entire schedule" experience, as a real in-app screen rather than
 * crammed into a widget. This is where the whole week is genuinely scrollable
 * and every class is tappable (maps prompt shown as a normal AlertDialog here,
 * since we have a real window to work with).
 */
public class WeekScheduleActivity extends AppCompatActivity {

    private static final int RED = Color.parseColor("#FF2E17");
    private static final int INK = Color.parseColor("#F3F2ED");
    private static final int INK_DIM = Color.parseColor("#8B8B86");

    private boolean redirectedForTerms = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    saveScheduleToGallery();
                } else {
                    Toast.makeText(this, "Storage permission is needed to save the image.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> icsExportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/calendar"), uri -> {
                if (uri != null) writeIcsToUri(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SettingsStore.hasAcceptedTerms(this)) {
            // Only the Week widget requires no mandatory setup screen, so someone
            // could reach here without ever having seen the Terms gate -- send
            // them through Configure first, which owns that flow.
            redirectedForTerms = true;
            startActivity(new Intent(this, ConfigureActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_week_schedule);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, ConfigureActivity.class)));
        findViewById(R.id.btn_save_gallery).setOnClickListener(v -> onSaveToGalleryTapped());
        findViewById(R.id.btn_export_ics).setOnClickListener(v -> onExportIcsTapped());

        buildWeek();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (redirectedForTerms) return;
        buildWeek(); // pick up any edits made in Configure/Edit Info since last shown
    }

    private void buildWeek() {
        LinearLayout container = findViewById(R.id.week_schedule_container);
        container.removeAllViews();

        List<ClassSession> schedule = ScheduleStore.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK) - 1;

        if (schedule.isEmpty()) {
            View empty = inflater.inflate(R.layout.row_day_empty, container, false);
            ((TextView) empty.findViewById(R.id.day_empty_text))
                    .setText("No schedule loaded. Tap the gear to add one.");
            container.addView(empty);
            return;
        }

        SettingsStore.SemesterPhase phase = SettingsStore.effectiveDisplayPhase(this);
        if (phase == SettingsStore.SemesterPhase.UPCOMING || phase == SettingsStore.SemesterPhase.ENDED) {
            View empty = inflater.inflate(R.layout.row_day_empty, container, false);
            String message = phase == SettingsStore.SemesterPhase.UPCOMING
                    ? "Semester hasn't started yet. Classes begin "
                            + WidgetRenderer.formatSemesterDate(SettingsStore.getSemesterStart(this)) + "."
                    : "Semester has ended. Classes ran through "
                            + WidgetRenderer.formatSemesterDate(SettingsStore.getSemesterEnd(this)) + ".";
            ((TextView) empty.findViewById(R.id.day_empty_text)).setText(message);
            container.addView(empty);
            return;
        }

        for (int dayIdx : WidgetRenderer.WEEK_ORDER) {
            View head = inflater.inflate(R.layout.row_day_header, container, false);
            TextView dayLabel = head.findViewById(R.id.day_label);
            dayLabel.setText(WidgetRenderer.DAY_LABELS[dayIdx]);
            boolean isToday = dayIdx == today;
            ((ImageView) head.findViewById(R.id.day_dot))
                    .setImageResource(isToday ? R.drawable.dot_red : R.drawable.dot_dim);
            dayLabel.setTextColor(isToday ? INK : INK_DIM);
            container.addView(head);

            List<ClassSession> items = new ArrayList<>();
            for (ClassSession c : schedule) if (c.days.contains(dayIdx)) items.add(c);
            items.sort((a, b) -> Integer.compare(a.start, b.start));

            if (items.isEmpty()) {
                View dash = inflater.inflate(R.layout.row_day_empty, container, false);
                ((TextView) dash.findViewById(R.id.day_empty_text)).setText("\u2014");
                container.addView(dash);
            } else {
                for (ClassSession c : items) container.addView(buildRow(inflater, container, c));
            }
        }
    }

    private View buildRow(LayoutInflater inflater, LinearLayout parent, ClassSession c) {
        View row = inflater.inflate(R.layout.row_class, parent, false);
        ((TextView) row.findViewById(R.id.row_time))
                .setText(WidgetRenderer.minToLabel(c.start) + "\n" + WidgetRenderer.minToLabel(c.end));
        ((TextView) row.findViewById(R.id.row_name)).setText(c.name);

        String meta = c.displayRoom()
                + (c.instructor != null && !c.instructor.isEmpty() ? " \u00B7 " + c.instructor : "");
        ((TextView) row.findViewById(R.id.row_room)).setText(meta);

        row.setOnClickListener(v -> maybeOfferMaps(c));
        return row;
    }

    private void maybeOfferMaps(ClassSession c) {
        String mapQuery = (c.room != null && !c.room.trim().isEmpty() && !c.room.equalsIgnoreCase("TBA"))
                ? c.room
                : null;
        if (mapQuery == null || mapQuery.trim().isEmpty()) {
            Toast.makeText(this, "No location set for this class yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String room = mapQuery;
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setMessage("Open " + room + " in a maps app?\n\n"
                        + getString(R.string.maps_room_disclaimer_short))
                .setPositiveButton("Open Maps", (d, w) -> openMaps(room))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openMaps(String room) {
        String campus = SettingsStore.getCampusHint(this);
        String query = campus != null && !campus.trim().isEmpty() ? room + " " + campus : room;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app found to handle this.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Below Android 10, writing into the shared gallery via MediaStore still needs
     * WRITE_EXTERNAL_STORAGE at runtime. On 10+ (scoped storage) inserting into our
     * own MediaStore entry needs no permission at all, so this just proceeds there.
     */
    private void onSaveToGalleryTapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            saveScheduleToGallery();
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void saveScheduleToGallery() {
        List<ClassSession> schedule = ScheduleStore.load(this);
        if (schedule.isEmpty()) {
            Toast.makeText(this, "No schedule loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        ioExecutor.execute(() -> {
            Bitmap bitmap = ScheduleImageExporter.render(this, schedule);
            String message;
            if (bitmap == null) {
                message = "Nothing to save yet.";
            } else {
                try {
                    ScheduleImageExporter.saveToGallery(this, bitmap);
                    message = "Saved to Gallery.";
                } catch (IOException e) {
                    message = "Couldn't save image: " + e.getMessage();
                } finally {
                    bitmap.recycle();
                }
            }
            String finalMessage = message;
            runOnUiThread(() -> Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    private void onExportIcsTapped() {
        List<ClassSession> schedule = ScheduleStore.load(this);
        if (schedule.isEmpty()) {
            Toast.makeText(this, "No schedule loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        icsExportLauncher.launch(IcsExporter.suggestedFileName());
    }

    private void writeIcsToUri(Uri uri) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        ioExecutor.execute(() -> {
            String message;
            try {
                String ics = IcsExporter.build(this, schedule);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open an output stream for the calendar file.");
                    out.write(ics.getBytes(StandardCharsets.UTF_8));
                }
                message = "Saved calendar file. Import it from your calendar app.";
            } catch (IOException e) {
                message = "Couldn't save calendar file: " + e.getMessage();
            }
            String finalMessage = message;
            runOnUiThread(() -> Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show());
        });
    }
}
