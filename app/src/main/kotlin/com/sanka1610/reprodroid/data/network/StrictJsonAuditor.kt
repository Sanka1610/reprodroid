package com.sanka1610.reprodroid.data.network

import java.nio.charset.StandardCharsets

internal class StrictJsonAuditor(
    private val source: String,
    private val maximumDepth: Int = 32,
    private val maximumStringBytes: Int = 16 * 1024,
) {
    private var index = 0

    fun audit() {
        skipWhitespace()
        parseValue(1)
        skipWhitespace()
        check(index == source.length) { "Trailing content is not allowed." }
    }

    private fun parseValue(depth: Int) {
        check(depth <= maximumDepth) { "JSON nesting exceeds the supported depth." }
        skipWhitespace()
        when (peek()) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> parseString()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> parseNumber()
            else -> invalid()
        }
    }

    private fun parseObject(depth: Int) {
        expect('{')
        skipWhitespace()
        if (consume('}')) return
        val keys = mutableSetOf<String>()
        while (true) {
            skipWhitespace()
            check(peek() == '"') { "Object keys must be strings." }
            check(keys.add(parseString())) { "Duplicate JSON object keys are not allowed." }
            skipWhitespace()
            expect(':')
            parseValue(depth + 1)
            skipWhitespace()
            if (consume('}')) return
            expect(',')
        }
    }

    private fun parseArray(depth: Int) {
        expect('[')
        skipWhitespace()
        if (consume(']')) return
        while (true) {
            parseValue(depth + 1)
            skipWhitespace()
            if (consume(']')) return
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when {
                character == '"' -> {
                    check(result.toString().toByteArray(StandardCharsets.UTF_8).size <= maximumStringBytes) {
                        "A JSON string exceeds the supported size."
                    }
                    return result.toString()
                }
                character == '\\' -> parseEscape(result)
                character.code < 0x20 -> invalid()
                character.isHighSurrogate() -> {
                    val low = source.getOrNull(index)
                    check(low != null && low.isLowSurrogate()) { "An invalid surrogate was received." }
                    result.append(character).append(low)
                    index++
                }
                character.isLowSurrogate() -> invalid()
                else -> result.append(character)
            }
        }
        invalid()
    }

    private fun parseEscape(result: StringBuilder) {
        val escaped = source.getOrNull(index++) ?: invalid()
        when (escaped) {
            '"', '\\', '/' -> result.append(escaped)
            'b' -> result.append('\b')
            'f' -> result.append('\u000c')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'u' -> {
                val first = parseHexCodeUnit()
                if (first.isHighSurrogate()) {
                    check(source.getOrNull(index) == '\\' && source.getOrNull(index + 1) == 'u') {
                        "An invalid surrogate was received."
                    }
                    index += 2
                    val second = parseHexCodeUnit()
                    check(second.isLowSurrogate()) { "An invalid surrogate was received." }
                    result.append(first).append(second)
                } else {
                    check(!first.isLowSurrogate()) { "An invalid surrogate was received." }
                    result.append(first)
                }
            }
            else -> invalid()
        }
    }

    private fun parseHexCodeUnit(): Char {
        check(index + 4 <= source.length) { "An incomplete unicode escape was received." }
        val raw = source.substring(index, index + 4)
        check(raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "An invalid unicode escape was received." }
        index += 4
        return raw.toInt(16).toChar()
    }

    private fun parseNumber() {
        consume('-')
        when (peek()) {
            '0' -> {
                index++
                check(peekOrNull()?.isDigit() != true) { "Leading zeroes are not valid JSON numbers." }
            }
            in '1'..'9' -> while (peekOrNull()?.isDigit() == true) index++
            else -> invalid()
        }
        if (consume('.')) {
            check(peekOrNull()?.isDigit() == true) { "A fractional digit is required." }
            while (peekOrNull()?.isDigit() == true) index++
        }
        if (consume('e') || consume('E')) {
            consume('+') || consume('-')
            check(peekOrNull()?.isDigit() == true) { "An exponent digit is required." }
            while (peekOrNull()?.isDigit() == true) index++
        }
    }

    private fun literal(value: String) {
        check(source.startsWith(value, index)) { "An invalid JSON literal was received." }
        index += value.length
    }

    private fun skipWhitespace() {
        while (peekOrNull() in setOf(' ', '\n', '\r', '\t')) index++
    }

    private fun expect(expected: Char) = check(consume(expected)) { "Expected '$expected'." }
    private fun consume(expected: Char): Boolean = if (peekOrNull() == expected) { index++; true } else false
    private fun peek(): Char = peekOrNull() ?: invalid()
    private fun peekOrNull(): Char? = source.getOrNull(index)
    private fun invalid(): Nothing = throw IllegalArgumentException("The response is not valid strict JSON.")
}
