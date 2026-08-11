/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.core.Fcitx
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

class FcitxTest {

    private companion object {

        lateinit var fcitx: Fcitx
        val fcitxEventChannel = Channel<FcitxEvent<*>>(capacity = Channel.CONFLATED)
        val scope = MainScope()

        @BeforeClass
        @JvmStatic
        fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fcitx = Fcitx(context)

            // forward to our channel for point to point consuming
            fcitx.eventFlow
                .onEach { fcitxEventChannel.send(it) }
                .launchIn(scope)
            fcitx.start()

            // wait fcitx started
            runBlocking {
                receiveFirst<FcitxEvent.ReadyEvent>()
                fcitx.activate(context.applicationInfo.uid, context.packageName)
                fcitx.focus()
                fcitx.setEnabledIme(arrayOf("pinyin"))
                fcitx.setGlobalConfig(
                    RawConfig(
                        arrayOf(
                            RawConfig(
                                "Behavior", arrayOf(
                                    RawConfig("ShowInputMethodInformation", false)
                                )
                            )
                        )
                    )
                )
            }
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            fcitx.stop()
        }

        private suspend fun sendString(str: String) {
            str.forEach { c ->
                fcitx.sendKey(c)
                delay(50)
            }
        }

        private suspend fun enableAndActivateIme(ime: String) {
            fcitx.setEnabledIme(arrayOf(ime))
            fcitx.activateIme(ime)
            withTimeout(10_000) {
                while (fcitx.currentIme().uniqueName != ime) {
                    delay(20)
                }
            }
        }

        private suspend inline fun <reified T : FcitxEvent<*>> receiveFirst(): T? =
            fcitxEventChannel.receiveAsFlow().mapNotNull { it as? T }.firstOrNull()

        private suspend fun receiveFirstCandidateList() =
            receiveFirst<FcitxEvent.CandidateListEvent>()

        private suspend fun receiveFirstCommitString() =
            receiveFirst<FcitxEvent.CommitStringEvent>()

        private suspend fun receiveFirstPreedit() = receiveFirst<FcitxEvent.ClientPreeditEvent>()

        private suspend fun receiveFirstInputPanelAux() =
            receiveFirst<FcitxEvent.InputPanelEvent>()

    }

    private var enabledIme: List<String> = listOf()

    @Before
    fun saveEnabledIME() = runBlocking {
        enabledIme = fcitx.enabledIme().map { it.uniqueName }
    }

    @After
    fun restoreEnabledIME() = runBlocking {
        fcitx.setEnabledIme(enabledIme.toTypedArray())
    }

    @Test
    fun testWbx(): Unit = runBlocking {
        enableAndActivateIme("wbx")
        sendString("wqvb")
        val expected = "你好"
        fcitx.select(0)
        val commitString = receiveFirstCommitString()?.data?.text
        Timber.i("commitString is $commitString")
        Assert.assertEquals(expected, commitString)
        fcitx.reset()
    }

    @Test
    fun testPinyin(): Unit = runBlocking {
        enableAndActivateIme("pinyin")
        sendString("nihaoshijie")
        val expected = "你好世界"
        val candidates = fcitx.getCandidates(0, 16)
        val expectedRank = candidates.indexOfFirst { it.text == expected }
        Assert.assertTrue("$expected is missing from top 16", expectedRank >= 0)
        fcitx.select(expectedRank)
        val commitString = receiveFirstCommitString()?.data?.text
        Timber.i("commitString is $commitString")
        Assert.assertEquals(expected, commitString)
        fcitx.reset()
    }

    @Test
    fun testPinyinFastPathCandidateQuality(): Unit = runBlocking {
        enableAndActivateIme("pinyin")
        // Exclude one-time dictionary/model loading from the key-path timing.
        fcitx.sendKey('a')
        fcitx.getCandidates(0, 16)
        fcitx.reset()

        val cases = listOf(
            Triple("nihaoshijie", "你好世界", false),
            Triple("zhonghuarenmingongheguo", "中华人民共和国", true),
            Triple("beijing", "北京", false),
            Triple("shanghai", "上海", false),
            Triple("zhongguo", "中国", false),
            Triple("woaibeijing", "我爱北京", false),
            Triple("jintiantianqihenhao", "今天天气很好", false)
        )
        cases.forEach { (input, expected, requireFirst) ->
            fcitx.reset()
            val start = SystemClock.elapsedRealtimeNanos()
            val keyTimes = input.map { key ->
                val keyStart = SystemClock.elapsedRealtimeNanos()
                fcitx.sendKey(key)
                key to (SystemClock.elapsedRealtimeNanos() - keyStart) / 1_000_000.0
            }
            val elapsedMs =
                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            val candidates = fcitx.getCandidates(0, 16)
            Timber.i(
                "Pinyin fast path: input=%s keys=%d elapsed=%.2fms first=%s",
                input,
                input.length,
                elapsedMs,
                candidates.firstOrNull()?.text
            )
            Timber.i(
                "Pinyin key path: %s",
                keyTimes.joinToString(" ") { (key, duration) ->
                    "$key=${"%.2f".format(duration)}ms"
                }
            )
            val expectedRank = candidates.indexOfFirst { it.text == expected }
            Assert.assertTrue("$expected is missing from top 16", expectedRank >= 0)
            if (requireFirst) {
                Assert.assertEquals("$expected must remain first", 0, expectedRank)
            }
        }
        fcitx.reset()
    }

    @Test
    fun testInputPanelStatus(): Unit = runBlocking {
        enableAndActivateIme("pinyin")
        fcitx.reset()
        Timber.i("after first reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
        fcitx.sendKey('a')
        Timber.i("after sending 'a': ${fcitx.isEmpty()}")
        Assert.assertEquals(false, fcitx.isEmpty())
        fcitx.reset()
        Timber.i("after second reset: ${fcitx.isEmpty()}")
        Assert.assertEquals(true, fcitx.isEmpty())
    }

}
