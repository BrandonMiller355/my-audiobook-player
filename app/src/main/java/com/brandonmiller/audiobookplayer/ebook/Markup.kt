package com.brandonmiller.audiobookplayer.ebook

/**
 * A lenient tag scanner for the XHTML and XML inside an EPUB (design D17).
 *
 * Not `XmlPullParser`, for two reasons. Real EPUB XHTML is not reliably well-formed XML — `&nbsp;`
 * is undefined without the XHTML DTD and makes a conforming parser throw, and unclosed `<br>` and
 * `<img>` are everywhere — while the `ebook-reader` spec requires rendering what can be rendered
 * rather than failing. And `XmlPullParser` on Android lives in `android.util.Xml`, which would drag
 * Robolectric into every parser test; this is plain Kotlin over a `String`.
 *
 * Never throws. Anything it cannot make sense of is skipped or treated as text.
 */
internal object Markup {

    sealed interface Token {
        data class Start(
            val name: String,
            val attributes: Map<String, String>,
            val selfClosing: Boolean,
        ) : Token

        data class End(val name: String) : Token

        data class Text(val value: String) : Token
    }

    /**
     * Tokenizes a whole document. A list rather than a stream: EPUB documents are tens of
     * kilobytes, and the callers walk them more than once.
     */
    fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val length = source.length

        while (i < length) {
            val open = source.indexOf('<', i)
            if (open < 0) {
                appendText(tokens, source, i, length)
                break
            }
            appendText(tokens, source, i, open)

            i = when {
                source.startsWith("<!--", open) -> skipTo(source, open + 4, "-->")
                source.startsWith("<![CDATA[", open) -> {
                    val end = source.indexOf("]]>", open + 9)
                    val stop = if (end < 0) length else end
                    // CDATA content is literal — no entity decoding.
                    if (stop > open + 9) tokens += Token.Text(source.substring(open + 9, stop))
                    if (end < 0) length else end + 3
                }
                // Doctypes and processing instructions carry nothing the reader needs.
                source.startsWith("<!", open) || source.startsWith("<?", open) ->
                    skipTo(source, open + 2, ">")
                else -> readTag(source, open, tokens)
            }
        }
        return tokens
    }

    private fun appendText(tokens: MutableList<Token>, source: String, from: Int, to: Int) {
        if (to <= from) return
        tokens += Token.Text(decodeEntities(source.substring(from, to)))
    }

    private fun skipTo(source: String, from: Int, terminator: String): Int {
        val end = source.indexOf(terminator, from)
        return if (end < 0) source.length else end + terminator.length
    }

    /**
     * Reads one tag starting at `<`. Returns where scanning should continue.
     *
     * A `<` that does not begin a plausible tag — a stray less-than in prose, which real books do
     * contain — is emitted as text rather than swallowed.
     */
    private fun readTag(source: String, open: Int, tokens: MutableList<Token>): Int {
        var i = open + 1
        val length = source.length
        val closing = i < length && source[i] == '/'
        if (closing) i++

        val nameStart = i
        while (i < length && isNameChar(source[i])) i++
        if (i == nameStart) {
            tokens += Token.Text("<")
            return open + 1
        }
        val name = normalizeName(source.substring(nameStart, i))

        if (closing) {
            val end = source.indexOf('>', i)
            tokens += Token.End(name)
            return if (end < 0) length else end + 1
        }

        val attributes = mutableMapOf<String, String>()
        var selfClosing = false
        while (i < length) {
            while (i < length && source[i].isWhitespace()) i++
            if (i >= length) break
            when {
                source[i] == '>' -> { i++; break }
                source.startsWith("/>", i) -> { selfClosing = true; i += 2; break }
                source[i] == '/' -> { selfClosing = true; i++ }
                else -> i = readAttribute(source, i, attributes)
            }
        }

        tokens += Token.Start(name, attributes, selfClosing)
        return i
    }

    private fun readAttribute(source: String, start: Int, into: MutableMap<String, String>): Int {
        val length = source.length
        var i = start
        val nameStart = i
        while (i < length && isNameChar(source[i])) i++
        if (i == nameStart) return i + 1 // Unparseable character; step past it rather than spin.

        val name = normalizeName(source.substring(nameStart, i))
        while (i < length && source[i].isWhitespace()) i++

        if (i >= length || source[i] != '=') {
            // A valueless attribute, as in HTML. Nothing here needs its value.
            into[name] = ""
            return i
        }
        i++
        while (i < length && source[i].isWhitespace()) i++
        if (i >= length) return i

        val quote = source[i]
        if (quote == '"' || quote == '\'') {
            i++
            val valueStart = i
            while (i < length && source[i] != quote) i++
            into[name] = decodeEntities(source.substring(valueStart, minOf(i, length)))
            return if (i < length) i + 1 else i
        }

        val valueStart = i
        while (i < length && !source[i].isWhitespace() && source[i] != '>' && source[i] != '/') i++
        into[name] = decodeEntities(source.substring(valueStart, i))
        return i
    }

    private fun isNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '-' || c == ':' || c == '.'

    /**
     * Lowercased, and stripped of any namespace prefix. EPUB documents namespace their elements
     * (`opf:`, `ncx:`, `epub:`) inconsistently between producers, and every element this project
     * cares about is unambiguous by local name alone.
     */
    private fun normalizeName(raw: String): String {
        val local = raw.substringAfterLast(':')
        return local.lowercase()
    }

    // ---------------------------------------------------------------- entities

    /**
     * The named entities that actually appear in ebook text. XHTML defines several hundred; these
     * are what Calibre and the common producers emit, and an unrecognized entity is left verbatim
     * rather than dropped — showing `&sigma;` is bad, but silently losing a character is worse.
     */
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "­",
        "mdash" to "—", "ndash" to "–", "minus" to "−",
        "hellip" to "…", "middot" to "·", "bull" to "•",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
        "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
        "laquo" to "«", "raquo" to "»",
        "copy" to "©", "reg" to "®", "trade" to "™",
        "deg" to "°", "para" to "¶", "sect" to "§",
        "dagger" to "†", "Dagger" to "‡", "prime" to "′", "Prime" to "″",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "times" to "×", "divide" to "÷", "plusmn" to "±",
        "eacute" to "é", "egrave" to "è", "agrave" to "à",
        "ccedil" to "ç", "uuml" to "ü", "ouml" to "ö", "auml" to "ä",
        "ntilde" to "ñ", "iexcl" to "¡", "iquest" to "¿",
        "pound" to "£", "euro" to "€", "yen" to "¥", "cent" to "¢",
        "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    )

    /** Longest plausible entity name, used to bound the scan for a terminating `;`. */
    private const val MAX_ENTITY_LENGTH = 10

    fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw

        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }

            val semicolon = raw.indexOf(';', i + 1)
            if (semicolon < 0 || semicolon - i > MAX_ENTITY_LENGTH + 1) {
                // A bare ampersand in prose. Keep it.
                out.append(c)
                i++
                continue
            }

            val body = raw.substring(i + 1, semicolon)
            val decoded = decodeEntityBody(body)
            if (decoded == null) {
                out.append(c)
                i++
            } else {
                out.append(decoded)
                i = semicolon + 1
            }
        }
        return out.toString()
    }

    private fun decodeEntityBody(body: String): String? {
        if (body.isEmpty()) return null

        if (body[0] == '#') {
            val hex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
            val digits = if (hex) body.substring(2) else body.substring(1)
            if (digits.isEmpty()) return null
            val code = digits.toIntOrNull(if (hex) 16 else 10) ?: return null
            // Surrogates and out-of-range values would corrupt the string; drop them rather than
            // produce an invalid one.
            if (code <= 0 || code > 0x10FFFF || (code in 0xD800..0xDFFF)) return null
            return String(Character.toChars(code))
        }

        return NAMED_ENTITIES[body]
    }
}
