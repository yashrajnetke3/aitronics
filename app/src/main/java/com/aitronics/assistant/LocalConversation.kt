package com.aitronics.assistant

/** Session-only context. Only completed local model replies enter this buffer. */
internal class LocalConversation {
    private val turns = ArrayDeque<Pair<String,String>>()
    fun remember(question: String, reply: String) {
        if (turns.size == 4) turns.removeFirst()
        turns.addLast(question.take(2048) to reply.take(2048))
    }
    fun snapshot() = turns.toList()
    fun clear() = turns.clear()

    companion object {
        fun prompt(question: String, recent: List<Pair<String,String>>, cost: (String) -> Int): String? {
            if (question.length > 8192 || cost(question) !in 1..320) return null
            var best = question
            for (count in 1..recent.size) {
                val previous = recent.takeLast(count).joinToString("\n") { (q,a) -> "User: $q\nAssistant: $a" }
                val candidate = "$previous\nUser: $question\nAssistant:"
                if (cost(candidate) !in 1..320) break
                best = candidate
            }
            return best
        }
    }
}
