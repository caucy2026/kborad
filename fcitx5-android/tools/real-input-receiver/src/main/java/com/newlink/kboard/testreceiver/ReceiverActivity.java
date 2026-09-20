package com.newlink.kboard.testreceiver;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.json.JSONObject;

/** Test-only served editor. Records key metadata and commit counts, never committed text. */
public final class ReceiverActivity extends Activity {
    private EventView editor;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        editor = new EventView(this);
        setContentView(editor);
    }

    @Override public void onResume() {
        super.onResume();
        if (getIntent().getBooleanExtra("clear", true)) editor.clearEvents();
        editor.requestFocus();
        editor.post(() -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            imm.restartInput(editor);
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private static final class EventView extends TextView {
        private final File eventFile;

        EventView(Context context) {
            super(context);
            eventFile = new File(context.getFilesDir(), "events.jsonl");
            setFocusableInTouchMode(true);
            setTextSize(22f);
            setTextColor(Color.BLACK);
            setBackgroundColor(Color.WHITE);
            setPadding(32, 32, 32, 32);
            setText("KBoard independent InputConnection receiver\n" + eventFile);
        }

        void clearEvents() {
            try (FileWriter ignored = new FileWriter(eventFile, false)) { }
            catch (IOException error) { throw new IllegalStateException(error); }
        }

        @Override public boolean onCheckIsTextEditor() { return true; }

        @Override public InputConnection onCreateInputConnection(EditorInfo out) {
            out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            out.packageName = getContext().getPackageName();
            return new BaseInputConnection(this, false) {
                @Override public boolean sendKeyEvent(KeyEvent event) {
                    boolean accepted = super.sendKeyEvent(event);
                    JSONObject row = base("key");
                    put(row, "action", event.getAction());
                    put(row, "keyCode", event.getKeyCode());
                    put(row, "metaState", event.getMetaState());
                    put(row, "repeatCount", event.getRepeatCount());
                    put(row, "downTime", event.getDownTime());
                    put(row, "eventTime", event.getEventTime());
                    put(row, "accepted", accepted);
                    append(row);
                    return accepted;
                }

                @Override public boolean commitText(CharSequence text, int cursor) {
                    JSONObject row = base("commitText");
                    put(row, "length", text == null ? 0 : text.length());
                    put(row, "cursor", cursor);
                    append(row);
                    return true;
                }

                @Override public boolean setComposingText(CharSequence text, int cursor) {
                    JSONObject row = base("setComposingText");
                    put(row, "length", text == null ? 0 : text.length());
                    put(row, "cursor", cursor);
                    append(row);
                    return true;
                }
            };
        }

        private JSONObject base(String type) {
            JSONObject row = new JSONObject();
            put(row, "type", type);
            put(row, "elapsedRealtime", SystemClock.elapsedRealtime());
            put(row, "displayId", getDisplay() == null ? -1 : getDisplay().getDisplayId());
            return row;
        }

        private static void put(JSONObject row, String name, Object value) {
            try { row.put(name, value); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }

        private void append(JSONObject row) {
            try (FileWriter writer = new FileWriter(eventFile, true)) {
                writer.write(row.toString());
                writer.write('\n');
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }
    }
}
