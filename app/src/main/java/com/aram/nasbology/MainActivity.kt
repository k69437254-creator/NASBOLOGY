package com.aram.nasbology

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.aram.nasbology.engine.EditableQuestion
import com.aram.nasbology.engine.QuestionnaireEngine
import com.aram.nasbology.engine.SubmittedAnswer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.random.Random

private val ColorBg = Color(0xFF080610)
private val ColorPanel = Color(0xFF151020)
private val ColorPurple = Color(0xFF9B5CFF)
private val ColorPink = Color(0xFFFF4F91)
private val ColorText = Color(0xFFF5F0FF)
private val ColorMuted = Color(0xFFDCD4EA)

/** IBM Plex Sans Arabic fonts are downloaded by the app module's Gradle preBuild task. */
private val IpmArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold)
)

private fun Typography.withArabicFont(fontFamily: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)

private val NasbologyTypography = Typography().withArabicFont(IpmArabic)
private val Context.dataStore by preferencesDataStore(name = "nasbology_settings")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun promptKey(number: Int) = stringPreferencesKey("question_prompt_$number")
private fun yesKey(number: Int) = stringPreferencesKey("question_yes_$number")
private fun noKey(number: Int) = stringPreferencesKey("question_no_$number")

private fun loadQuestions(prefs: Preferences): List<EditableQuestion> {
    val saved = (1..QuestionnaireEngine.SLOT_COUNT).map { number ->
        EditableQuestion(
            number = number,
            prompt = prefs[promptKey(number)] ?: "",
            yesConclusion = prefs[yesKey(number)] ?: "",
            noConclusion = prefs[noKey(number)] ?: ""
        )
    }
    val hasSavedQuestionData = (1..QuestionnaireEngine.SLOT_COUNT).any { number ->
        prefs.contains(promptKey(number)) || prefs.contains(yesKey(number)) || prefs.contains(noKey(number))
    }
    return if (hasSavedQuestionData) QuestionnaireEngine.upgradeLegacyStarterRows(saved) else QuestionnaireEngine.starterQuestionnaire()
}

private suspend fun saveQuestions(
    context: Context,
    questions: List<EditableQuestion>
) {
    context.dataStore.edit { prefs ->
        questions.forEach { question ->
            prefs[promptKey(question.number)] = question.prompt
            prefs[yesKey(question.number)] = question.yesConclusion
            prefs[noKey(question.number)] = question.noConclusion
        }
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NasbologyApp() }
    }
}

@Composable
private fun NasbologyApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = androidx.datastore.preferences.core.emptyPreferences())
    val soundEnabled = prefs[booleanPreferencesKey("sound_enabled")] ?: true
    val saveHistory = prefs[booleanPreferencesKey("save_history")] ?: false
    var questions by remember { mutableStateOf(QuestionnaireEngine.starterQuestionnaire()) }
    LaunchedEffect(Unit) { questions = QuestionnaireEngine.starterQuestionnaire() }
    var page by remember { mutableStateOf("welcome") }
    var showSettings by remember { mutableStateOf(false) }
    var quizQuestions by remember { mutableStateOf<List<EditableQuestion>>(emptyList()) }
    var questionIndex by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf<List<SubmittedAnswer>>(emptyList()) }
    var selectedConclusion by remember { mutableStateOf("") }
    var showBiometricFallback by remember { mutableStateOf(false) }
    var biometricFallbackMessage by remember { mutableStateOf("") }
    var biometricResult by remember { mutableStateOf("") }
    var biometricVerified by remember { mutableStateOf(false) }

    val requestBiometric: () -> Unit = {
        biometricVerified = false
        val activity = context.findActivity() as? FragmentActivity
        if (activity == null) {
            biometricFallbackMessage = "تعذر فتح نافذة التحقق على هذا الجهاز. يمكنك متابعة الاختبار دون بصمة."
            showBiometricFallback = true
        } else {
            when (BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                biometricVerified = true
                                page = "fingerprintReady"
                            }

                            override fun onAuthenticationFailed() {
                                // Keep the system prompt open so the user can retry.
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                    errorCode == BiometricPrompt.ERROR_CANCELED
                                ) return

                                biometricFallbackMessage = "تعذر إكمال التحقق: $errString\nيمكنك إعادة المحاولة أو متابعة الاختبار دون بصمة."
                                showBiometricFallback = true
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("نسبولوجي | التحقق بالبصمة")
                        .setSubtitle("خطوة ترفيهية قبل الاختبار")
                        .setDescription("التحقق يؤكد استخدام وسيلة بيومترية مسجلة على هذا الجهاز فقط، ولا يكشف النسب.")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .setNegativeButtonText("إلغاء")
                        .build()
                    prompt.authenticate(promptInfo)
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    biometricFallbackMessage = "لا توجد بصمة أو وسيلة بيومترية مسجلة في إعدادات الهاتف. يمكنك إعدادها في النظام أو متابعة الاختبار دونها."
                    showBiometricFallback = true
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    biometricFallbackMessage = "هذا الجهاز لا يوفر مستشعرًا بيومتريًا متوافقًا. يمكنك متابعة الاختبار دون بصمة."
                    showBiometricFallback = true
                }
                else -> {
                    biometricFallbackMessage = "التحقق البيومتري غير متاح حاليًا على هذا الجهاز. يمكنك متابعة الاختبار دون بصمة."
                    showBiometricFallback = true
                }
            }
        }
    }

    MaterialTheme(
        typography = NasbologyTypography,
        colorScheme = darkColorScheme(
            primary = ColorPurple,
            secondary = ColorPink,
            background = ColorBg,
            surface = ColorPanel,
            onBackground = ColorText,
            onSurface = ColorText
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF170B2B), ColorBg, Color(0xFF090711)))
                )
            ) {
                when (page) {
                    "welcome" -> WelcomeScreen(
                        onReady = requestBiometric,
                        onDeveloper = { page = "developer" },
                        onEdit = { page = "editor" }
                    )
                    "fingerprintReady" -> FingerprintReadyScreen(
                        isVerified = biometricVerified,
                        onScan = { page = "biometricScan" },
                        onBack = { page = "welcome" }
                    )
                    "biometricScan" -> BiometricScanScreen(
                        onComplete = {
                            scope.launch {
                                biometricResult = getOrCreateBiometricResult(context)
                                page = "biometricResult"
                            }
                        }
                    )
                    "biometricResult" -> BiometricResultScreen(
                        result = biometricResult,
                        onContinue = { page = "face" },
                        onDeveloper = { page = "developer" }
                    )
                    "face" -> CameraFaceScanScreen(
                        onContinue = {
                            quizQuestions = QuestionnaireEngine.configuredQuestions(questions)
                            questionIndex = 0
                            answers = emptyList()
                            page = "questions"
                        },
                        onBack = { page = "biometricResult" }
                    )
                    "developer" -> DeveloperScreen(onBack = { page = "welcome" })
                    "home" -> HomeScreen(
                        configuredCount = QuestionnaireEngine.configuredQuestions(questions).size,
                        onStart = requestBiometric,
                        onEdit = { page = "editor" },
                        onSettings = { showSettings = true },
                        onDeveloper = { page = "developer" }
                    )
                    "editor" -> QuestionEditorScreen(
                        questions = questions,
                        onQuestionsChange = { questions = it },
                        onSave = {
                            scope.launch {
                                saveQuestions(context, questions)
                                page = "home"
                            }
                        },
                        onBack = { page = "home" }
                    )
                    "questions" -> {
                        if (quizQuestions.isEmpty()) {
                            EmptyQuestionsScreen(onEdit = { page = "editor" }, onBack = { page = "home" })
                        } else {
                            QuestionScreen(
                                questions = quizQuestions,
                                index = questionIndex,
                                onBack = {
                                    if (questionIndex == 0) page = "face" else {
                                    questionIndex--
                                    if (answers.isNotEmpty()) answers = answers.dropLast(1)
                                }
                                },
                                onAnswer = { answerIsYes ->
                                    val answer = SubmittedAnswer(quizQuestions[questionIndex], answerIsYes)
                                    answers = answers + answer
                                    selectedConclusion = answer.conclusion
                                    page = "conclusion"
                                }
                            )
                        }
                    }
                    "conclusion" -> ConclusionScreen(
                        index = questionIndex,
                        total = quizQuestions.size,
                        conclusion = selectedConclusion,
                        onContinue = {
                            if (questionIndex < quizQuestions.lastIndex) {
                                questionIndex++
                                page = "questions"
                            } else {
                                if (soundEnabled) runCatching {
                                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_ACK, 180)
                                }
                                if (saveHistory) {
                                    scope.launch {
                                        val reportText = answers.joinToString("\n\n") { a ->
                                            "${a.question.prompt}\nالإجابة: ${if (a.answerIsYes) "نعم" else "لا"}\nالاستنتاج: ${a.conclusion}"
                                        }
                                        context.dataStore.edit { it[stringPreferencesKey("last_result")] = reportText }
                                    }
                                }
                                page = "result"
                            }
                        }
                    )
                    "result" -> ResultScreen(
                        answers = answers,
                        onAgain = { page = "home" },
                        onShare = {
                            val report = buildString {
                                append("نسبولوجي | تقرير ترفيهي خيالي\n\n")
                                answers.forEachIndexed { index, answer ->
                                    append("${index + 1}. ${answer.question.prompt}\n")
                                    append("الإجابة: ${if (answer.answerIsYes) "نعم" else "لا"}\n")
                                    append("الاستنتاج: ${answer.conclusion}\n\n")
                                }
                                append("هذا التطبيق لا يكشف النسب الحقيقي ولا يقدم استنتاجات علمية.")
                            }
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "مشاركة التقرير"))
                        }
                    )
                }
                if (showSettings) {
                    SettingsDialog(
                        soundEnabled = soundEnabled,
                        saveHistory = saveHistory,
                        onSound = { enabled -> scope.launch { context.dataStore.edit { it[booleanPreferencesKey("sound_enabled")] = enabled } } },
                        onHistory = { enabled -> scope.launch {
                            context.dataStore.edit {
                                it[booleanPreferencesKey("save_history")] = enabled
                                if (!enabled) it.remove(stringPreferencesKey("last_result"))
                            }
                        } },
                        onClose = { showSettings = false }
                    )
                }
                if (showBiometricFallback) {
                    AlertDialog(
                        onDismissRequest = { showBiometricFallback = false },
                        title = { Text("التحقق بالبصمة غير متاح") },
                        text = {
                            Text(
                                "$biometricFallbackMessage\n\nهذه خطوة ترفيهية فقط؛ التحقق بالبصمة لا يحدد النسب ولا يرسل بيانات البصمة إلى التطبيق."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showBiometricFallback = false; page = "fingerprintReady" }) { Text("متابعة إلى الفحص") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBiometricFallback = false }) { Text("إلغاء") }
                        },
                        containerColor = ColorPanel,
                        titleContentColor = ColorText,
                        textContentColor = ColorMuted
                    )
                }
            }
        }
    }
}

private val biometricResultKey = stringPreferencesKey("biometric_result_once_v2")

private val biometricResultOptions = listOf(
    "اكتملت محاكاة واجهة البصمة. لم يُجرَ فحص DNA أو تحليل للنسب.",
    "اكتمل العرض التمهيدي للبصمة؛ هذه الخطوة لا تنتج نتيجة علمية عن الأبوة أو النسب."
)

private suspend fun getOrCreateBiometricResult(context: Context): String {
    var chosen = biometricResultOptions.first()
    context.dataStore.edit { preferences ->
        val existing = preferences[biometricResultKey]
        if (existing != null) {
            chosen = existing
        } else {
            chosen = biometricResultOptions[Random.nextInt(biometricResultOptions.size)]
            preferences[biometricResultKey] = chosen
        }
    }
    return chosen
}

@Composable
private fun WelcomeScreen(onReady: () -> Unit, onDeveloper: () -> Unit, onEdit: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.size(142.dp).background(
                Brush.radialGradient(listOf(Color(0xFF512A8C), Color(0xFF170D28), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(94.dp)) }
        BrandHeader()
        Spacer(Modifier.height(8.dp))
        Text(
            "مرحبًا بك في نسبولوجي. أجب عن الأسئلة العشرين وشاهد ملخصًا بصريًا لإجاباتك.",
            fontSize = 19.sp,
            lineHeight = 31.sp,
            color = ColorText,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onReady,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Fingerprint, null)
            Spacer(Modifier.width(10.dp))
            Text("أنا جاهز", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDeveloper) {
            Icon(Icons.Default.Code, null, tint = ColorMuted)
            Spacer(Modifier.width(7.dp))
            Text("صفحة المطوّر", color = ColorMuted)
        }
    }
}

@Composable
private fun FingerprintReadyScreen(isVerified: Boolean, onScan: () -> Unit, onBack: () -> Unit) {
    ScreenFrame {
        TopBar("التحقق بالبصمة", onBack)
        Box(
            Modifier.size(150.dp).background(
                Brush.radialGradient(listOf(Color(0xFF48247D), Color(0xFF171022), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(98.dp)) }
        Text(
            if (isVerified) "تم التحقق من وسيلة البصمة المسجّلة على الهاتف."
            else "يمكنك متابعة المحاكاة دون التحقق البيومتري.",
            fontSize = 21.sp,
            lineHeight = 32.sp,
            color = ColorText,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Fingerprint, null)
            Spacer(Modifier.width(9.dp))
            Text("فحص بايومتري", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Text(
            "التطبيق لا يقرأ قالب بصمتك ولا يستنتج منه النسب.",
            color = ColorMuted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp
        )
    }
}

@Composable
private fun BiometricScanScreen(onComplete: () -> Unit) {
    var scanPercent by remember { mutableIntStateOf(0) }
    val pulse by rememberInfiniteTransition(label = "biometricPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    LaunchedEffect(Unit) {
        for (step in 1..20) {
            scanPercent = step * 5
            delay(130)
        }
        onComplete()
    }
    ScreenFrame {
        Spacer(Modifier.height(42.dp))
        Box(
            Modifier.size(176.dp).scale(pulse).background(
                Brush.radialGradient(listOf(Color(0xFF5C2D91), Color(0xFF1A1028), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(112.dp)) }
        Text("جارٍ الفحص البايومتري...", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { scanPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = ColorPurple,
            trackColor = ColorPanel
        )
        Text("$scanPercent%", color = ColorPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("يرجى الانتظار حتى اكتمال المؤثر البصري.", color = ColorMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BiometricResultScreen(result: String, onContinue: () -> Unit, onDeveloper: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Default.FactCheck, null, tint = ColorPurple, modifier = Modifier.size(66.dp))
        Text("نتيجة الفحص البايومتري", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1B1027),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ColorPurple)
        ) {
            Text(result, modifier = Modifier.padding(20.dp), color = ColorText, fontSize = 19.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
        }
        Text(
            "هذه محاكاة واجهة فقط؛ التحقق من بصمة الهاتف لا يفحص DNA ولا يحدد النسب.",
            color = ColorMuted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) {
            Icon(Icons.Default.Face, null)
            Spacer(Modifier.width(8.dp))
            Text("الانتقال إلى فحص الوجه", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDeveloper) { Text("صفحة المطوّر", color = ColorMuted) }
    }
}

@Composable
private fun DeveloperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenFrame {
        TopBar("صفحة المطوّر", onBack)
        Spacer(Modifier.height(30.dp))
        Box(
            Modifier.size(118.dp).background(
                Brush.radialGradient(listOf(Color(0xFF58328B), Color(0xFF171022), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Code, null, tint = ColorPurple, modifier = Modifier.size(64.dp)) }
        Text("Aram Aoyal", fontSize = 27.sp, fontWeight = FontWeight.Black, color = ColorText)
        Text("مطور تطبيق نسبولوجي", color = ColorMuted, fontSize = 15.sp)
        Button(
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://t.me/ro_gy")))
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("التواصل عبر Telegram", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CameraFaceScanScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val detectorOptions = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    }
    val detector = remember { FaceDetection.getClient(detectorOptions) }
    DisposableEffect(detector) { onDispose { detector.close() } }
    var faceDetected by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(8) }
    var scanComplete by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(permissionGranted, lifecycleOwner, previewView) {
        if (!permissionGranted) {
            onDispose { }
        } else {
            val executor = Executors.newSingleThreadExecutor()
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var providerToUnbind: ProcessCameraProvider? = null
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    providerToUnbind = provider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                        } else {
                            val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            detector.process(input)
                                .addOnSuccessListener { faces -> faceDetected = faces.isNotEmpty() }
                                .addOnFailureListener { cameraError = "تعذر تحليل إطار الكاميرا." }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    cameraError = "تعذر تشغيل الكاميرا الأمامية على هذا الجهاز."
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching { providerToUnbind?.unbindAll() }
                executor.shutdown()
            }
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            secondsLeft = 8
            scanComplete = false
            for (seconds in 8 downTo 1) {
                secondsLeft = seconds
                delay(1000)
            }
            secondsLeft = 0
            scanComplete = true
        }
    }

    ScreenFrame {
        TopBar("فحص الوجه", onBack)
        Text("ضع وجهك أمام الكاميرا", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (permissionGranted) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                shape = RoundedCornerShape(22.dp),
                color = ColorPanel,
                border = BorderStroke(1.dp, ColorPurple)
            ) {
                Box {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.Center).size(210.dp, 260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            shape = RoundedCornerShape(110.dp),
                            border = BorderStroke(2.dp, if (faceDetected) Color(0xFF67E8A5) else ColorPurple)
                        ) {}
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        color = Color(0xCC090711), shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (scanComplete) "اكتمل فحص الوجه" else "المسح جارٍ • $secondsLeft ثوانٍ",
                            color = if (faceDetected) Color(0xFF8FF0B7) else ColorText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                when {
                    scanComplete && faceDetected -> "تم رصد وجه أمام الكاميرا. لم تُلتقط صورة ولم تُحفظ بيانات الوجه."
                    scanComplete -> "انتهت مدة المسح، لكن لم يتم رصد وجه واضح. يمكنك المتابعة أو إعادة فتح الفحص."
                    faceDetected -> "تم رصد وجه؛ يستمر المسح حتى إكمال الثواني الثماني."
                    else -> "سيعمل العرض المباشر لمدة 8 ثوانٍ. لا تُحفظ الصور في التطبيق."
                },
                color = ColorMuted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
            if (cameraError != null) Text(cameraError!!, color = ColorPink, fontSize = 12.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onContinue,
                enabled = scanComplete,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPurple, disabledContainerColor = Color(0xFF3B2B50))
            ) {
                Text(if (scanComplete) "متابعة إلى الأسئلة" else "يرجى إكمال المسح", fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.Default.CameraAlt, null, tint = ColorPurple, modifier = Modifier.size(58.dp))
            Text(
                if (permissionDenied) "يحتاج فحص الوجه إلى إذن الكاميرا. لم يتم التقاط أو تخزين أي صورة." else "بانتظار إذن الكاميرا…",
                color = ColorMuted, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) {
                Text("السماح باستخدام الكاميرا")
            }
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("متابعة بدون فحص الوجه") }
        }
    }
}

@Composable
private fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) { item { Column(horizontalAlignment = Alignment.CenterHorizontally, content = content) } }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("نسبولوجي", fontSize = 28.sp, fontWeight = FontWeight.Black, color = ColorText)
            Text("NASBOLOGY", fontSize = 10.sp, color = ColorMuted, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun Disclaimer() {
    Surface(
        color = Color(0xFF21152D),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF51346E))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = ColorPink, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "هذا الملخص يحصي الكلمات في النصوص المحددة فقط؛ لا يكشف النسب ولا يثبت الأبوة.",
                color = ColorMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun HomeScreen(
    configuredCount: Int,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onDeveloper: () -> Unit
) {
    ScreenFrame {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "الإعدادات", tint = ColorMuted) }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(136.dp).background(
                Brush.radialGradient(listOf(Color(0xFF512A8C), Color(0xFF170D28), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(92.dp)) }
        BrandHeader()
        Text("اختبارك يبدأ الآن", color = ColorPink, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "أجب عن الأسئلة العشرين وشاهد ملخصًا بصريًا للكلمات الواردة في الاستنتاجات.",
            color = ColorMuted, textAlign = TextAlign.Center, lineHeight = 23.sp
        )
        Surface(color = ColorPanel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF51346E))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, null, tint = ColorPurple, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("الأسئلة المُعدّة", fontWeight = FontWeight.Bold)
                    Text("$configuredCount من ${QuestionnaireEngine.SLOT_COUNT} سؤالًا مكتملًا", color = ColorMuted, fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = configuredCount > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Science, null)
            Spacer(Modifier.width(10.dp))
            Text("ابدأ الاختبار", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onDeveloper, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Code, null)
            Spacer(Modifier.width(8.dp))
            Text("صفحة المطوّر")
        }
    }
}

@Composable
private fun QuestionEditorScreen(
    questions: List<EditableQuestion>,
    onQuestionsChange: (List<EditableQuestion>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar("إعداد الأسئلة", onBack)
            Text("20 خانة قابلة للتعديل", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                "اكتب نص السؤال واستنتاجين مخصصين له. لن يدخل السؤال في الاختبار إلا إذا امتلأت الحقول الثلاثة.",
                color = ColorMuted, lineHeight = 21.sp
            )
            Spacer(Modifier.height(3.dp))
        }
        items(questions, key = { it.number }) { question ->
            val index = question.number - 1
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ColorPanel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (question.isConfigured) Color(0xFF6B3CB5) else Color(0xFF342740))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("السؤال ${question.number}", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(if (question.isConfigured) "جاهز" else "غير مكتمل", color = if (question.isConfigured) Color(0xFFB9F6CA) else ColorMuted, fontSize = 11.sp)
                    }
                    OutlinedTextField(
                        value = question.prompt,
                        onValueChange = { value -> onQuestionsChange(questions.updated(index) { copy(prompt = value) }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نص السؤال") },
                        placeholder = { Text("اكتب السؤال هنا") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = question.yesConclusion,
                        onValueChange = { value -> onQuestionsChange(questions.updated(index) { copy(yesConclusion = value) }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الاستنتاج عند الإجابة: نعم") },
                        placeholder = { Text("اكتب الاستنتاج الذي تريد إظهاره") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = question.noConclusion,
                        onValueChange = { value -> onQuestionsChange(questions.updated(index) { copy(noConclusion = value) }) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الاستنتاج عند الإجابة: لا") },
                        placeholder = { Text("اكتب الاستنتاج البديل") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("حفظ الأسئلة على الجهاز")
            }
            Text("تُحفظ الأسئلة والاستنتاجات محليًا، وتبقى محفوظة بعد إغلاق التطبيق.", color = ColorMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun List<EditableQuestion>.updated(index: Int, transform: EditableQuestion.() -> EditableQuestion): List<EditableQuestion> =
    toMutableList().also { it[index] = it[index].transform() }

@Composable
private fun QuestionScreen(
    questions: List<EditableQuestion>,
    index: Int,
    onBack: () -> Unit,
    onAnswer: (Boolean) -> Unit
) {
    val question = questions[index]
    ScreenFrame {
        TopBar("الاختبار", onBack)
        Text("السؤال ${index + 1} من ${questions.size}", color = ColorPurple, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { (index + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth(), color = ColorPurple, trackColor = ColorPanel
        )
        Text(question.prompt, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 31.sp)
        listOf(true to "نعم", false to "لا").forEach { (answer, label) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onAnswer(answer) },
                color = ColorPanel,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, if (answer) ColorPurple else ColorPink)
            ) {
                Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (answer) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (answer) ColorPurple else ColorPink)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = ColorText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ConclusionScreen(index: Int, total: Int, conclusion: String, onContinue: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(35.dp))
        Icon(Icons.Default.Article, null, tint = ColorPurple, modifier = Modifier.size(64.dp))
        Text("الاستنتاج المحدد لهذا الجواب", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth(), color = Color(0xFF1B1027),
            shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, ColorPurple)
        ) {
            Text(conclusion, color = ColorText, fontSize = 19.sp, lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(22.dp))
        }
        Text("هذا النص كتبتموه أنتم، وليس نتيجة علمية.", color = ColorMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) {
            Text(if (index < total - 1) "السؤال التالي" else "عرض التقرير الكامل")
        }
    }
}

@Composable
private fun EmptyQuestionsScreen(onEdit: () -> Unit, onBack: () -> Unit) {
    ScreenFrame {
        TopBar("لا توجد أسئلة مكتملة", onBack)
        Icon(Icons.Default.EditNote, null, tint = ColorPurple, modifier = Modifier.size(62.dp))
        Text("أكمل سؤالًا واحدًا على الأقل", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("يحتاج كل سؤال إلى نص، واستنتاج عند نعم، واستنتاج آخر عند لا.", color = ColorMuted, textAlign = TextAlign.Center)
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) { Text("فتح محرر الأسئلة") }
    }
}

@Composable
private fun ResultScreen(answers: List<SubmittedAnswer>, onAgain: () -> Unit, onShare: () -> Unit) {
    val flaggedCount = answers.count { it.conclusion.contains("زنا") }
    val otherCount = answers.size - flaggedCount
    val total = answers.size.coerceAtLeast(1)
    val flaggedPercent = (flaggedCount * 100) / total
    val otherPercent = (otherCount * 100) / total
    val visualStyle = remember { listOf("إضاءة هادئة", "إضاءة سينمائية").random() }
    ScreenFrame {
        TopBar("التقرير النهائي", onBack = onAgain)
        Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(64.dp))
        Text("تقرير الإجابات والاستنتاجات", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${answers.size} استنتاجًا حسب الإجابات المختارة", color = ColorMuted, textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF191226),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color(0xFF6B49A3))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FactCheck, null, tint = ColorPurple, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("مؤشر لغوي للإجابات", color = ColorText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text("استنتاجات تتضمن كلمة «زنا»", color = ColorMuted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { flaggedPercent / 100f },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = ColorPink,
                        trackColor = Color(0xFF35263E)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("$flaggedPercent%", color = ColorText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("$flaggedCount من ${answers.size} إجابة", color = ColorText, fontSize = 13.sp)
                HorizontalDivider(color = Color(0xFF35263E))
                Text("استنتاجات لا تتضمن الكلمة", color = ColorMuted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { otherPercent / 100f },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = Color(0xFF7BD6AC),
                        trackColor = Color(0xFF35263E)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("$otherPercent%", color = ColorText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("$otherCount من ${answers.size} إجابة", color = ColorText, fontSize = 13.sp)
                HorizontalDivider(color = Color(0xFF35263E))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("مؤشر البصمة", color = ColorText, fontWeight = FontWeight.Bold)
                        Text("عرض واجهة فقط — لا توجد قراءة للنسب", color = ColorMuted, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, null, tint = ColorPurple, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("الوصف البصري", color = ColorText, fontWeight = FontWeight.Bold)
                        Text("$visualStyle • اختيار عشوائي غير مستنتج من الوجه", color = ColorMuted, fontSize = 12.sp)
                    }
                }
                Text(
                    "النسب المئوية تصف الكلمات الموجودة في الاستنتاجات المحددة فقط، ولا تعني نسبة شرعية أو زنا.",
                    color = ColorText, fontSize = 13.sp, lineHeight = 20.sp
                )
            }
        }
        answers.forEachIndexed { index, answer ->
            Surface(
                modifier = Modifier.fillMaxWidth(), color = ColorPanel,
                shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFF51346E))
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${answer.question.prompt}", color = ColorText, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجابة: ", color = ColorMuted, fontSize = 13.sp)
                        Text(if (answer.answerIsYes) "نعم" else "لا", color = if (answer.answerIsYes) Color(0xFFB9F6CA) else ColorPink, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color(0xFF342740))
                    Text("الاستنتاج", color = ColorPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(answer.conclusion, color = ColorText, lineHeight = 23.sp)
                }
            }
        }
        Disclaimer()
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("مشاركة التقرير")
        }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("العودة للرئيسية")
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع", tint = ColorText) }
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsDialog(
    soundEnabled: Boolean,
    saveHistory: Boolean,
    onSound: (Boolean) -> Unit,
    onHistory: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(22.dp), color = ColorPanel,
            border = BorderStroke(1.dp, ColorPurple)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("إعدادات المختبر", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = ColorPurple)
                    Spacer(Modifier.width(10.dp))
                    Text("المؤثر الصوتي عند إنهاء الاختبار", modifier = Modifier.weight(1f))
                    Switch(checked = soundEnabled, onCheckedChange = { enabled ->
                        onSound(enabled)
                        if (enabled) runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_ACK, 100) }
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = ColorPurple)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("حفظ آخر تقرير نصيًا")
                        Text("تخزين محلي اختياري", color = ColorMuted, fontSize = 11.sp)
                    }
                    Switch(checked = saveHistory, onCheckedChange = onHistory)
                }
                Text("الصور لا تُرفع إلى خادم من هذه النسخة.", color = ColorMuted, fontSize = 12.sp)
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) { Text("تم") }
            }
        }
    }
}
