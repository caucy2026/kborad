package org.fcitx.fcitx5.android.input

import android.view.inputmethod.CursorAnchorInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import sun.misc.Unsafe

/** Exercises the real callback without creating an Android window on the host JVM. */
class LateCursorCallbackTest {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
        it.isAccessible = true
        it.get(null) as Unsafe
    }

    private fun service(released: Boolean): FcitxInputMethodService {
        // Bypass framework constructors; a retired service has no usable window references.
        val service = unsafe.allocateInstance(FcitxInputMethodService::class.java) as FcitxInputMethodService
        FcitxInputMethodService::class.java.getDeclaredField("ownedResourcesReleased").apply {
            isAccessible = true
            setBoolean(service, released)
        }
        return service
    }

    @Test
    fun lateCursorCallbackAfterReleaseDoesNotAccessWindowOrAnchor() {
        val info = unsafe.allocateInstance(CursorAnchorInfo::class.java) as CursorAnchorInfo
        service(true).onUpdateCursorAnchorInfo(info)
    }

    @Test
    fun cursorCallbackWithoutWindowDoesNotAccessAnchor() {
        val info = unsafe.allocateInstance(CursorAnchorInfo::class.java) as CursorAnchorInfo
        service(false).onUpdateCursorAnchorInfo(info)
    }

    @Test
    fun measuringMissingWindowReportsUnavailable() {
        val method = FcitxInputMethodService::class.java.getDeclaredMethod("updateDecorLocation")
        method.isAccessible = true
        assertEquals(false, method.invoke(service(false)))
    }
}
