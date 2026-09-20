package android.app;

/** Compile-only Android 12 hidden API signature. The device boot classpath provides the runtime. */
public class ActivityTaskManager {
    public static ActivityTaskManager getInstance() { throw new UnsupportedOperationException(); }
    public void registerTaskStackListener(TaskStackListener listener) { throw new UnsupportedOperationException(); }
    public void unregisterTaskStackListener(TaskStackListener listener) { throw new UnsupportedOperationException(); }
}
