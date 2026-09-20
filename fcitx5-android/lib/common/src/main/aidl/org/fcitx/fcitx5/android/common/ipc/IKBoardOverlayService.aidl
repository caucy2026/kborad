package org.fcitx.fcitx5.android.common.ipc;

import org.fcitx.fcitx5.android.common.ipc.IKBoardOverlayCallback;

interface IKBoardOverlayService {
    /** Bit mask: 1=bidirectional overlay, 2=local composition with final commit relay, 4=remote mouse relay. */
    int getCapabilities();
    /** Shows KBoard at the bottom of targetDisplayId while sourceDisplayId keeps focus. */
    boolean show(long requestId, String sessionId, int sourceDisplayId, int targetDisplayId, int targetWidth, int targetHeight, int keyboardHeight, IKBoardOverlayCallback callback);
    void hide(long requestId, String sessionId);
}
