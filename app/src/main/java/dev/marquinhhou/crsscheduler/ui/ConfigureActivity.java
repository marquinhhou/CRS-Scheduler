package dev.marquinhhou.crsscheduler.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.data.IcsImporter;
import dev.marquinhhou.crsscheduler.data.ScheduleHistoryStore;
import dev.marquinhhou.crsscheduler.data.ScheduleParser;
import dev.marquinhhou.crsscheduler.data.ScheduleStore;
import dev.marquinhhou.crsscheduler.data.SettingsStore;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.widget.WidgetRefreshScheduler;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

public class ConfigureActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private EditText htmlInput;
    private TextView statusText;
    private EditText inputCampus;
    private SwitchCompat switchEditMode;
    private LinearLayout editClassList;
    private Button btnSemesterStart;
    private Button btnSemesterEnd;
    private SwitchCompat switchSemesterOngoing;
    private TextView[] reminderChips;
    private static final int[] REMINDER_LEAD_OPTIONS = {0, 5, 10, 15, 30};

    // Collapsible "settings" cards -- collapsed by default, auto-expanded in
    // onCreate() if that section already has a non-default value set.
    private View headerMaps, bodyMaps, headerSemester, bodySemester, headerReminders, bodyReminders;
    private ImageView chevronMaps, chevronSemester, chevronReminders;
    private TextView summaryMaps, summarySemester, summaryReminders;

    private static final DateTimeFormatter SETTINGS_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) readFileIntoInput(uri);
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Notifications are off for this app, so reminders won't show. You can allow them from system Settings any time.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_configure);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        htmlInput = findViewById(R.id.html_input);
        statusText = findViewById(R.id.status_text);
        inputCampus = findViewById(R.id.input_campus);
        switchEditMode = findViewById(R.id.switch_edit_mode);
        editClassList = findViewById(R.id.edit_class_list);
        btnSemesterStart = findViewById(R.id.btn_semester_start);
        btnSemesterEnd = findViewById(R.id.btn_semester_end);
        switchSemesterOngoing = findViewById(R.id.switch_semester_ongoing);
        reminderChips = new TextView[]{
                findViewById(R.id.chip_reminder_off),
                findViewById(R.id.chip_reminder_5),
                findViewById(R.id.chip_reminder_10),
                findViewById(R.id.chip_reminder_15),
                findViewById(R.id.chip_reminder_30),
        };
        for (int i = 0; i < reminderChips.length; i++) {
            int minutes = REMINDER_LEAD_OPTIONS[i];
            reminderChips[i].setOnClickListener(v -> onReminderLeadPicked(minutes));
        }

        headerMaps = findViewById(R.id.header_maps);
        bodyMaps = findViewById(R.id.body_maps);
        chevronMaps = findViewById(R.id.chevron_maps);
        summaryMaps = findViewById(R.id.summary_maps);

        headerSemester = findViewById(R.id.header_semester);
        bodySemester = findViewById(R.id.body_semester);
        chevronSemester = findViewById(R.id.chevron_semester);
        summarySemester = findViewById(R.id.summary_semester);

        headerReminders = findViewById(R.id.header_reminders);
        bodyReminders = findViewById(R.id.body_reminders);
        chevronReminders = findViewById(R.id.chevron_reminders);
        summaryReminders = findViewById(R.id.summary_reminders);

        headerMaps.setOnClickListener(v -> toggleSection(bodyMaps, chevronMaps));
        headerSemester.setOnClickListener(v -> toggleSection(bodySemester, chevronSemester));
        headerReminders.setOnClickListener(v -> toggleSection(bodyReminders, chevronReminders));

        refreshReminderChips();

        Button pickFileBtn = findViewById(R.id.btn_pick_file);
        Button parseBtn = findViewById(R.id.btn_parse);
        Button clearBtn = findViewById(R.id.btn_clear);
        Button viewHistoryBtn = findViewById(R.id.btn_view_history);
        Button doneBtn = findViewById(R.id.btn_done);
        Button clearDatesBtn = findViewById(R.id.btn_semester_clear);

        inputCampus.setText(SettingsStore.getCampusHint(this));
        inputCampus.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateMapsSummary(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateMapsSummary();

        pickFileBtn.setOnClickListener(v -> filePicker.launch(new String[]{"text/html", "text/calendar", "*/*"}));
        parseBtn.setOnClickListener(v -> handleParse(htmlInput.getText().toString()));
        viewHistoryBtn.setOnClickListener(v -> startActivity(new Intent(this, ScheduleHistoryActivity.class)));

        clearBtn.setOnClickListener(v -> {
            ScheduleHistoryStore.archive(this, ScheduleStore.load(this));
            ScheduleStore.clear(this);
            htmlInput.setText("");
            showStatus("Cleared saved schedule. Find it under Saved Schedules if you need it back.", true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
        });

        switchEditMode.setOnCheckedChangeListener((btn, checked) ->
                editClassList.setVisibility(checked ? View.VISIBLE : View.GONE));

        refreshSemesterDateViews();

        btnSemesterStart.setOnClickListener(v -> pickDate(SettingsStore.getSemesterStart(this), picked -> {
            SettingsStore.setSemesterStart(this, picked);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
        }));

        btnSemesterEnd.setOnClickListener(v -> pickDate(SettingsStore.getSemesterEnd(this), picked -> {
            SettingsStore.setSemesterEnd(this, picked);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
        }));

        clearDatesBtn.setOnClickListener(v -> {
            SettingsStore.setSemesterStart(this, null);
            SettingsStore.setSemesterEnd(this, null);
            SettingsStore.setLastAutoArchivedEnd(this, null);
            refreshSemesterDateViews();
            WidgetRefreshScheduler.updateAllWidgets(this);
            Toast.makeText(this, "Semester dates cleared.", Toast.LENGTH_SHORT).show();
        });

        doneBtn.setOnClickListener(v -> {
            SettingsStore.setCampusHint(this, inputCampus.getText().toString().trim());
            finishWithUpdate();
            finish();
        });

        boolean expandMaps = !SettingsStore.getCampusHint(this).trim().isEmpty();
        boolean expandSemester = SettingsStore.getSemesterStart(this) != null || SettingsStore.getSemesterEnd(this) != null;
        boolean expandReminders = SettingsStore.getReminderLeadMinutes(this) > 0;
        setSectionExpanded(bodyMaps, chevronMaps, expandMaps);
        setSectionExpanded(bodySemester, chevronSemester, expandSemester);
        setSectionExpanded(bodyReminders, chevronReminders, expandReminders);

        if (!SettingsStore.hasAcceptedTerms(this)) {
            showTermsGate();
        }
    }

    /** Expands/collapses a settings card body on header tap, animating the transition and flipping its chevron. */
    private void toggleSection(View body, ImageView chevron) {
        ViewGroup parent = (ViewGroup) body.getParent();
        if (parent != null) TransitionManager.beginDelayedTransition(parent);
        setSectionExpanded(body, chevron, body.getVisibility() != View.VISIBLE);
    }

    private void setSectionExpanded(View body, ImageView chevron, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(expanded ? 180f : 0f);
    }

    private void updateMapsSummary() {
        String v = inputCampus.getText().toString().trim();
        summaryMaps.setText(v.isEmpty() ? "Not set" : v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<ClassSession> existing = ScheduleStore.load(this);
        statusText.setTextColor(ContextCompat.getColor(this, R.color.ink_dim));
        statusText.setText(existing.isEmpty() ? "" : existing.size() + " classes currently loaded.");
        renderEditList();
    }

    /** Shown once, before the rest of the screen can be used. Declining cancels setup. */
    private void showTermsGate() {
        View termsView = LayoutInflater.from(this).inflate(R.layout.dialog_terms, null);
        new AlertDialog.Builder(this)
                .setView(termsView)
                .setCancelable(false)
                .setPositiveButton(R.string.terms_agree, (d, w) ->
                        SettingsStore.setAcceptedTerms(this, true))
                .setNegativeButton(R.string.terms_decline, (d, w) -> {
                    Toast.makeText(this, "You need to accept the terms to use this widget.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .show();
    }

    /** Reflects the stored semester start/end into the buttons and the ongoing switch. */
    private void refreshSemesterDateViews() {
        LocalDate start = SettingsStore.getSemesterStart(this);
        LocalDate end = SettingsStore.getSemesterEnd(this);
        boolean ongoing = end == null;

        btnSemesterStart.setText(start != null ? start.format(SETTINGS_DATE_FORMAT) : "Not set");

        // Detach first so setting the checked state here doesn't re-trigger the
        // listener and loop back into this method a second time.
        switchSemesterOngoing.setOnCheckedChangeListener(null);
        switchSemesterOngoing.setChecked(ongoing);
        switchSemesterOngoing.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                SettingsStore.setSemesterEnd(this, null);
                refreshSemesterDateViews();
                WidgetRefreshScheduler.updateAllWidgets(this);
            } else {
                // Turning "ongoing" off is meaningless without an end date to
                // turn it off *for* -- ask for one right away instead of
                // leaving the switch unchecked with nothing set, which used
                // to just snap back to "ongoing" on the next refresh with no
                // visible feedback about why.
                pickDate(SettingsStore.getSemesterEnd(this), picked -> {
                    SettingsStore.setSemesterEnd(this, picked);
                    refreshSemesterDateViews();
                    WidgetRefreshScheduler.updateAllWidgets(this);
                }, () -> refreshSemesterDateViews()); // cancelled -- snap back to "ongoing"
            }
        });

        btnSemesterEnd.setEnabled(!ongoing);
        btnSemesterEnd.setAlpha(ongoing ? 0.45f : 1f);
        btnSemesterEnd.setText(ongoing ? "Ongoing" : end.format(SETTINGS_DATE_FORMAT));

        if (summarySemester != null) {
            if (start == null && end == null) {
                summarySemester.setText("Not set");
            } else {
                String startLabel = start != null ? start.format(SETTINGS_DATE_FORMAT) : "Not set";
                String endLabel = ongoing ? "Ongoing" : end.format(SETTINGS_DATE_FORMAT);
                summarySemester.setText(startLabel + " \u2013 " + endLabel);
            }
        }
    }

    /** Opens a DatePickerDialog seeded at `current` (or today if unset), and hands back the picked date. */
    private void pickDate(LocalDate current, java.util.function.Consumer<LocalDate> onPicked) {
        pickDate(current, onPicked, null);
    }

    /**
     * Same as above, but also runs `onCancelled` if the dialog is dismissed
     * without a date being picked (back press, tapping outside, or the
     * negative button) -- DatePickerDialog's negative button just calls
     * dismiss() rather than cancel(), so a plain OnCancelListener alone
     * wouldn't catch it; a "did we actually get a date" flag is tracked
     * instead and checked on dismiss.
     */
    private void pickDate(LocalDate current, java.util.function.Consumer<LocalDate> onPicked, Runnable onCancelled) {
        LocalDate seed = current != null ? current : LocalDate.now();
        boolean[] picked = {false};
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            picked[0] = true;
            onPicked.accept(LocalDate.of(year, month + 1, dayOfMonth));
        }, seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth());
        if (onCancelled != null) {
            dialog.setOnDismissListener(d -> {
                if (!picked[0]) onCancelled.run();
            });
        }
        dialog.show();
    }

    // Class reminders

    private void refreshReminderChips() {
        int lead = SettingsStore.getReminderLeadMinutes(this);
        for (int i = 0; i < reminderChips.length; i++) {
            boolean active = REMINDER_LEAD_OPTIONS[i] == lead;
            reminderChips[i].setBackgroundResource(active ? R.drawable.chip_filled_bg : R.drawable.chip_outline_bg);
            reminderChips[i].setTextColor(ContextCompat.getColor(this, active ? R.color.bg : R.color.ink_dim));
        }
        if (summaryReminders != null) {
            summaryReminders.setText(lead > 0 ? lead + " min before class" : "Off");
        }
    }

    private void onReminderLeadPicked(int minutes) {
        SettingsStore.setReminderLeadMinutes(this, minutes);
        refreshReminderChips();
        if (minutes > 0) ensureReminderPermissions();
        // Also re-anchors ClassReminderScheduler against the new lead time.
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this,
                minutes > 0 ? "Reminders on \u00B7 " + minutes + " min before class" : "Reminders off",
                Toast.LENGTH_SHORT).show();
    }

    /** Requests whatever's missing for reminders to actually fire: POST_NOTIFICATIONS (33+) and, best-effort, exact-alarm scheduling (31+). */
    private void ensureReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    // No such settings screen on this OEM build -- reminders
                    // will still fire, just not necessarily to the exact minute.
                }
            }
        }
    }

    private void readFileIntoInput(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("empty stream");
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            String content = sb.toString();

            // A .ics file isn't the CRS page HTML this screen otherwise expects --
            // detect it by content (content providers don't always expose a
            // reliable file name/extension) and import it directly rather than
            // dropping raw ICS text into the HTML paste box.
            if (content.toUpperCase(Locale.US).contains("BEGIN:VCALENDAR")) {
                handleIcsImport(content);
            } else {
                htmlInput.setText(content);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't read that file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIcsImport(String ics) {
        try {
            IcsImporter.Result result = IcsImporter.parse(ics);

            List<ClassSession> outgoing = ScheduleStore.load(this);
            if (!outgoing.isEmpty() && !sameSchedule(outgoing, result.classes)) {
                ScheduleHistoryStore.archive(this, outgoing);
            }
            ScheduleStore.save(this, result.classes);

            String msg = "Imported " + result.classes.size() + " classes from calendar file"
                    + (result.skipped > 0 ? " \u00B7 " + result.skipped + " event(s) skipped" : "");
            showStatus(msg, true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
        } catch (IcsImporter.ParseException e) {
            if (e.reason == IcsImporter.ParseException.Reason.NO_CALENDAR) {
                showStatus("That doesn't look like a calendar (.ics) file.", false);
            } else {
                showStatus("Found the calendar file but couldn't read any events from it.", false);
            }
        } catch (Exception e) {
            showStatus("Something went wrong reading that calendar file.", false);
        }
    }

    private void handleParse(String html) {
        if (html == null || html.trim().isEmpty()) {
            showStatus("Paste the page source or choose a file first.", false);
            return;
        }
        try {
            ScheduleParser.Result result = ScheduleParser.parse(html);

            List<ClassSession> outgoing = ScheduleStore.load(this);
            if (!outgoing.isEmpty() && !sameSchedule(outgoing, result.classes)) {
                ScheduleHistoryStore.archive(this, outgoing);
            }
            ScheduleStore.save(this, result.classes);

            // auto-fill the campus field the first time, if we can sniff one out
            if (inputCampus.getText().toString().trim().isEmpty()) {
                String detected = ScheduleParser.detectCampusHint(html);
                if (detected != null) {
                    inputCampus.setText(detected);
                    SettingsStore.setCampusHint(this, detected);
                }
            }

            double units = 0;
            for (ClassSession c : result.classes) units += c.credits;
            String msg = "Loaded " + result.classes.size() + " classes \u00B7 "
                    + String.format(Locale.US, "%.1f", units) + " units"
                    + (result.skipped > 0 ? " \u00B7 " + result.skipped + " row(s) skipped" : "");

            showStatus(msg, true);
            renderEditList();
            WidgetRefreshScheduler.updateAllWidgets(this);
        } catch (ScheduleParser.ParseException e) {
            if (e.reason == ScheduleParser.ParseException.Reason.NO_TABLE) {
                showStatus("Couldn't find the Enlisted Classes table \u2014 make sure you copied the schedule page, not another CRS page.", false);
            } else {
                showStatus("Found the table but couldn't read any class rows.", false);
            }
        } catch (Exception e) {
            showStatus("Something went wrong reading that file.", false);
        }
    }

    /** True if two schedules serialize identically -- used to skip archiving a no-op re-import. */
    private boolean sameSchedule(List<ClassSession> a, List<ClassSession> b) {
        if (a.size() != b.size()) return false;
        try {
            org.json.JSONArray arrA = new org.json.JSONArray();
            for (ClassSession c : a) arrA.put(c.toJson());
            org.json.JSONArray arrB = new org.json.JSONArray();
            for (ClassSession c : b) arrB.put(c.toJson());
            return arrA.toString().equals(arrB.toString());
        } catch (org.json.JSONException e) {
            return false;
        }
    }

    // Edit Class Info

    private void renderEditList() {
        editClassList.removeAllViews();
        List<ClassSession> schedule = ScheduleStore.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ClassSession c : schedule) {
            View row = inflater.inflate(R.layout.row_edit_class, editClassList, false);
            ((TextView) row.findViewById(R.id.edit_row_name)).setText(c.name);
            String daysLabel = daysToLabel(c.days);
            String meta = daysLabel + " \u00B7 " + WidgetRenderer.minToLabel(c.start)
                    + "\u2013" + WidgetRenderer.minToLabel(c.end) + " \u00B7 " + c.displayRoom()
                    + " \u00B7 " + String.format(Locale.US, "%.1f", c.credits) + " units";
            ((TextView) row.findViewById(R.id.edit_row_meta)).setText(meta);
            row.setOnClickListener(v -> showEditDialog(c));
            editClassList.addView(row);
        }
    }

    private String daysToLabel(List<Integer> days) {
        String[] letters = {"Su", "M", "T", "W", "Th", "F", "S"};
        StringBuilder sb = new StringBuilder();
        for (int d : days) sb.append(letters[d]);
        return sb.toString();
    }

    private void showEditDialog(ClassSession c) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_class, null);
        ((TextView) dialogView.findViewById(R.id.dialog_edit_class_name)).setText(c.name);

        EditText roomInput = dialogView.findViewById(R.id.dialog_input_room);
        EditText instructorInput = dialogView.findViewById(R.id.dialog_input_instructor);
        EditText unitsInput = dialogView.findViewById(R.id.dialog_input_units);

        roomInput.setText(c.room != null && !c.room.equalsIgnoreCase("TBA") ? c.room : "");
        instructorInput.setText(c.instructor != null ? c.instructor : "");
        unitsInput.setText(String.format(Locale.US, "%.1f", c.credits));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("SAVE", (d, w) -> {
                    String newRoom = roomInput.getText().toString().trim();
                    String newInstructor = instructorInput.getText().toString().trim();
                    double newUnits;
                    try {
                        newUnits = Double.parseDouble(unitsInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        newUnits = c.credits; // keep the original value if the field was left blank/invalid
                    }
                    applyEdit(c, newRoom, newInstructor, newUnits);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void applyEdit(ClassSession original, String newRoom, String newInstructor, double newUnits) {
        List<ClassSession> schedule = ScheduleStore.load(this);
        List<ClassSession> updated = new ArrayList<>();
        for (ClassSession c : schedule) {
            if (c.code.equals(original.code)) {
                updated.add(c.withEditedInfo(newRoom.isEmpty() ? "TBA" : newRoom, newInstructor, newUnits));
            } else {
                updated.add(c);
            }
        }
        ScheduleStore.save(this, updated);
        renderEditList();
        WidgetRefreshScheduler.updateAllWidgets(this);
        Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
    }

    // Shared

    private void showStatus(String msg, boolean ok) {
        statusText.setText((ok ? "\u2713 " : "\u2715 ") + msg);
        statusText.setTextColor(ok ? 0xFF2ED573 : 0xFFFF2E17);
    }

    private void finishWithUpdate() {
        WidgetRefreshScheduler.updateAllWidgets(this);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(Activity.RESULT_OK, result);
        }
    }
}
