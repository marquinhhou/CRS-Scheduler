package dev.marquinhhou.crsscheduler.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.res.ResourcesCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

import dev.marquinhhou.crsscheduler.R;
import dev.marquinhhou.crsscheduler.model.ClassSession;
import dev.marquinhhou.crsscheduler.widget.WidgetRenderer;

/**
 * Draws the week's schedule as a single image matching the widget's own look --
 * dark background, monospace type, dim dots marking free slots -- and saves it
 * to the device's Pictures gallery via MediaStore.
 *
 * Deliberately "plain": unlike the live widgets, this is a static snapshot
 * someone might save or share later, so it doesn't highlight "today" in red --
 * that distinction only makes sense at the moment it's generated and would just
 * be wrong/confusing by the time anyone actually looks at the saved image.
 * Class names ARE shown per cell (abbreviated the same way the widget does),
 * just without any day-specific color coding -- every occupied cell reads the
 * same regardless of which day it falls on.
 *
 * Reuses WidgetRenderer's grid math (weekBreakpoints/activeWeekDays/
 * occupiedRowIndices/findClassInSlot/abbreviateName/compactRangeLabel) so the
 * exported table always matches what the widget itself considers "the
 * schedule" -- including which day columns and time-slot rows it bothers to
 * show -- only the drawing/rendering step here differs.
 */
public final class ScheduleImageExporter {

    private static final int PADDING = 28;
    private static final int HEADER_BAR_HEIGHT = 60;
    private static final int HEADLINE_HEIGHT = 50;
    private static final int GRID_ROW_HEIGHT = 50;
    private static final int TIME_COL_WIDTH = 130;
    private static final int DAY_COL_WIDTH = 130;
    private static final int NOTE_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 46;

    private static final int COLOR_BG = Color.parseColor("#000000");
    private static final int COLOR_SURFACE = Color.parseColor("#0D0D0D");
    private static final int COLOR_GRID_LINE = Color.parseColor("#1AFFFFFF");
    private static final int COLOR_INK = Color.parseColor("#F3F2ED");
    private static final int COLOR_INK_DIM = Color.parseColor("#8B8B86");
    private static final int COLOR_RED = Color.parseColor("#FF2E17");

    private ScheduleImageExporter() {}

    /** Renders the full week as a table image. Returns null if there's nothing to draw. */
    public static Bitmap render(Context context, List<ClassSession> schedule) {
        if (schedule.isEmpty()) return null;

        List<Integer> breakpoints = WidgetRenderer.weekBreakpoints(schedule);
        List<Integer> activeDays = WidgetRenderer.activeWeekDays(schedule);
        List<Integer> occupiedRows = WidgetRenderer.occupiedRowIndices(schedule, breakpoints, activeDays);
        int rows = occupiedRows.size();
        int dayCols = activeDays.size();
        if (rows == 0 || dayCols == 0) return null;

        String noClassDays = WidgetRenderer.noClassDaysLabel(activeDays);
        int noteHeight = noClassDays.isEmpty() ? 0 : NOTE_HEIGHT;

        Typeface mono = font(context, R.font.jetbrains_mono);
        Typeface monoBold = font(context, R.font.jetbrains_mono_bold);

        int width = PADDING * 2 + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols;
        int height = PADDING * 2 + HEADER_BAR_HEIGHT + HEADLINE_HEIGHT + GRID_ROW_HEIGHT * (rows + 1)
                + noteHeight + FOOTER_HEIGHT;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(COLOR_BG);

        Paint brandPaint = textPaint(20, COLOR_INK_DIM, Paint.Align.LEFT, mono);
        Paint headlinePaint = textPaint(26, COLOR_INK, Paint.Align.LEFT, monoBold);
        Paint dayLabelPaint = textPaint(20, COLOR_INK_DIM, Paint.Align.CENTER, monoBold);
        Paint timeTextPaint = textPaint(16, COLOR_INK_DIM, Paint.Align.CENTER, mono);
        Paint cellTextPaint = textPaint(17, COLOR_INK, Paint.Align.CENTER, monoBold);
        Paint notePaint = textPaint(16, COLOR_INK_DIM, Paint.Align.LEFT, mono);
        Paint footerPaint = textPaint(16, COLOR_INK_DIM, Paint.Align.CENTER, mono);
        Paint gridPaint = new Paint();
        gridPaint.setColor(COLOR_GRID_LINE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        Paint tablePaint = new Paint();
        tablePaint.setColor(COLOR_SURFACE);
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(COLOR_INK_DIM);
        dotPaint.setAlpha(90);

        double totalUnits = 0;
        for (ClassSession c : schedule) if (!c.creditsExcluded) totalUnits += c.credits;

        int y = PADDING;

        // brand row, matching the widget header's style (the red dot is just
        // branding here, not a "today" indicator)
        Paint brandDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandDotPaint.setColor(COLOR_RED);
        canvas.drawCircle(PADDING + 6, y + HEADER_BAR_HEIGHT / 2f - 6, 6, brandDotPaint);
        canvas.drawText("WEEKLY", PADDING + 20, y + HEADER_BAR_HEIGHT / 2f, brandPaint);
        y += HEADER_BAR_HEIGHT;

        // headline: "N classes · X.X units"
        String headline = schedule.size() + (schedule.size() == 1 ? " class" : " classes")
                + " \u00B7 " + String.format(Locale.US, "%.1f", totalUnits) + " units";
        canvas.drawText(headline, PADDING, y + HEADLINE_HEIGHT / 2f + 9, headlinePaint);
        y += HEADLINE_HEIGHT;

        int tableTop = y;
        int tableBottom = y + GRID_ROW_HEIGHT * (rows + 1);
        canvas.drawRoundRect(PADDING, tableTop, PADDING + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols, tableBottom,
                24f, 24f, tablePaint);

        // header row: blank corner + one day letter per day that actually has a
        // class this week (empty days, e.g. weekends for most students, are
        // dropped rather than shown as an all-dot column -- see
        // WidgetRenderer.activeWeekDays())
        for (int i = 0; i < dayCols; i++) {
            int dayIdx = activeDays.get(i);
            float cx = PADDING + TIME_COL_WIDTH + i * DAY_COL_WIDTH + DAY_COL_WIDTH / 2f;
            float cy = y + GRID_ROW_HEIGHT / 2f + 7;
            canvas.drawText(WidgetRenderer.DAY_LABELS[dayIdx].substring(0, 1), cx, cy, dayLabelPaint);
        }
        y += GRID_ROW_HEIGHT;

        // data rows: time range + the class abbreviation (or a dim dot if free) --
        // only for slots with a class somewhere (see WidgetRenderer.occupiedRowIndices())
        for (int i = 0; i < rows; i++) {
            int r = occupiedRows.get(i);
            int slotStart = breakpoints.get(r);
            int slotEnd = breakpoints.get(r + 1);
            float rowTop = y + i * GRID_ROW_HEIGHT;
            float rowMidY = rowTop + GRID_ROW_HEIGHT / 2f;

            canvas.drawLine(PADDING, rowTop, PADDING + TIME_COL_WIDTH + DAY_COL_WIDTH * dayCols, rowTop, gridPaint);
            drawFittedText(canvas, WidgetRenderer.compactRangeLabel(slotStart, slotEnd),
                    PADDING + TIME_COL_WIDTH / 2f, rowMidY + 6, TIME_COL_WIDTH - 12, timeTextPaint);

            for (int c = 0; c < dayCols; c++) {
                int dayIdx = activeDays.get(c);
                float cx = PADDING + TIME_COL_WIDTH + c * DAY_COL_WIDTH + DAY_COL_WIDTH / 2f;
                ClassSession match = WidgetRenderer.findClassInSlot(schedule, dayIdx, slotStart, slotEnd);

                if (match != null) {
                    String label = WidgetRenderer.abbreviateName(match.name);
                    drawFittedText(canvas, label, cx, rowMidY + 6, DAY_COL_WIDTH - 10, cellTextPaint);
                } else {
                    canvas.drawCircle(cx, rowMidY, 4, dotPaint);
                }
            }
        }

        // "No classes on: Sat, Sun" -- explains why those day columns aren't in
        // the grid above, rather than the grid just silently not accounting for
        // all 7 days. Skipped entirely when every day has a class.
        if (!noClassDays.isEmpty()) {
            canvas.drawText(noClassDays, PADDING, tableBottom + NOTE_HEIGHT / 2f + 6, notePaint);
        }

        // footer: fine print
        int footerY = tableBottom + noteHeight + FOOTER_HEIGHT / 2 + 6;
        canvas.drawText("CRS Scheduler by marquinhhou on GitHub", width / 2f, footerY, footerPaint);

        return bitmap;
    }

    /** Draws centered text, shrinking the font just enough to fit maxWidth if it'd otherwise overflow the cell. */
    private static void drawFittedText(Canvas canvas, String text, float cx, float baselineY, float maxWidth, Paint basePaint) {
        Paint p = new Paint(basePaint);
        float w = p.measureText(text);
        if (w > maxWidth && w > 0) {
            p.setTextSize(basePaint.getTextSize() * (maxWidth / w));
        }
        canvas.drawText(text, cx, baselineY, p);
    }

    private static Typeface font(Context context, int fontRes) {
        Typeface t = ResourcesCompat.getFont(context, fontRes);
        return t != null ? t : Typeface.MONOSPACE;
    }

    private static Paint textPaint(float textSize, int color, Paint.Align align, Typeface typeface) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(textSize);
        p.setColor(color);
        p.setTextAlign(align);
        p.setTypeface(typeface);
        return p;
    }

    /**
     * Saves the bitmap into the device's Pictures/CRSScheduler gallery folder.
     * Uses scoped storage (MediaStore, no permission needed) on Android 10+;
     * callers on older versions must hold WRITE_EXTERNAL_STORAGE first.
     */
    public static Uri saveToGallery(Context context, Bitmap bitmap) throws IOException {
        String displayName = "CRS_Schedule_" + System.currentTimeMillis() + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CRSScheduler");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IOException("MediaStore did not return a Uri to write to.");

        try (OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) throw new IOException("Could not open an output stream for the saved image.");
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Bitmap compression failed.");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(item, done, null, null);
        }

        return item;
    }
}
