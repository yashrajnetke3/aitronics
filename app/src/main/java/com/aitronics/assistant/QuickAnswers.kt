package com.aitronics.assistant

import java.math.BigDecimal
import java.math.MathContext

object QuickAnswers {
    private val arithmetic = Regex("""(?:what is |what's |calculate |how much is )?(-?\d{1,18}(?:\.\d{1,8})?)\s*(plus|minus|times|multiplied by|divided by|[+*/×÷-])\s*(-?\d{1,18}(?:\.\d{1,8})?)""")
    fun answer(question: String): String? {
        val q = question.trim().trimEnd('?', '.', '!').lowercase()
        when(q) {
            "hello","hi","hey","good morning","good evening" -> return "Hello! How can I help?"
            "who are you","what is your name","what's your name" -> return "I’m AITRONICS, your phone assistant."
            "how are you" -> return "I’m ready to help. What would you like to do?"
            "thank you","thanks" -> return "You’re welcome."
        }
        val match = arithmetic.matchEntire(q) ?: return null
        val left=BigDecimal(match.groupValues[1])
        val right=BigDecimal(match.groupValues[3])
        val value=when(match.groupValues[2]) {
            "+","plus" -> left+right
            "-","minus" -> left-right
            "*","×","times","multiplied by" -> left*right
            else -> if(right.signum()==0) return "Division by zero is undefined." else left.divide(right, MathContext.DECIMAL64)
        }
        return value.stripTrailingZeros().toPlainString()
    }
}
