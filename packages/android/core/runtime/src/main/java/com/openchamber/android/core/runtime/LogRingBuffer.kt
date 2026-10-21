package com.openchamber.android.core.runtime

import java.util.ArrayDeque

/**
 * A bounded buffer of the server's log lines.
 *
 * The server writes to a pipe, and a pipe nobody reads blocks the writer, so
 * the supervisor drains both streams into this buffer continuously. The
 * buffer keeps the last lines and drops the oldest, because a phone has no
 * room for an unbounded log.
 */
class LogRingBuffer(private val capacity: Int = 500) {

    private val lines = ArrayDeque<String>(capacity)

    @Synchronized
    fun append(line: String) {
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun append(text: String, source: String) {
        text.split('\n').filter { it.isNotEmpty() }.forEach { append("[$source] $it") }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun tail(count: Int): List<String> = lines.toList().takeLast(count)

    @Synchronized
    fun clear() = lines.clear()
}
