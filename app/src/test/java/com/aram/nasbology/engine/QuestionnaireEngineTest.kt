package com.aram.nasbology.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireEngineTest {
    @Test
    fun `starts with twenty empty editable slots`() {
        val questions = QuestionnaireEngine.emptyQuestionnaire()
        assertEquals(20, questions.size)
        assertTrue(questions.all { !it.isConfigured })
    }

    @Test
    fun `starter questionnaire preserves the twenty selected questions`() {
        val questions = QuestionnaireEngine.starterQuestionnaire()
        assertEquals(20, questions.size)
        assertTrue(questions.all { it.isConfigured })
        assertEquals(
            listOf(
                "هل تحب آرام؟",
                "هل تحب سكولر؟",
                "هل تشبه والديك؟",
                "هل تشجع ريال مدريد؟",
                "هل تحفظ أسرار أصحابك؟",
                "هل أنت سني؟",
                "هل تشعر أنك مختلف عن محيطك؟",
                "هل تحلم أن تدخل تخصص طبي؟",
                "هل تغيّر إجابتك أحيانًا لتجنب مشكلة؟",
                "هل سبق أن بالغت في قصة لتبدو أكثر إثارة؟",
                "هل تجد صعوبة في الاعتراف بخطئك؟",
                "هل سبق أن أسأت فهم نية شخص قريب منك؟",
                "هل تتخذ قرارًا ثم تندم عليه بسرعة؟",
                "هل تشجع برشلونة؟",
                "هل عشت طفولتك في منزل كان يجمع أشخاصًا تجمعهم مجرد مصلحة أو ستر فضيحة؟",
                "هل أنت ابن أخطائهم أم مجرد ضحية لقصة لم يُسمح لك بمعرفتها؟",
                "هل تتصرف بثقة حتى عندما لا تكون متأكدًا؟",
                "هل سبق أن غيّرت رأيك بسبب ضغط الآخرين؟",
                "هل تشعر بالفضول تجاه أسرار العائلة القديمة؟",
                "هل تستطيع الاحتفاظ بسر حتى لو كان من الصعب عليك ذلك؟"
            ),
            questions.map { it.prompt }
        )
    }

    @Test
    fun `legacy default rows upgrade while user edited rows remain unchanged`() {
        val oldDefaults = QuestionnaireEngine.legacyStarterQuestionnaire()
        val legacySaved = oldDefaults.toMutableList()
        legacySaved[1] = legacySaved[1].copy(yesConclusion = "نتيجة عدّلتها بنفسي")

        val migrated = QuestionnaireEngine.upgradeLegacyStarterRows(legacySaved)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[0], migrated[0])
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[1].prompt, migrated[1].prompt)
        assertEquals("نتيجة عدّلتها بنفسي", migrated[1].yesConclusion)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[1].noConclusion, migrated[1].noConclusion)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[2], migrated[2])
    }

    @Test
    fun `old unsupported starter conclusions upgrade without overwriting customized rows`() {
        val oldClaims = listOf(
            EditableQuestion(1, "هل تحب آرام؟", "أنت شريف جدًا.", "أنت أبن زنا من المستوى العاشر"),
            EditableQuestion(2, "هل تحب سكولر؟", "كسمك أنت ابن زنا", "شريف والله")
        )
        val saved = QuestionnaireEngine.starterQuestionnaire().toMutableList()
        saved[0] = oldClaims[0]
        saved[1] = oldClaims[1].copy(noConclusion = "تعديل شخصي")
        val migrated = QuestionnaireEngine.upgradeLegacyStarterRows(saved)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[0], migrated[0])
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[1].prompt, migrated[1].prompt)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[1].yesConclusion, migrated[1].yesConclusion)
        assertEquals("تعديل شخصي", migrated[1].noConclusion)
    }

    @Test
    fun `only fully filled questions enter the quiz`() {
        val questions = QuestionnaireEngine.emptyQuestionnaire().toMutableList()
        questions[0] = EditableQuestion(1, "سؤال تجريبي؟", "استنتاج نعم", "استنتاج لا")
        questions[1] = EditableQuestion(2, "سؤال ناقص؟", "استنتاج نعم", "")

        assertEquals(listOf(questions[0]), QuestionnaireEngine.configuredQuestions(questions))
    }

    @Test
    fun `yes and no map to their respective user defined conclusions`() {
        val question = EditableQuestion(1, "هل؟", "النص عند نعم", "النص عند لا")
        assertEquals("النص عند نعم", QuestionnaireEngine.conclusionFor(question, true))
        assertEquals("النص عند لا", QuestionnaireEngine.conclusionFor(question, false))
        assertTrue(question.isConfigured)
    }

    @Test
    fun `weighted questionnaire result is stable and uses question weights`() {
        val questions = QuestionnaireEngine.starterQuestionnaire()
        val allYes = questions.map { SubmittedAnswer(it, true) }
        val allNo = questions.map { SubmittedAnswer(it, false) }

        assertEquals(100, QuestionnaireEngine.calculateResult(allYes).percentage)
        assertEquals(0, QuestionnaireEngine.calculateResult(allNo).percentage)
        assertEquals("ابن شرعي", QuestionnaireEngine.calculateResult(allYes).verdict)
        assertEquals("ابن زنا من العيار الثقيل", QuestionnaireEngine.calculateResult(allNo).verdict)
        val middleScore = questions.mapIndexed { index, question ->
            SubmittedAnswer(question, index < 11)
        }
        assertEquals(55, QuestionnaireEngine.calculateResult(middleScore).percentage)
        assertEquals("مثير للشبهة (مشكوك في أمر نسبك)", QuestionnaireEngine.calculateResult(middleScore).verdict)
        assertTrue(QuestionnaireEngine.weightForQuestion(1) != QuestionnaireEngine.weightForQuestion(3))
        val oneHighWeightYes = allNo.toMutableList().also { it[0] = SubmittedAnswer(questions[0], true) }
        val oneLowWeightYes = allNo.toMutableList().also { it[2] = SubmittedAnswer(questions[2], true) }
        assertTrue(
            QuestionnaireEngine.calculateResult(oneHighWeightYes).percentage >
                QuestionnaireEngine.calculateResult(oneLowWeightYes).percentage
        )
    }

    @Test
    fun `empty answers produce a safe empty result`() {
        assertEquals(0, QuestionnaireEngine.calculateResult(emptyList()).percentage)
        assertEquals("لم تكتمل الإجابات", QuestionnaireEngine.calculateResult(emptyList()).verdict)
    }

    @Test
    fun `blank question is not configured`() {
        assertFalse(EditableQuestion(1, prompt = "", yesConclusion = "نعم", noConclusion = "لا").isConfigured)
    }
}
