package com.meshsos.data.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BleFramingTest {

    private var now = 0L
    private val framing = BleFraming { now }

    private fun message(size: Int) = Random(size).nextBytes(size)

    @Test
    fun `frames never exceed a single ATT value`() {
        for (mtu in listOf(23, 185, 247, 517, 1000)) {
            val frames = framing.buildFrames(message(5_000), mtu)
            val limit = minOf(mtu - 3, 512)
            assertTrue("mtu=$mtu", frames.all { it.size <= limit })
        }
    }

    @Test
    fun `round trips at minimum and maximum MTU`() {
        for (mtu in listOf(23, 517)) {
            val original = message(3_000)
            val frames = framing.buildFrames(original, mtu)
            val results = frames.map { framing.accept("S:A", it) }
            assertTrue(results.dropLast(1).all { it == null })
            assertArrayEquals(original, results.last())
        }
    }

    @Test
    fun `single frame message is returned immediately`() {
        val original = message(10)
        val frames = framing.buildFrames(original, 247)
        assertEquals(1, frames.size)
        assertArrayEquals(original, framing.accept("C:B", frames[0]))
    }

    @Test
    fun `out of order and duplicate frames reassemble once`() {
        val original = message(1_000)
        val frames = framing.buildFrames(original, 23)
        val shuffled = (frames + frames.take(3)).shuffled(Random(7))
        val completed = shuffled.mapNotNull { framing.accept("S:A", it) }
        assertEquals(1, completed.size)
        assertArrayEquals(original, completed.single())
    }

    @Test
    fun `interleaved messages on one link do not corrupt each other`() {
        val first = message(700)
        val second = message(900)
        val a = framing.buildFrames(first, 23)
        val b = framing.buildFrames(second, 23)
        val interleaved = (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }
        val completed = interleaved.mapNotNull { framing.accept("S:A", it) }
        assertEquals(2, completed.size)
        assertArrayEquals(first, completed[0])
        assertArrayEquals(second, completed[1])
    }

    @Test
    fun `same sequence on different links is kept apart`() {
        val original = message(400)
        val frames = framing.buildFrames(original, 23)
        frames.dropLast(1).forEach { assertNull(framing.accept("S:A", it)) }
        frames.dropLast(1).forEach { assertNull(framing.accept("C:A", it)) }
        assertArrayEquals(original, framing.accept("S:A", frames.last()))
        assertArrayEquals(original, framing.accept("C:A", frames.last()))
    }

    @Test
    fun `stale partial messages are pruned`() {
        val frames = framing.buildFrames(message(400), 23)
        framing.accept("S:A", frames[0])
        now += 60_000
        framing.pruneOlderThan(30_000)
        // The rest of the message alone can no longer complete it.
        assertTrue(frames.drop(1).mapNotNull { framing.accept("S:A", it) }.isEmpty())
    }

    @Test
    fun `malformed frames are ignored`() {
        assertNull(framing.accept("S:A", byteArrayOf(1, 2, 3)))
        assertNull(framing.accept("S:A", byteArrayOf(0, 1, 0, 5, 0, 2, 9))) // index >= total
        assertNull(framing.accept("S:A", byteArrayOf(0, 1, 0, 0, 0, 0, 9))) // total == 0
    }
}
