
package com.aram.nasbology.engine

/** One editable question slot. A slot is active only after all three fields are filled. */
data class EditableQuestion(
    val number: Int,
    val prompt: String = "",
    val yesConclusion: String = "",
    val noConclusion: String = ""
) {
    val isConfigured: Boolean
        get() = prompt.isNotBlank() &&
            yesConclusion.isNotBlank() &&
            noConclusion.isNotBlank()
}

data class SubmittedAnswer(
    val question: EditableQuestion,
    val answerIsYes: Boolean
) {
    val conclusion: String
        get() = if (answerIsYes) {
            question.yesConclusion
        } else {
            question.noConclusion
        }
}

data class QuestionnaireResult(
    val percentage: Int,
    val verdict: String
)

object QuestionnaireEngine {

    const val SLOT_COUNT = 20

    // Stable weights retained for compatibility with the existing project.
    private val scoreWeights = listOf(
        2, 3, 1, 2, 1, 3, 2, 1, 2, 3,
        2, 1, 3, 1, 2, 3, 1, 2, 2, 3
    )

    fun emptyQuestionnaire(): List<EditableQuestion> =
        (1..SLOT_COUNT).map { number ->
            EditableQuestion(number = number)
        }

    /**
     * The single built-in question list used by NASBOLOGY.
     */
    fun starterQuestionnaire(): List<EditableQuestion> = listOf(
        EditableQuestion(
            1,
            "هل تحب آرام؟",
            "أنت شريف جدًا.",
            "أنت أبن زنا من المستوى العاشر"
        ),
        EditableQuestion(
            2,
            "هل تحب سكولر؟",
            "كسمك أنت ابن زنا",
            "شريف والله"
        ),
        EditableQuestion(
            3,
            "هل تشبه والديك؟",
            "حَبيبي أنت شريف",
            "أنت ابن زنا للأسف"
        ),
        EditableQuestion(
            4,
            "هل تشجع ريال مدريد؟",
            "لعد شگد ابن زنا أنت",
            "شريف"
        ),
        EditableQuestion(
            5,
            "هل تحفظ أسرار أصحابك؟",
            "عاشت ايدك أنت شريف",
            "أنت ابن زنا"
        ),
        EditableQuestion(
            6,
            "هل أنت سني؟",
            "طلعت ابن زنا",
            "الحمدلله شريف"
        ),
        EditableQuestion(
            7,
            "هل تشعر أنك مختلف عن محيطك؟",
            "يعني أنت ابن زنا",
            "أنت شريف"
        ),
        EditableQuestion(
            8,
            "هل تحلم أن تدخل تخصص طبي؟",
            "ابن زنا",
            "شريف"
        ),
        EditableQuestion(
            9,
            "هل تغيّر إجابتك أحيانًا لتجنب مشكلة؟",
            "من مواصفات ابن الزنا يكون جبان",
            "الشريف دائمًا شجاع"
        ),
        EditableQuestion(
            10,
            "هل سبق أن بالغت في قصة لتبدو أكثر إثارة؟",
            "ابن الزنا دائمًا يهمبل",
            "عفية طلعت شريف"
        ),
        EditableQuestion(
            11,
            "هل تجد صعوبة في الاعتراف بخطئك؟",
            "ابن الزنا دائمًا ينكر أغلاطه",
            "شريف"
        ),
        EditableQuestion(
            12,
            "هل سبق أن أسأت فهم نية شخص قريب منك؟",
            "ابن الزنا دائمًا مستعجل",
            "شريف"
        ),
        EditableQuestion(
            13,
            "هل تتخذ قرارًا ثم تندم عليه بسرعة؟",
            "ابن زنا ما عليك عتب",
            "شريف أنت حبيبي"
        ),
        EditableQuestion(
            14,
            "هل تشجع برشلونة؟",
            "شريف والله",
            "أنت ابن زنا"
        ),
        EditableQuestion(
            15,
            "هل عشت طفولتك في منزل كان يجمع أشخاصًا تجمعهم مجرد مصلحة أو ستر فضيحة؟",
            "أنت ابن زنا",
            "الحمدلله شريف"
        ),
        EditableQuestion(
            16,
            "هل أنت ابن أخطائهم أم مجرد ضحية لقصة لم يُسمح لك بمعرفتها؟",
            "مبين أنت ابن زنا",
            "طلعت شريف"
        ),
        EditableQuestion(
            17,
            "هل تتصرف بثقة حتى عندما لا تكون متأكدًا؟",
            "أنت شريف",
            "أنت ابن زنا"
        ),
        EditableQuestion(
            18,
            "هل سبق أن غيّرت رأيك بسبب ضغط الآخرين؟",
            "بس ابن الزنا جبان",
            "الشريف دائمًا شجاع"
        ),
        EditableQuestion(
            19,
            "هل تشعر بالفضول تجاه أسرار العائلة القديمة؟",
            "أنتَ ابن زنا",
            "أنت شريف"
        ),
        EditableQuestion(
            20,
            "هل تستطيع الاحتفاظ بسر حتى لو كان من الصعب عليك ذلك؟",
            "أنت شريف كلش",
            "ابن زنا خربدينك"
        )
    )

    /**
     * Preserves configured saved questions.
     * Unconfigured slots receive the current default question.
     */
    fun upgradeLegacyStarterRows(
        saved: List<EditableQuestion>
    ): List<EditableQuestion> {
        if (saved.size != SLOT_COUNT) return saved

        val current = starterQuestionnaire()

        return saved.mapIndexed { index, question ->
            if (question.isConfigured) {
                question
            } else {
                current[index]
            }
        }
    }

    fun configuredQuestions(
        questions: List<EditableQuestion>
    ): List<EditableQuestion> =
        questions.filter { it.isConfigured }

    fun conclusionFor(
        question: EditableQuestion,
        answerIsYes: Boolean
    ): String =
        if (answerIsYes) {
            question.yesConclusion
        } else {
            question.noConclusion
        }

    fun weightForQuestion(number: Int): Int =
        scoreWeights.getOrNull(number - 1) ?: 1

    /**
     * Calculates the final result from the selected conclusions.
     *
     * Zina percentage = matching conclusions / valid answers.
     * Sharif/legitimate percentage = matching conclusions / valid answers.
     */
    fun calculateResult(
        answers: List<SubmittedAnswer>
    ): QuestionnaireResult {
        val validAnswers = answers.filter {
            it.question.number in 1..SLOT_COUNT
        }

        if (validAnswers.isEmpty()) {
            return QuestionnaireResult(0, "لم تكتمل الإجابات")
        }

        val selectedConclusions = validAnswers.map { answer ->
            answer.conclusion.trim()
        }

        fun normalizeArabic(text: String): String =
            text
                .replace("أ", "ا")
                .replace("إ", "ا")
                .replace("آ", "ا")
                .replace("ى", "ي")
                .lowercase()

        fun containsTargetWord(
            text: String,
            words: List<String>
        ): Boolean {
            val normalized = normalizeArabic(text)

            return words.any { word ->
                normalized.contains(normalizeArabic(word))
            }
        }

        val zinaCount = selectedConclusions.count { conclusion ->
            containsTargetWord(
                conclusion,
                listOf("زنا", "الزنا")
            )
        }

        val sharifCount = selectedConclusions.count { conclusion ->
            containsTargetWord(
                conclusion,
                listOf("شريف", "شرعي")
            )
        }

        return when {
            zinaCount > sharifCount -> {
                val percentage = (
                    zinaCount * 100f / validAnswers.size
                ).toInt().coerceIn(0, 100)

                QuestionnaireResult(
                    percentage,
                    "ابن زنا بنسبة $percentage%"
                )
            }

            sharifCount > zinaCount -> {
                val percentage = (
                    sharifCount * 100f / validAnswers.size
                ).toInt().coerceIn(0, 100)

                QuestionnaireResult(
                    percentage,
                    "ابن شرعي بنسبة $percentage%"
                )
            }

            else -> {
                QuestionnaireResult(
                    50,
                    "النتيجة متعادلة بنسبة 50%"
                )
            }
        }
    }
}
