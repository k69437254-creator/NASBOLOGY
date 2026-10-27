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
    fun `starter questionnaire contains the exact twenty supplied questions and conclusions`() {
        val expected = listOf(
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
        val questions = QuestionnaireEngine.starterQuestionnaire()
        assertEquals(expected, questions)
        assertEquals(20, questions.size)
        assertTrue(questions.all { it.isConfigured })
    }

    @Test
    fun `legacy default rows upgrade while user edited rows remain unchanged`() {
        val oldDefaults = QuestionnaireEngine.legacyStarterQuestionnaire()
        val legacySaved = oldDefaults.toMutableList()
        legacySaved[1] = legacySaved[1].copy(yesConclusion = "نتيجة عدّلتها بنفسي")

        val migrated = QuestionnaireEngine.upgradeLegacyStarterRows(legacySaved)
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[0], migrated[0])
        assertEquals(legacySaved[1], migrated[1])
        assertEquals(QuestionnaireEngine.starterQuestionnaire()[2], migrated[2])
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
    fun `blank question is not configured`() {
        assertFalse(EditableQuestion(1, prompt = "", yesConclusion = "نعم", noConclusion = "لا").isConfigured)
    }
}
