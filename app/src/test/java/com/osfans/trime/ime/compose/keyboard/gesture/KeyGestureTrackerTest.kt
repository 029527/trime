/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.compose.keyboard.gesture

import androidx.compose.ui.geometry.Offset
import com.osfans.trime.ime.keyboard.KeyBehavior
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private class FakeClock(
    var time: Long = 1_000_000,
) : GestureClock {
    private class Task(
        val at: Long,
        val seq: Long,
        val action: () -> Unit,
    ) {
        var cancelled = false
    }

    private val tasks = mutableListOf<Task>()
    private var seq = 0L

    override fun now() = time

    override fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): GestureTimer {
        val task = Task(time + delayMillis, seq++, action)
        tasks += task
        return GestureTimer { task.cancelled = true }
    }

    fun advance(millis: Long) {
        val end = time + millis
        while (true) {
            tasks.removeAll { it.cancelled }
            val next = tasks.filter { it.at <= end }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            tasks.remove(next)
            time = next.at
            next.action()
        }
        time = end
    }
}

private data class TestConfig(
    override val swipeTravel: Int = 60,
    override val swipeVelocity: Int = 0,
    override val longPressTimeout: Int = 300,
    override val repeatInterval: Int = 30,
    override val doubleTapTimeout: Int = 300,
    override val slideStepSize: Int = 24,
    override val vibrateOnKeyPress: Boolean = true,
    override val vibrateOnKeyRelease: Boolean = true,
    override val vibrateOnKeyRepeat: Boolean = true,
) : KeyGestureConfig

/** A row of keys 100px wide and 100px tall. Events come out as short strings in the order they fire. */
private class Harness(
    caps: Map<Int, KeyCaps> = emptyMap(),
    config: KeyGestureConfig = TestConfig(),
    keyCount: Int = 4,
) {
    val clock = FakeClock()
    val events = mutableListOf<String>()

    private val target =
        object : KeyGestureTarget {
            override fun keyAt(
                x: Float,
                y: Float,
            ): Int = if (x >= 0 && y in 0f..<100f && x < keyCount * 100) (x / 100).toInt() else -1

            override fun capsOf(key: Int) = caps[key] ?: KeyCaps()

            override fun cellOrigin(key: Int) = Offset(key * 100f, 0f)
        }

    private val listener =
        object : KeyGestureListener {
            override fun onPress(key: Int) {
                events += "press $key"
            }

            override fun onSwipe(
                key: Int,
                behavior: KeyBehavior,
            ) {
                events += "swipe $key $behavior"
            }

            override fun onRelease(
                key: Int,
                behavior: KeyBehavior,
                fromLongPress: Boolean,
            ) {
                events += "release $key $behavior" + if (fromLongPress) " long" else ""
            }

            override fun onLongPress(key: Int) {
                events += "longPress $key"
            }

            override fun onMove(
                key: Int,
                x: Float,
                y: Float,
                longPressed: Boolean,
            ) {
                events += "move $key ${x.toInt()},${y.toInt()}" + if (longPressed) " long" else ""
            }

            override fun onSlide(
                key: Int,
                delta: Int,
            ) {
                events += "slide $key $delta"
            }

            override fun onCancel(key: Int) {
                events += "cancel $key"
            }
        }

    val tracker = KeyGestureTracker(target, listener, config, clock) { events += if (it) "vibrate long" else "vibrate" }

    fun take(): List<String> = events.toList().also { events.clear() }
}

class KeyGestureTrackerTest :
    StringSpec({

        "tap presses on down and clicks on up" {
            val h = Harness()
            h.tracker.down(1, 150f, 50f) shouldBe true
            h.take() shouldBe listOf("vibrate", "press 1")
            h.clock.advance(80)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "release 1 CLICK")
        }

        "pressing outside every key is ignored" {
            val h = Harness()
            h.tracker.down(1, 50f, 150f) shouldBe false
            h.tracker.up(1)
            h.events.shouldBeEmpty()
        }

        "vibration follows the preferences" {
            val h = Harness(config = TestConfig(vibrateOnKeyPress = false, vibrateOnKeyRelease = false))
            h.tracker.down(1, 50f, 50f)
            h.tracker.up(1)
            h.take() shouldBe listOf("press 0", "release 0 CLICK")
        }

        "swipes in four directions preview once and release with the direction" {
            listOf(
                Triple(80f, 0f, KeyBehavior.SWIPE_RIGHT),
                Triple(-80f, 0f, KeyBehavior.SWIPE_LEFT),
                Triple(0f, -80f, KeyBehavior.SWIPE_UP),
                Triple(0f, 80f, KeyBehavior.SWIPE_DOWN),
            ).forEach { (dx, dy, behavior) ->
                val h = Harness()
                h.tracker.down(1, 150f, 50f)
                h.tracker.move(1, 150f + dx / 2, 50f + dy / 2)
                h.tracker.move(1, 150f + dx, 50f + dy)
                h.tracker.move(1, 150f + dx * 1.2f, 50f + dy * 1.2f)
                h.tracker.up(1)
                val mx = (50 + dx / 2).toInt()
                val my = (50 + dy / 2).toInt()
                h.take() shouldBe
                    listOf(
                        "vibrate",
                        "press 1",
                        "move 1 $mx,$my",
                        "move 1 ${(50 + dx).toInt()},${(50 + dy).toInt()}",
                        "swipe 1 $behavior",
                        "move 1 ${(50 + dx * 1.2f).toInt()},${(50 + dy * 1.2f).toInt()}",
                        "vibrate",
                        "release 1 $behavior",
                    )
            }
        }

        "swipe preview fires again when the direction changes and a return cancels the swipe" {
            val h = Harness()
            h.tracker.down(1, 150f, 150f - 100f)
            h.tracker.move(1, 150f, -20f)
            h.tracker.move(1, 230f, 50f)
            h.tracker.move(1, 160f, 50f)
            h.tracker.up(1)
            h.take() shouldBe
                listOf(
                    "vibrate",
                    "press 1",
                    "move 1 50,-20",
                    "swipe 1 SWIPE_UP",
                    "move 1 130,50",
                    "swipe 1 SWIPE_RIGHT",
                    "move 1 60,50",
                    "vibrate",
                    "release 1 CLICK",
                )
        }

        "swipe by velocity" {
            val h = Harness(config = TestConfig(swipeTravel = 0, swipeVelocity = 1000))
            h.tracker.down(1, 150f, 50f)
            h.clock.advance(20)
            h.tracker.move(1, 150f, 20f) // 30px in 20ms = 1500px/s
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 1", "move 1 50,20", "swipe 1 SWIPE_UP", "vibrate", "release 1 SWIPE_UP")
        }

        "long press fires after the timeout and releases as a long click" {
            val h = Harness(caps = mapOf(1 to KeyCaps(hasLongPress = true)))
            h.tracker.down(1, 150f, 50f)
            h.clock.advance(299)
            h.take() shouldBe listOf("vibrate", "press 1")
            h.clock.advance(1)
            h.take() shouldBe listOf("vibrate long", "longPress 1")
            h.tracker.move(1, 150f, -50f)
            h.clock.advance(500)
            h.tracker.up(1)
            h.take() shouldBe listOf("move 1 50,-50 long", "vibrate", "release 1 LONG_CLICK long")
        }

        "keys without long press, repeat or popup never wait for it" {
            val h = Harness()
            h.tracker.down(1, 150f, 50f)
            h.clock.advance(2000)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 1", "vibrate", "release 1 CLICK")
        }

        "popup keys long press and report moves while held" {
            val h = Harness(caps = mapOf(2 to KeyCaps(hasPopup = true)))
            h.tracker.down(1, 250f, 50f)
            h.clock.advance(300)
            h.tracker.move(1, 330f, 50f)
            h.tracker.up(1)
            h.take() shouldBe
                listOf("vibrate", "press 2", "vibrate long", "longPress 2", "move 2 130,50 long", "vibrate", "release 2 LONG_CLICK long")
        }

        "a swipe before the timeout suppresses the long press" {
            val h = Harness(caps = mapOf(1 to KeyCaps(hasLongPress = true)))
            h.tracker.down(1, 150f, 50f)
            h.tracker.move(1, 150f, -20f)
            h.clock.advance(1000)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 1", "move 1 50,-20", "swipe 1 SWIPE_UP", "vibrate", "release 1 SWIPE_UP")
        }

        "repeatable keys repeat after the timeout and cancel before the final release" {
            val h = Harness(caps = mapOf(3 to KeyCaps(repeatable = true)))
            h.tracker.down(1, 350f, 50f)
            h.clock.advance(300)
            h.take() shouldBe listOf("vibrate", "press 3", "vibrate long", "vibrate", "release 3 CLICK long")
            h.clock.advance(60)
            h.take() shouldBe listOf("vibrate", "release 3 CLICK long", "vibrate", "release 3 CLICK long")
            h.clock.advance(10)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "cancel 3", "release 3 LONG_CLICK long")
            h.clock.advance(1000)
            h.events.shouldBeEmpty()
        }

        "double tap" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasDouble = true)))
            h.tracker.down(1, 50f, 50f)
            h.tracker.up(1)
            h.clock.advance(200)
            h.tracker.down(2, 50f, 50f)
            h.tracker.up(2)
            h.take() shouldBe
                listOf("vibrate", "press 0", "vibrate", "release 0 CLICK", "vibrate", "press 0", "vibrate", "release 0 DOUBLE_CLICK")
            // A third tap starts over.
            h.clock.advance(100)
            h.tracker.down(3, 50f, 50f)
            h.tracker.up(3)
            h.take() shouldBe listOf("vibrate", "press 0", "vibrate", "release 0 CLICK")
        }

        "double tap too slow is two clicks" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasDouble = true)))
            h.tracker.down(1, 50f, 50f)
            h.tracker.up(1)
            h.clock.advance(301)
            h.tracker.down(2, 50f, 50f)
            h.tracker.up(2)
            h.take() shouldBe
                listOf("vibrate", "press 0", "vibrate", "release 0 CLICK", "vibrate", "press 0", "vibrate", "release 0 CLICK")
        }

        "lazy double tap holds the click back until the timeout" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasLazyDouble = true)))
            h.tracker.down(1, 50f, 50f)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 0", "vibrate")
            h.clock.advance(299)
            h.events.shouldBeEmpty()
            h.clock.advance(1)
            h.take() shouldBe listOf("release 0 CLICK")
        }

        "lazy double tap replaces the pending click" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasLazyDouble = true)))
            h.tracker.down(1, 50f, 50f)
            h.tracker.up(1)
            h.clock.advance(150)
            h.tracker.down(2, 50f, 50f)
            h.tracker.up(2)
            h.clock.advance(1000)
            h.take() shouldBe listOf("vibrate", "press 0", "vibrate", "vibrate", "press 0", "vibrate", "release 0 LAZY_DOUBLE_CLICK")
        }

        "slide cursor steps and ends with zero then cancel" {
            val h = Harness(caps = mapOf(1 to KeyCaps(slideCursor = true)))
            h.tracker.down(1, 110f, 50f)
            h.tracker.move(1, 150f, 50f) // below travel
            h.tracker.move(1, 180f, 50f) // 70px: activates, 2 steps of 24
            h.tracker.move(1, 190f, 50f) // 10px since last step: nothing
            h.tracker.move(1, 140f, 50f) // 40px back: 1 step
            h.tracker.up(1)
            h.take() shouldBe
                listOf(
                    "vibrate",
                    "press 1",
                    "move 1 50,50",
                    "move 1 80,50",
                    "slide 1 2",
                    "swipe 1 SWIPE_RIGHT",
                    "move 1 90,50",
                    "move 1 40,50",
                    "slide 1 -1",
                    "vibrate",
                    "slide 1 0",
                    "cancel 1",
                )
        }

        "slide delete activates to the left and suppresses long press and repeat" {
            val h = Harness(caps = mapOf(3 to KeyCaps(slideDelete = true, repeatable = true)))
            h.tracker.down(1, 390f, 50f)
            h.tracker.move(1, 320f, 50f)
            h.clock.advance(1000)
            h.tracker.move(1, 290f, 50f)
            h.tracker.up(1)
            h.take() shouldBe
                listOf(
                    "vibrate",
                    "press 3",
                    "move 3 20,50",
                    "slide 3 -2",
                    "swipe 3 SWIPE_LEFT",
                    "move 3 -10,50",
                    "slide 3 -1",
                    "vibrate",
                    "slide 3 0",
                    "cancel 3",
                )
        }

        "slide is disabled while swipe travel is off" {
            val h = Harness(caps = mapOf(1 to KeyCaps(slideCursor = true)), config = TestConfig(swipeTravel = 0))
            h.tracker.down(1, 110f, 50f)
            h.tracker.move(1, 250f, 50f)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 1", "move 1 150,50", "vibrate", "release 1 CLICK")
        }

        "cancel ends the gesture without a release" {
            val h = Harness(caps = mapOf(1 to KeyCaps(hasLongPress = true)))
            h.tracker.down(1, 150f, 50f)
            h.tracker.move(1, 160f, 50f)
            h.tracker.cancel()
            h.clock.advance(1000)
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 1", "move 1 60,50", "cancel 1")
            h.tracker.isTracking shouldBe false
        }

        "cancelling a repeat cancels twice like GestureFrame" {
            val h = Harness(caps = mapOf(3 to KeyCaps(repeatable = true)))
            h.tracker.down(1, 350f, 50f)
            h.clock.advance(300)
            h.take()
            h.tracker.cancel()
            h.clock.advance(1000)
            h.take() shouldBe listOf("cancel 3", "cancel 3")
        }

        "two fingers interleave, each key on its own" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasLongPress = true)))
            h.tracker.down(10, 50f, 50f)
            h.clock.advance(100)
            h.tracker.down(11, 250f, 50f)
            h.clock.advance(50)
            h.tracker.move(11, 250f, -30f)
            h.tracker.up(10)
            h.clock.advance(500)
            h.tracker.up(11)
            h.take() shouldBe
                listOf(
                    "vibrate",
                    "press 0",
                    "vibrate",
                    "press 2",
                    "move 2 50,-30",
                    "swipe 2 SWIPE_UP",
                    "vibrate",
                    "release 0 CLICK",
                    "vibrate",
                    "release 2 SWIPE_UP",
                )
        }

        "a held key long presses while another finger taps" {
            val h = Harness(caps = mapOf(0 to KeyCaps(hasLongPress = true)))
            h.tracker.down(10, 50f, 50f)
            h.clock.advance(100)
            h.tracker.down(11, 150f, 50f)
            h.tracker.up(11)
            h.clock.advance(200)
            h.tracker.up(10)
            h.take() shouldBe
                listOf(
                    "vibrate",
                    "press 0",
                    "vibrate",
                    "press 1",
                    "vibrate",
                    "release 1 CLICK",
                    "vibrate long",
                    "longPress 0",
                    "vibrate",
                    "release 0 LONG_CLICK long",
                )
        }

        "a finger that leaves its key still belongs to it" {
            val h = Harness()
            h.tracker.down(1, 95f, 50f)
            h.tracker.move(1, 130f, 50f) // over key 1, below swipe travel
            h.tracker.up(1)
            h.take() shouldBe listOf("vibrate", "press 0", "move 0 130,50", "vibrate", "release 0 CLICK")
        }

        "a second finger on a held key only takes over when the first lifts" {
            val h = Harness()
            h.tracker.down(1, 150f, 50f)
            h.tracker.down(2, 160f, 50f)
            h.tracker.move(2, 170f, 50f)
            h.tracker.up(1)
            h.tracker.move(2, 180f, 50f)
            h.tracker.up(2)
            h.take() shouldBe listOf("vibrate", "press 1", "move 1 80,50", "vibrate", "release 1 CLICK")
        }
    })
