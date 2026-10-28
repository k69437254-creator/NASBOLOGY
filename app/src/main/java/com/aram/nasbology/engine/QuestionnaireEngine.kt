
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
    private fun previousClaimingStarterQuestionnaire(): List<EditableQuestion> = listOf(
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

    /** Returns the twenty questions selected for NASBOLOGY. */
    fun starterQuestionnaire(): List<EditableQuestion> =
        previousClaimingStarterQuestionnaire()

    /**
     * Preserves configured saved questions.
     * Unconfigured slots receive the current default question.
     */
        /** Built-in bank shipped by the earlier safe starter version. */
     fun legacyStarterQuestionnaire(): List<EditableQuestion> {
        val prompts = listOf(
            "هل تتصرف أحيانًا بعفوية زائدة؟", "هل تحب المزاح حتى في الأوقات الجدية؟",
            "هل تغيّر رأيك بسرعة عندما تظهر فكرة أفضل؟", "هل تنسى أين وضعت أغراضك؟",
            "هل تميل إلى المبالغة في وصف القصص؟", "هل أنت دائماً قوي وواثق؟",
            "هل تفضّل اتخاذ القرار بنفسك؟", "هل تضحك أحيانًا في وقت غير مناسب؟",
            "هل تحب أن تكون محور الاهتمام؟", "هل تتأخر أحيانًا رغم استعدادك المبكر؟",
            "هل تعتمد على حدسك في بعض القرارات؟", "هل تؤجل بعض الأعمال إلى آخر لحظة؟",
            "هل تحب تجربة أشياء جديدة؟", "هل يصعب عليك الاعتراف بأنك مخطئ؟",
            "هل تتذكر التفاصيل الصغيرة؟", "هل تفضّل الهدوء على التجمعات الكبيرة؟",
            "هل تتكلم قبل أن تفكر أحيانًا؟", "هل تستطيع إخفاء ضحكتك في المواقف الجدية؟",
            "هل تحب المنافسة حتى في الألعاب البسيطة؟", "هل تغيّر خططك حسب مزاجك؟"
        )
        val yes = listOf(
            "روحك العفوية حاضرة بقوة؛ هذا استنتاج ترفيهي لا أكثر.", "أنت تضيف لمسة فكاهية إلى يومك؛ استنتاج ساخر فقط.",
            "مرن في تفكيرك، أو ربما تحب المفاجآت؛ النتيجة للترفيه.", "الأغراض عندك تخوض مغامرات مستقلة؛ استنتاج خيالي.",
            "راوٍ محترف مع مؤثرات إضافية؛ هذا مزاح وليس تقييمًا.", "إجابتك تعبّر عن ثقة بالنفس؛ استنتاج ترفيهي.",
            "تحب الاستقلال في القرار؛ قراءة خفيفة لإجابتك.", "توقيت الضحك عندك له قوانينه الخاصة؛ استنتاج ساخر.",
            "لديك حضور لافت حسب إجابتك؛ النتيجة غير علمية.", "الساعة عندك اقتراح وليست موعدًا؛ مزحة فقط.",
            "تثق بحدسك أحيانًا؛ لا يعني هذا صحة كل قرار.", "تتقن فن التأجيل؛ استنتاج فكاهي.",
            "فضولك مستعد للمغامرة؛ استنتاج ترفيهي.", "تتمسك برأيك بقوة؛ مجرد قراءة ساخرة.",
            "ملاحظتك للتفاصيل تستحق الانتباه؛ لا توجد دلالة علمية هنا.", "قد تحتاج وقتًا هادئًا لنفسك؛ استنتاج ترفيهي.",
            "كلماتك تسبق أحيانًا خطة الطوارئ؛ مزحة فقط.", "لديك قدرة على كتم الضحك… إلى حد ما؛ استنتاج ساخر.",
            "المنافسة تضيف نكهة إلى يومك؛ هذا للترفيه.", "خططك مرنة مثل الطقس؛ استنتاج فكاهي."
        )
        val no = listOf(
            "تختار العفوية بجرعات محسوبة؛ استنتاج ترفيهي.", "تعرف متى تكون جادًا؛ قراءة خفيفة لإجابتك.",
            "تحب الثبات على رأيك؛ استنتاج غير علمي.", "ذاكرتك قد تكون منظمة أكثر مما تتوقع؛ مزحة فقط.",
            "تفضّل رواية الأحداث كما هي؛ استنتاج ترفيهي.", "الثقة تظهر بطرق مختلفة؛ إجابتك لا تثبت أي شيء عن النسب.",
            "ترحّب بالمشورة أحيانًا؛ استنتاج ساخر.", "تحافظ على رباطة جأشك؛ قراءة فكاهية فقط.",
            "تترك مساحة للآخرين؛ استنتاج ترفيهي.", "قد تكون علاقتك بالمواعيد جيدة؛ مزحة لا أكثر.",
            "تفضّل الأدلة الواضحة؛ استنتاج ترفيهي.", "تبدأ الأعمال قبل أن تتراكم؛ هذا استنتاج خفيف.",
            "تختار المألوف أحيانًا؛ استنتاج غير علمي.", "تستطيع مراجعة موقفك؛ قراءة فكاهية.",
            "تركّز على الصورة العامة؛ استنتاج ترفيهي.", "تستمتع بالأجواء الاجتماعية؛ مزحة فقط.",
            "تفكر قبل الكلام في الغالب؛ استنتاج ترفيهي.", "تضحك عندما يحين الوقت المناسب، غالبًا؛ استنتاج ساخر.",
            "لا تحوّل كل شيء إلى منافسة؛ قراءة خفيفة.", "تلتزم بخطتك غالبًا؛ استنتاج ترفيهي."
        )
        return (1..SLOT_COUNT).map { number ->
            EditableQuestion(number, prompts[number - 1], yes[number - 1], no[number - 1])
        }
    }


    fun upgradeLegacyStarterRows(
        saved: List<EditableQuestion>
    ): List<EditableQuestion> {
        if (saved.size != SLOT_COUNT) return saved

        val current = starterQuestionnaire()
        val previous = legacyStarterQuestionnaire()

        return saved.mapIndexed { index, question ->
            val safe = current[index]
            val old = previous[index]

            fun migrateField(value: String, oldDefault: String, safeDefault: String): String =
                when {
                    value.isBlank() -> safeDefault
                    value == oldDefault -> safeDefault
                    else -> value
                }

            EditableQuestion(
                number = safe.number,
                prompt = migrateField(question.prompt, old.prompt, safe.prompt),
                yesConclusion = migrateField(
                    question.yesConclusion,
                    old.yesConclusion,
                    safe.yesConclusion
                ),
                noConclusion = migrateField(
                    question.noConclusion,
                    old.noConclusion,
                    safe.noConclusion
                )
            )
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
     * Calculates a weighted, entertainment-only score from the yes/no answers.
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

        val totalWeight = validAnswers.sumOf { answer ->
            weightForQuestion(answer.question.number)
        }
        val yesWeight = validAnswers.sumOf { answer ->
            if (answer.answerIsYes) weightForQuestion(answer.question.number) else 0
        }
        val percentage = if (totalWeight == 0) {
            0
        } else {
            (yesWeight * 100 / totalWeight).coerceIn(0, 100)
        }

        val verdict = when {
            percentage >= 70 -> "ابن شرعي"
            percentage <= 30 -> "ابن زنا من العيار الثقيل"
            else -> "مثير للشبهة (مشكوك في أمر نسبك)"
        }

        return QuestionnaireResult(percentage, verdict)
    }
}
