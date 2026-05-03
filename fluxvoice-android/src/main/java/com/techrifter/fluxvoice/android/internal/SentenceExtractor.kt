package com.techrifter.fluxvoice.android.internal

/**
 * Extracts complete sentences from a streaming token buffer for sentence-level TTS.
 *
 * The regex negative lookbehind prevents splitting on numbered-list markers ("1. ").
 * The lookahead requires the next word to start with a capital letter or opening quote,
 * avoiding splits on mid-sentence abbreviations like "Dr." or "vs.".
 *
 * Unconsumed text (partial sentence) remains in [buffer] after each [extract] call,
 * and is combined with future tokens in subsequent calls.
 */
internal object SentenceExtractor {

    private val SENTENCE_BOUNDARY = Regex("""(?<![0-9])[.!?]+\s+(?=[A-Z"'])""")

    /**
     * Extract complete sentences from [buffer], leaving any trailing partial
     * sentence in place for the next token batch.
     */
    fun extract(buffer: StringBuilder): List<String> {
        val text = buffer.toString()
        val sentences = mutableListOf<String>()
        var lastIndex = 0

        for (match in SENTENCE_BOUNDARY.findAll(text)) {
            val end = match.range.last + 1
            val sentence = text.substring(lastIndex, end).trim()
            // contains(' ') rejects lone fragments like "1." that bypass the lookbehind.
            if (sentence.isNotBlank() && sentence.contains(' ')) {
                sentences.add(sentence)
                lastIndex = end
            }
        }

        buffer.clear()
        buffer.append(text.substring(lastIndex))
        return sentences
    }
}
