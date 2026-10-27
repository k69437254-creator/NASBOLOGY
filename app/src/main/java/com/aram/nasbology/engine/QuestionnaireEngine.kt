package com.aram.nasbology.engine

/** One editable question slot. A slot is active only after all three fields are filled. */
data class EditableQuestion(
    val number: Int,
    val prompt: String = "",
    val yesConclusion: String = "",
    val noConclusion: String = ""
) {
    val isConfigured: Boolean
        get() = prompt.isNotBlank() && yesConclusion.isNotBlank() && noConclusion.isNotBlank()
}

data class SubmittedAnswer(
    val question: EditableQuestion,
    val answerIsYes: Boolean
) {
    val conclusion: String
        get() = if (answerIsYes) question.yesConclusion else question.noConclusion
}

object QuestionnaireEngine {
    const val SLOT_COUNT = 20

    fun emptyQuestionnaire(): List<EditableQuestion> =
        (1..SLOT_COUNT).map { EditableQuestion(number = it) }

    /**
     * Built-in Arabic bank supplied by the app owner. Text is kept verbatim.
     * These are entertainment prompts and do not establish anything about parentage.
     */
    fun starterQuestionnaire(): List<EditableQuestion> = listOf(
        EditableQuestion(1, "هل تحب آرام؟", "أنت شريف جدًا.", "أنت أبن زنا من المستوى العاشر"),
        EditableQuestion(2, "هل تحب سكولر؟", "كسمك أنت ابن زنا", "شريف والله"),
        EditableQuestion(3, "هل تشبه والديك؟", "حَبيبي أنت شريف", "أنت ابن زنا للأسف"),
        EditableQuestion(4, "هل تشجع ريال مدريد؟", "لعد شگد ابن زنا أنت", "شريف"),
        EditableQuestion(5, "هل تحفظ أسرار أصحابك؟", "عاشت ايدك أنت شريف", "أنت ابن زنا"),
        EditableQuestion(6, "هل أنت سني؟", "طلعت ابن زنا", "الحمدلله شريف"),
        EditableQuestion(7, "هل تشعر أنك مختلف عن محيطك؟", "يعني أنت ابن زنا", "أنت شريف"),
        EditableQuestion(8, "هل تحلم أن تدخل تخصص طبي؟", "ابن زنا", "شريف"),
        EditableQuestion(9, "هل تغيّر إجابتك أحيانًا لتجنب مشكلة؟", "من مواصفات ابن الزنا يكون جبان", "ابن الحلال دائمًا شجاع"),
        EditableQuestion(10, "هل سبق أن بالغت في قصة لتبدو أكثر إثارة؟", "ابن الزنا دائمًا يهمبل", "عفية طلعت شريف"),
        EditableQuestion(11, "هل تجد صعوبة في الاعتراف بخطئك؟", "ابن الزنا دائمًا ينكر أغلاطه", "شريف"),
        EditableQuestion(12, "هل سبق أن أسأت فهم نية شخص قريب منك؟", "ابن الزنا دائمًا مستعجل", "شريف"),
        EditableQuestion(13, "هل تتخذ قرارًا ثم تندم عليه بسرعة؟", "ابن زنا ما عليك عتب", "شريف أنت حبيبي"),
        EditableQuestion(14, "هل تشجع برشلونة؟", "شريف والله", "أنت ابن زنا"),
        EditableQuestion(15, "هل عشت طفولتك في منزل كان يجمع أشخاصًا تجمعهم مجرد مصلحة أو ستر فضيحة؟", "أنت ابن زنا", "الحمدلله شريف"),
        EditableQuestion(16, "هل أنت ابن أخطائهم أم مجرد ضحية لقصة لم يُسمح لك بمعرفتها؟", "مبين أنت ابن زنا", "طلعت شريف"),
        EditableQuestion(17, "هل تتصرف بثقة حتى عندما لا تكون متأكدًا؟", "أنت شريف", "أنت ابن زنا"),
        EditableQuestion(18, "هل سبق أن غيّرت رأيك بسبب ضغط الآخرين؟", "بس ابن الزنا جبان", "الشريف دائمًا شجاع"),
        EditableQuestion(19, "هل تشعر بالفضول تجاه أسرار العائلة القديمة؟", "أنتَ ابن زنا", "أنت شريف"),
        EditableQuestion(20, "هل تستطيع الاحتفاظ بسر حتى لو كان من الصعب عليك ذلك؟", "أنت شريف كلش", "ابن زنا خربدينك")
    )

    /**
     * Original built-in bank shipped by earlier versions. Used only to recognize
     * untouched default rows and upgrade those rows without overwriting edits.
     */
    internal fun legacyStarterQuestionnaire(): List<EditableQuestion> {
        val prompts = listOf(
            "هل تتصرف أحيانًا بعفوية زائدة؟",
            "هل تحب المزاح حتى في الأوقات الجدية؟",
            "هل تغيّر رأيك بسرعة عندما تظهر فكرة أفضل؟",
            "هل تنسى أين وضعت أغراضك؟",
            "هل تميل إلى المبالغة في وصف القصص؟",
            "هل أنت دائماً قوي وواثق؟",
            "هل تفضّل اتخاذ القرار بنفسك؟",
            "هل تضحك أحيانًا في وقت غير مناسب؟",
            "هل تحب أن تكون محور الاهتمام؟",
            "هل تتأخر أحيانًا رغم استعدادك المبكر؟",
            "هل تعتمد على حدسك في بعض القرارات؟",
            "هل تؤجل بعض الأعمال إلى آخر لحظة؟",
            "هل تحب تجربة أشياء جديدة؟",
            "هل يصعب عليك الاعتراف بأنك مخطئ؟",
            "هل تتذكر التفاصيل الصغيرة؟",
            "هل تفضّل الهدوء على التجمعات الكبيرة؟",
            "هل تتكلم قبل أن تفكر أحيانًا؟",
            "هل تستطيع إخفاء ضحكتك في المواقف الجدية؟",
            "هل تحب المنافسة حتى في الألعاب البسيطة؟",
            "هل تغيّر خططك حسب مزاجك؟"
        )
        val yes = listOf(
            "روحك العفوية حاضرة بقوة؛ هذا استنتاج ترفيهي لا أكثر.",
            "أنت تضيف لمسة فكاهية إلى يومك؛ استنتاج ساخر فقط.",
            "مرن في تفكيرك، أو ربما تحب المفاجآت؛ النتيجة للترفيه.",
            "الأغراض عندك تخوض مغامرات مستقلة؛ استنتاج خيالي.",
            "راوٍ محترف مع مؤثرات إضافية؛ هذا مزاح وليس تقييمًا.",
            "إجابتك تعبّر عن ثقة بالنفس؛ استنتاج ترفيهي لا يثبت أي شيء عن النسب.",
            "تحب الاستقلال في القرار؛ قراءة خفيفة لإجابتك.",
            "توقيت الضحك عندك له قوانينه الخاصة؛ استنتاج ساخر.",
            "لديك حضور لافت حسب إجابتك؛ النتيجة غير علمية.",
            "الساعة عندك اقتراح وليست موعدًا؛ مزحة فقط.",
            "تثق بحدسك أحيانًا؛ لا يعني هذا صحة كل قرار.",
            "تتقن فن التأجيل؛ استنتاج فكاهي.",
            "فضولك مستعد للمغامرة؛ استنتاج ترفيهي.",
            "تتمسك برأيك بقوة؛ مجرد قراءة ساخرة.",
            "ملاحظتك للتفاصيل تستحق الانتباه؛ لا توجد دلالة علمية هنا.",
            "قد تحتاج وقتًا هادئًا لنفسك؛ استنتاج ترفيهي.",
            "كلماتك تسبق أحيانًا خطة الطوارئ؛ مزحة فقط.",
            "لديك قدرة على كتم الضحك… إلى حد ما؛ استنتاج ساخر.",
            "المنافسة تضيف نكهة إلى يومك؛ هذا للترفيه.",
            "خططك مرنة مثل الطقس؛ استنتاج فكاهي."
        )
        val no = listOf(
            "تختار العفوية بجرعات محسوبة؛ استنتاج ترفيهي.",
            "تعرف متى تكون جادًا؛ قراءة خفيفة لإجابتك.",
            "تحب الثبات على رأيك؛ استنتاج غير علمي.",
            "ذاكرتك قد تكون منظمة أكثر مما تتوقع؛ مزحة فقط.",
            "تفضّل رواية الأحداث كما هي؛ استنتاج ترفيهي.",
            "الثقة تظهر بطرق مختلفة؛ إجابتك لا تثبت أي حقيقة عن النسب.",
            "ترحّب بالمشورة أحيانًا؛ استنتاج ساخر.",
            "تحافظ على رباطة جأشك؛ قراءة فكاهية فقط.",
            "تترك مساحة للآخرين؛ استنتاج ترفيهي.",
            "قد تكون علاقتك بالمواعيد جيدة؛ مزحة لا أكثر.",
            "تفضّل الأدلة الواضحة؛ استنتاج ترفيهي.",
            "تبدأ الأعمال قبل أن تتراكم؛ هذا استنتاج خفيف.",
            "تختار المألوف أحيانًا؛ استنتاج غير علمي.",
            "تستطيع مراجعة موقفك؛ قراءة فكاهية.",
            "تركّز على الصورة العامة؛ استنتاج ترفيهي.",
            "تستمتع بالأجواء الاجتماعية؛ مزحة فقط.",
            "تفكر قبل الكلام في الغالب؛ استنتاج ترفيهي.",
            "تضحك عندما يحين الوقت المناسب، غالبًا؛ استنتاج ساخر.",
            "لا تحوّل كل شيء إلى منافسة؛ قراءة خفيفة.",
            "تلتزم بخطتك غالبًا؛ استنتاج ترفيهي."
        )
        return (1..SLOT_COUNT).map { number ->
            EditableQuestion(number, prompts[number - 1], yes[number - 1], no[number - 1])
        }
    }

    /** Replaces only rows still identical to the old defaults; user edits survive updates. */
    fun upgradeLegacyStarterRows(saved: List<EditableQuestion>): List<EditableQuestion> {
        if (saved.size != SLOT_COUNT) return saved
        val legacy = legacyStarterQuestionnaire()
        val current = starterQuestionnaire()
        return saved.mapIndexed { index, question ->
            if (question == legacy[index]) current[index] else question
        }
    }

    fun configuredQuestions(questions: List<EditableQuestion>): List<EditableQuestion> =
        questions.filter { it.isConfigured }

    fun conclusionFor(question: EditableQuestion, answerIsYes: Boolean): String =
        if (answerIsYes) question.yesConclusion else question.noConclusion
}
