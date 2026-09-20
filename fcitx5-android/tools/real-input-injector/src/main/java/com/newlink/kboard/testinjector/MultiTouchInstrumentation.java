package com.newlink.kboard.testinjector;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Test-only parameterized multi-pointer stream injector. */
public final class MultiTouchInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? Bundle.EMPTY : args;
        start();
    }

    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            int displayId = integer("display", 0);
            long holdMs = number("holdMs", 120L);
            long stepMs = number("stepMs", 24L);
            int rounds = integer("rounds", 1);
            List<Point> points = parsePoints(arguments.getString("points", ""));
            if (points.isEmpty()) throw new IllegalArgumentException("points is required: id:x:y,id:x:y");
            JSONArray runs = new JSONArray();
            boolean allAccepted = true;
            UiAutomation automation = getUiAutomation();
            for (int round = 0; round < rounds; round++) {
                JSONObject run = inject(automation, displayId, points, holdMs, stepMs, round);
                runs.put(run);
                allAccepted &= run.getBoolean("accepted");
            }
            JSONObject result = new JSONObject();
            result.put("schema", 1);
            result.put("displayId", displayId);
            result.put("holdMs", holdMs);
            result.put("stepMs", stepMs);
            result.put("rounds", rounds);
            result.put("points", pointJson(points));
            result.put("accepted", allAccepted);
            result.put("runs", runs);
            output.putString("resultJson", result.toString());
            finish(allAccepted ? -1 : 1, output);
        } catch (Throwable error) {
            JSONObject result = new JSONObject();
            try {
                result.put("schema", 1);
                result.put("accepted", false);
                result.put("errorClass", error.getClass().getName());
                result.put("error", String.valueOf(error.getMessage()));
            } catch (Exception ignored) { }
            output.putString("resultJson", result.toString());
            finish(1, output);
        }
    }

    private JSONObject inject(UiAutomation automation, int displayId, List<Point> points,
                              long holdMs, long stepMs, int round) throws Exception {
        long downTime = SystemClock.uptimeMillis();
        boolean accepted = send(automation, event(downTime, MotionEvent.ACTION_DOWN, points, 1, displayId));
        for (int i = 1; i < points.size(); i++) {
            SystemClock.sleep(stepMs);
            int action = MotionEvent.ACTION_POINTER_DOWN |
                (i << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            accepted &= send(automation, event(downTime, action, points, i + 1, displayId));
        }
        SystemClock.sleep(holdMs);
        for (int i = points.size() - 1; i >= 1; i--) {
            int action = MotionEvent.ACTION_POINTER_UP |
                (i << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            accepted &= send(automation, event(downTime, action, points, i + 1, displayId));
            SystemClock.sleep(stepMs);
        }
        accepted &= send(automation, event(downTime, MotionEvent.ACTION_UP, points, 1, displayId));
        JSONObject row = new JSONObject();
        row.put("round", round);
        row.put("downTime", downTime);
        row.put("endTime", SystemClock.uptimeMillis());
        row.put("accepted", accepted);
        return row;
    }

    private boolean send(UiAutomation automation, MotionEvent event) {
        try { return automation.injectInputEvent(event, true); }
        finally { event.recycle(); }
    }

    private MotionEvent event(long downTime, int action, List<Point> points,
                              int count, int displayId) throws Exception {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            Point point = points.get(i);
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = point.id;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = point.x;
            coordinates[i].y = point.y;
            coordinates[i].pressure = 1f;
            coordinates[i].size = 1f;
        }
        MotionEvent event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, count, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        );
        setDisplayId(event, displayId);
        return event;
    }

    /** Android 12 vendor builds expose this hidden method on different runtime classes. */
    private void setDisplayId(MotionEvent event, int displayId) throws Exception {
        ReflectiveOperationException last = null;
        for (Class<?> owner : new Class<?>[] { event.getClass(), MotionEvent.class, InputEvent.class }) {
            try {
                Method setter;
                try {
                    setter = owner.getMethod("setDisplayId", int.class);
                } catch (NoSuchMethodException ignored) {
                    setter = owner.getDeclaredMethod("setDisplayId", int.class);
                }
                setter.setAccessible(true);
                setter.invoke(event, displayId);
                return;
            } catch (ReflectiveOperationException error) {
                last = error;
            }
        }
        // A few Android 12 vendor frameworks retain the parcel field but strip the Java setter.
        for (Class<?> owner = event.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                java.lang.reflect.Field field = owner.getDeclaredField("mDisplayId");
                field.setAccessible(true);
                field.setInt(event, displayId);
                return;
            } catch (ReflectiveOperationException error) {
                last = error;
            }
        }
        throw new NoSuchMethodException(
            "No MotionEvent displayId setter or mDisplayId field; last=" + last
        );
    }

    private List<Point> parsePoints(String value) {
        List<Point> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String item : value.split(",")) {
            String[] parts = item.trim().split(":");
            if (parts.length != 3) throw new IllegalArgumentException("bad point: " + item);
            result.add(new Point(Integer.parseInt(parts[0]),
                Float.parseFloat(parts[1]), Float.parseFloat(parts[2])));
        }
        return result;
    }

    private JSONArray pointJson(List<Point> points) throws Exception {
        JSONArray array = new JSONArray();
        for (Point point : points) {
            JSONObject row = new JSONObject();
            row.put("id", point.id); row.put("x", point.x); row.put("y", point.y);
            array.put(row);
        }
        return array;
    }

    private int integer(String key, int fallback) {
        String value = arguments.getString(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private long number(String key, long fallback) {
        String value = arguments.getString(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    private static final class Point {
        final int id; final float x; final float y;
        Point(int id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }
}
