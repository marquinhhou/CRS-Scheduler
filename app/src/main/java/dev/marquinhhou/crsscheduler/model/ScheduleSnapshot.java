package dev.marquinhhou.crsscheduler.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A previously-active schedule kept in history after being replaced (see
 * ScheduleHistoryStore), so importing a new CRS page or hitting Clear doesn't
 * silently throw the old one away.
 */
public class ScheduleSnapshot {

    public final long id;
    public final String label;          // auto-generated "date · N classes · X.X units"
    public final String customName;     // user-set rename, or null if never renamed
    public final List<ClassSession> classes;

    public ScheduleSnapshot(long id, String label, String customName, List<ClassSession> classes) {
        this.id = id;
        this.label = label;
        this.customName = customName;
        this.classes = classes;
    }

    /** Convenience for the common case of archiving with no custom name yet. */
    public ScheduleSnapshot(long id, String label, List<ClassSession> classes) {
        this(id, label, null, classes);
    }

    /** What to actually show in the UI -- the user's rename if they set one, else the auto-generated label. */
    public String displayName() {
        return (customName != null && !customName.trim().isEmpty()) ? customName : label;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label);
        if (customName != null) o.put("customName", customName);
        JSONArray arr = new JSONArray();
        for (ClassSession c : classes) arr.put(c.toJson());
        o.put("classes", arr);
        return o;
    }

    public static ScheduleSnapshot fromJson(JSONObject o) throws JSONException {
        List<ClassSession> classes = new ArrayList<>();
        JSONArray arr = o.getJSONArray("classes");
        for (int i = 0; i < arr.length(); i++) {
            classes.add(ClassSession.fromJson(arr.getJSONObject(i)));
        }
        String customName = o.has("customName") ? o.getString("customName") : null;
        return new ScheduleSnapshot(o.getLong("id"), o.getString("label"), customName, classes);
    }
}
