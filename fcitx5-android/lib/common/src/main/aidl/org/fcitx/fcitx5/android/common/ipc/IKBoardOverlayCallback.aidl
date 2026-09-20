package org.fcitx.fcitx5.android.common.ipc;

import android.os.Bundle;

oneway interface IKBoardOverlayCallback {
    /** Emitted once, after the virtual editor is served and KBoard has drawn visible frames. */
    void onReady(long requestId, String sessionId, int virtualDisplayId);
    /** operation: commit/delete/deleteCodePoints/key/editorAction/privateCommand/selection.
        Composition stays local to the private editor; only its final commit is relayed. */
    void onInput(long requestId, String sessionId, String operation, String text, int arg1, int arg2, in Bundle extras);
    /** reason: 1 hidden, 2 replaced, 3 owner died, 4 display removed, 5 surface lost,
        6 start failed, 7 service stopped, 8 ready timeout, 9 regular input took focus. */
    void onClosed(long requestId, String sessionId, int reason);
}
