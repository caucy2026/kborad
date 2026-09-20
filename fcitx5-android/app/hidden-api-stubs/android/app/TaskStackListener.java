package android.app;

/** Compile-only Android 12 hidden API signature. The device boot classpath provides the runtime. */
public abstract class TaskStackListener {
    public TaskStackListener() {}
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {}
    public void onActivityRestartAttempt(
            ActivityManager.RunningTaskInfo task,
            boolean homeTaskVisible,
            boolean clearedTask,
            boolean wasVisible) {}
}
