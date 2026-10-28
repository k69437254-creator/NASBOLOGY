package com.aram.nasbology

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Environment
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import android.provider.MediaStore
import android.text.Layout
import android.text.TextDirectionHeuristics
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.security.MessageDigest
import java.util.Locale
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
private val ColorMuted = Color(0xFFD7CDE5)

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
    LaunchedEffect(Unit) { questions = loadQuestions(context.dataStore.data.first()) }
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
    var participantName by remember { mutableStateOf("") }
    val faceResult = remember(biometricResult) {
        if (biometricResult.contains("الغموض")) "ملامح سينمائية بطابع غامض"
        else "ملامح سينمائية بطابع مشرق"
    }

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
                        .setTitle("نسبولوجي | التحقق التمهيدي")
                        .setSubtitle("ضع إصبعك لإكمال التجربة")
                        .setDescription("استخدم وسيلة التحقق المسجلة على هاتفك للانتقال إلى الخطوة التالية.")
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
                        name = participantName,
                        onNameChange = { participantName = it },
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
                                biometricResult = getOrCreateBiometricResult(context, participantName)
                                page = "biometricResult"
                            }
                        }
                    )
                    "biometricResult" -> BiometricResultScreen(
                        result = biometricResult,
                        onContinue = { page = "face" }
                    )
                    "face" -> CameraFaceScanScreen(
                        faceResult = faceResult,
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
                                        val finalScore = QuestionnaireEngine.calculateResult(answers)
                                        val answersText = answers.joinToString("\n\n") { a ->
                                            "${a.question.prompt}\nالإجابة: ${if (a.answerIsYes) "نعم" else "لا"}\nالاستنتاج: ${a.conclusion}"
                                        }
                                        val reportText = "النتيجة النهائية: ${finalScore.percentage}%\n${finalScore.verdict}\n\n$answersText"
                                        context.dataStore.edit { it[stringPreferencesKey("last_result")] = reportText }
                                    }
                                }
                                page = "result"
                            }
                        }
                    )
                    "result" -> ResultScreen(
                        name = participantName,
                        answers = answers,
                        biometricResult = biometricResult,
                        faceResult = faceResult,
                        onAgain = { page = "home" },
                        onSaveReport = { saveReportImage(context, participantName, answers, biometricResult, faceResult) },
                        onShareReport = { shareReportImage(context, participantName, answers, biometricResult, faceResult) }
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
                            Text(biometricFallbackMessage)
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

private val legacyBiometricResultKey = stringPreferencesKey("biometric_result_once_v2")

private val biometricResultOptions = listOf(
    "نتيجة المحاكاة: ملفّك يحمل أجواء الغموض، والقصة ما زالت مفتوحة.",
    "نتيجة المحاكاة: ملفّك يحمل أجواء التفاؤل، والقصة تحمل مفاجآت جميلة."
)

private fun biometricResultKeyFor(name: String) = run {
    val normalizedName = name.trim().lowercase(Locale.ROOT).ifBlank { "guest" }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedName.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    stringPreferencesKey("biometric_result_v3_$digest")
}

/** One locally persisted simulation outcome per entered name; no IP address or biometric data is collected. */
private suspend fun getOrCreateBiometricResult(context: Context, name: String): String {
    val key = biometricResultKeyFor(name)
    var chosen = biometricResultOptions.first()
    context.dataStore.edit { preferences ->
        val existing = preferences[key]
        if (existing != null) {
            chosen = existing
        } else {
            val legacy = preferences[legacyBiometricResultKey]
            chosen = legacy ?: biometricResultOptions[Random.nextInt(biometricResultOptions.size)]
            preferences[key] = chosen
            if (legacy != null) preferences.remove(legacyBiometricResultKey)
        }
    }
    return chosen
}

@Composable
private fun WelcomeScreen(
    name: String,
    onNameChange: (String) -> Unit,
    onReady: () -> Unit,
    onDeveloper: () -> Unit,
    onEdit: () -> Unit
) {
    ScreenFrame {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(154.dp).background(
                Brush.radialGradient(listOf(Color(0xFF6C3CB2), Color(0xFF231333), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Science, null, tint = ColorPurple, modifier = Modifier.size(88.dp))
        }
        BrandHeader()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xCC151020),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color(0xFF654198))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Biotech, null, tint = ColorPurple, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مختبر نسبولوجي", color = ColorText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(
                    "مرحبًا بك في مختبر نسبولوجي الخاص بتجربة آرام أويال. خض جولة تفاعلية في عالم الأسئلة والمحاكاة، واكتشف كيف تتغير خلاصة تجربتك مع اختياراتك. هل أنت مستعد لأخذ جولة في مختبري؟",
                    fontSize = 17.sp, lineHeight = 28.sp, color = ColorText, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium
                )
            }
        }
        Text("إذا كنت مستعدًا، اكتب اسمك هنا", color = ColorText, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("الاسم") },
            placeholder = { Text("اكتب اسمك") },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = ColorPurple) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ColorText, unfocusedTextColor = ColorText,
                focusedBorderColor = ColorPurple, unfocusedBorderColor = Color(0xFF5A496E),
                focusedLabelColor = ColorPurple, unfocusedLabelColor = ColorMuted,
                cursorColor = ColorPurple
            )
        )
        Button(
            onClick = onReady,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Fingerprint, null)
            Spacer(Modifier.width(10.dp))
            Text("ابدأ الفحص", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EditNote, null)
            Spacer(Modifier.width(8.dp))
            Text("تعديل الأسئلة")
        }
        Spacer(Modifier.height(4.dp))
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
        TopBar("التحقق التمهيدي", onBack)
        Box(
            Modifier.size(150.dp).background(
                Brush.radialGradient(listOf(Color(0xFF48247D), Color(0xFF171022), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(98.dp)) }
        Text(
            if (isVerified) "تم التحقق من بصمات إصبعك" else "يمكنك المتابعة إلى الخطوة التالية",
            fontSize = 21.sp, lineHeight = 32.sp, color = ColorText,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Fingerprint, null)
            Spacer(Modifier.width(9.dp))
            Text("ابدأ مسح البصمة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
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
        for (step in 1..25) {
            scanPercent = step * 4
            delay(100)
        }
        onComplete()
    }
    ScreenFrame {
        Spacer(Modifier.height(34.dp))
        Box(
            Modifier.size(190.dp).scale(pulse).background(
                Brush.radialGradient(listOf(Color(0xFF7C45C8), Color(0xFF2A1740), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(145.dp), color = Color(0xFF100B1A), shape = CircleShape,
                border = BorderStroke(2.dp, ColorPurple)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(102.dp))
                }
            }
        }
        Text("مسح البصمة", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = ColorText)
        LinearProgressIndicator(
            progress = { scanPercent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = ColorPurple, trackColor = ColorPanel
        )
        Text("$scanPercent%", color = ColorPurple, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("جارٍ التحليل", color = ColorText, textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BiometricResultScreen(result: String, onContinue: () -> Unit) {
    val isMysterious = result.contains("الغموض")
    val pulse by rememberInfiniteTransition(label = "resultGlow").animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "resultPulse"
    )
    ScreenFrame {
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.size(112.dp).scale(pulse).background(
                Brush.radialGradient(listOf(Color(0xFF6D3AB1), Color(0xFF20132F), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.FactCheck, null, tint = ColorPurple, modifier = Modifier.size(62.dp))
        }
        Text("نتيجة التجربة", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth(), color = Color(0xFF1B1027), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (isMysterious) ColorPink else ColorPurple)
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (isMysterious) Icons.Default.AutoAwesome else Icons.Default.Stars,
                    null, tint = if (isMysterious) ColorPink else ColorPurple, modifier = Modifier.size(40.dp)
                )
                Text(result, color = ColorText, fontSize = 20.sp, lineHeight = 31.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        Button(
            onClick = onContinue, modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Face, null)
            Spacer(Modifier.width(8.dp))
            Text("الانتقال إلى مشهد الوجه", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
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
private fun CameraFaceScanScreen(faceResult: String, onContinue: () -> Unit, onBack: () -> Unit) {
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

    DisposableEffect(permissionGranted, scanComplete, lifecycleOwner, previewView) {
        if (!permissionGranted || scanComplete) {
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
        TopBar("مشهد الوجه", onBack)
        Text("ضع وجهك أمام الكاميرا", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = ColorText)
        if (permissionGranted) {
            if (!scanComplete) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(360.dp), shape = RoundedCornerShape(26.dp),
                    color = ColorPanel, border = BorderStroke(1.dp, ColorPurple)
                ) {
                    Box {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        val scanLineProgress by rememberInfiniteTransition(label = "faceScanLine").animateFloat(
                            initialValue = -1f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                            label = "faceScanLineProgress"
                        )
                        Box(Modifier.align(Alignment.Center).size(210.dp, 260.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.fillMaxSize(), color = Color.Transparent,
                                shape = RoundedCornerShape(110.dp),
                                border = BorderStroke(2.dp, if (faceDetected) Color(0xFF67E8A5) else ColorPurple)
                            ) {}
                            Box(
                                modifier = Modifier
                                    .width(176.dp)
                                    .height(2.dp)
                                    .offset(y = (scanLineProgress * 112f).dp)
                                    .background(Color(0xFFB99AFF), RoundedCornerShape(2.dp))
                            )
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            color = Color(0xCC090711), shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "المسح جارٍ • $secondsLeft ثوانٍ", color = if (faceDetected) Color(0xFF8FF0B7) else ColorText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (cameraError != null) Text(cameraError!!, color = ColorPink, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else {
                val pulse by rememberInfiniteTransition(label = "faceResultGlow").animateFloat(
                    initialValue = 0.96f, targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "faceResultPulse"
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(), color = Color(0xFF171020),
                    shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, ColorPurple)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.size(128.dp).scale(pulse).background(
                                Brush.radialGradient(listOf(Color(0xFF7043A8), Color(0xFF20142E), Color.Transparent)), CircleShape
                            ), contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Face, null, tint = ColorPurple, modifier = Modifier.size(68.dp)) }
                        Text("اكتمل المشهد", fontSize = 22.sp, color = ColorText, fontWeight = FontWeight.Bold)
                        Text(faceResult, fontSize = 21.sp, color = ColorText, lineHeight = 31.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPurple), shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(Icons.Default.Psychology, null)
                    Spacer(Modifier.width(8.dp))
                    Text("الانتقال إلى جلسة المستشار النفسي", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Icon(Icons.Default.CameraAlt, null, tint = ColorPurple, modifier = Modifier.size(58.dp))
            Text(
                if (permissionDenied) "إذن الكاميرا مطلوب لعرض مشهد الوجه." else "بانتظار إذن الكاميرا…",
                color = ColorMuted, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)) {
                Text("السماح باستخدام الكاميرا")
            }
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("متابعة إلى جلسة المستشار النفسي") }
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
        Text(
            "مرحبًا بك في نسبولوجي؛ خض تجربة تفاعلية تتكوّن من مشاهد محاكاة وأسئلة واختيارات، ثم شاهد نتيجتك النهائية في تقرير واحد.",
            color = ColorText, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 28.sp
        )
        Button(
            onClick = onStart, enabled = configuredCount > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple)
        ) {
            Icon(Icons.Default.Science, null)
            Spacer(Modifier.width(10.dp))
            Text("ابدأ الفحص", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text("إضافة الأسئلة وتعديل الاستنتاجات")
        }
        if (configuredCount == 0) Text("لا توجد أسئلة مكتملة", color = ColorMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
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
        TopBar("جلسة المستشار النفسي", onBack)
        Surface(
            modifier = Modifier.fillMaxWidth(), color = Color(0xFF171020),
            shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFF634391))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null, tint = ColorPurple, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("الحوار ${index + 1} من ${questions.size}", color = ColorPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                LinearProgressIndicator(
                    progress = { (index + 1f) / questions.size }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = ColorPurple, trackColor = Color(0xFF362844)
                )
                Text(question.prompt, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 33.sp, color = ColorText)
            }
        }
        listOf(true to "نعم، هذا ينطبق عليّ" to ColorPurple, false to "لا، هذا لا ينطبق عليّ" to ColorPink).forEach { triple ->
            val answer = triple.first.first
            val label = triple.first.second
            val accent = triple.second
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onAnswer(answer) },
                color = Color(0xFF151020), shape = RoundedCornerShape(17.dp),
                border = BorderStroke(1.dp, accent)
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 21.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (answer) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(13.dp))
                    Text(label, color = ColorText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowBackIosNew, null, tint = ColorMuted, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun ConclusionScreen(index: Int, total: Int, conclusion: String, onContinue: () -> Unit) {
    ScreenFrame {
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(), color = Color(0xFF171020),
            shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color(0xFF634391))
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Icon(Icons.Default.PsychologyAlt, null, tint = ColorPurple, modifier = Modifier.size(58.dp))
                Text("خلاصة الجلسة", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
                LinearProgressIndicator(
                    progress = { (index + 1f) / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = ColorPurple, trackColor = Color(0xFF362844)
                )
                Text("المحور ${index + 1} من $total", color = ColorPurple, fontWeight = FontWeight.Bold)
                Text(conclusion, color = ColorText, fontSize = 19.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Button(
            onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple), shape = RoundedCornerShape(17.dp)
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(Modifier.width(8.dp))
            Text(if (index < total - 1) "متابعة الحوار" else "عرض الملخص النهائي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
private fun ResultScreen(
    name: String,
    answers: List<SubmittedAnswer>,
    biometricResult: String,
    faceResult: String,
    onAgain: () -> Unit,
    onSaveReport: () -> Unit,
    onShareReport: () -> Unit
) {
    val analysis = remember(answers) { QuestionnaireEngine.calculateResult(answers) }
    val percent = analysis.percentage
    val verdict = analysis.verdict
    var fingerprintExpanded by remember { mutableStateOf(false) }
    var faceExpanded by remember { mutableStateOf(false) }

    ScreenFrame {
        TopBar("تقرير المستشار النفسي", onBack = onAgain)
        Box(
            Modifier.size(110.dp).background(
                Brush.radialGradient(listOf(Color(0xFF6339A1), Color(0xFF20132F), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Psychology, null, tint = ColorPurple, modifier = Modifier.size(65.dp)) }
        Text("تقرير المستشار النفسي", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
        Surface(
            modifier = Modifier.fillMaxWidth(), color = Color(0xFF171020),
            shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, ColorPurple)
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("$percent%", color = ColorText, fontSize = 48.sp, fontWeight = FontWeight.Black)
                LinearProgressIndicator(
                    progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = ColorPurple, trackColor = Color(0xFF35263E)
                )
                Text(verdict, color = ColorText, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 27.sp)
                Text("التقرير الشخصي لـ ${name.ifBlank { "زائر نسبولوجي" }}", color = ColorMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        ResultDetailAccordion(
            title = "نتيجة مشهد البصمة", detail = biometricResult,
            icon = { Icon(Icons.Default.Fingerprint, null, tint = ColorPurple, modifier = Modifier.size(30.dp)) },
            expanded = fingerprintExpanded, onToggle = { fingerprintExpanded = !fingerprintExpanded }
        )
        ResultDetailAccordion(
            title = "نتيجة مشهد الوجه", detail = faceResult,
            icon = { Icon(Icons.Default.Face, null, tint = ColorPurple, modifier = Modifier.size(30.dp)) },
            expanded = faceExpanded, onToggle = { faceExpanded = !faceExpanded }
        )
        Button(
            onClick = onSaveReport, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorPurple), shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("حفظ التقرير كصورة", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Share, null, tint = ColorText)
            Spacer(Modifier.width(8.dp))
            Text("مشاركة التقرير المصوّر", color = ColorText, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Refresh, null, tint = ColorText)
            Spacer(Modifier.width(8.dp))
            Text("العودة إلى القائمة الرئيسية", color = ColorText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultDetailAccordion(
    title: String,
    detail: String,
    icon: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = Color(0xFF171020),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF634391))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(Modifier.width(12.dp))
                Text(title, color = ColorText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "إخفاء التفاصيل" else "عرض التفاصيل",
                    tint = ColorPurple, modifier = Modifier.size(28.dp)
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(tween(280)) + androidx.compose.animation.fadeIn(tween(220)),
                exit = androidx.compose.animation.shrinkVertically(tween(220)) + androidx.compose.animation.fadeOut(tween(160))
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    HorizontalDivider(color = Color(0xFF49345F))
                    Spacer(Modifier.height(12.dp))
                    Text(detail, color = ColorMuted, fontSize = 14.sp, lineHeight = 23.sp)
                }
            }
        }
    }
}

private data class ReportTextBlock(val text: String, val size: Float, val color: Int, val bold: Boolean, val gap: Int = 24)

private fun createReportBitmap(
    name: String,
    answers: List<SubmittedAnswer>,
    biometricResult: String,
    faceResult: String
): Bitmap {
    val width = 1080
    val padding = 74
    val contentWidth = width - padding * 2
    // A paper-like report canvas with dark ink and restrained violet headings.
    val white = android.graphics.Color.rgb(39, 29, 52)
    val muted = android.graphics.Color.rgb(103, 91, 119)
    val lilac = android.graphics.Color.rgb(105, 61, 158)
    val blocks = mutableListOf<ReportTextBlock>()
    blocks += ReportTextBlock("تقرير المستشار النفسي", 48f, lilac, true, 18)
    blocks += ReportTextBlock("NASBOLOGY  •  ملف التجربة", 25f, muted, true, 36)
    blocks += ReportTextBlock(
        "مرحبًا عزيزي ${name.ifBlank { "زائر نسبولوجي" }}، هذا التقرير النهائي والكامل لتجربتك الترفيهية في نسبولوجي؛ يجمع نتيجة الاستنتاج من اختياراتك ونتيجتي مشهدَي المحاكاة في ملف واحد.",
        30f, white, false, 34
    )
    val analysis = QuestionnaireEngine.calculateResult(answers)
    val percent = analysis.percentage
    val verdict = analysis.verdict
    blocks += ReportTextBlock("النتيجة النهائية", 34f, lilac, true, 14)
    blocks += ReportTextBlock("$percent%\n$verdict", 31f, white, true, 30)
    blocks += ReportTextBlock("نتيجة مشهد البصمة", 29f, lilac, true, 10)
    blocks += ReportTextBlock(biometricResult, 27f, white, false, 26)
    blocks += ReportTextBlock("نتيجة مشهد الوجه", 29f, lilac, true, 10)
    blocks += ReportTextBlock(faceResult, 27f, white, false, 30)
    blocks += ReportTextBlock("تفاصيل الجلسة", 34f, lilac, true, 16)
    answers.forEachIndexed { index, answer ->
        blocks += ReportTextBlock("${index + 1}. ${answer.question.prompt}", 26f, white, true, 8)
        blocks += ReportTextBlock("الإجابة المختارة: ${if (answer.answerIsYes) "نعم" else "لا"}", 24f, muted, false, 8)
        blocks += ReportTextBlock("الخلاصة: ${answer.conclusion}", 24f, white, false, 25)
    }
    blocks += ReportTextBlock("Aram Aoyal", 32f, lilac, true, 8)
    blocks += ReportTextBlock("معدّ التجربة", 22f, muted, false, 24)

    fun layoutFor(block: ReportTextBlock): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = block.color
            textSize = block.size
            typeface = Typeface.create("sans-serif", if (block.bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(true)
            .setLineSpacing(8f, 1.05f)
            .build()
    }
    val layouts = blocks.map { layoutFor(it) }
    val top = 150
    val bottom = 100
    val contentHeight = layouts.indices.sumOf { layouts[it].height + blocks[it].gap }
    val height = (top + contentHeight + bottom).coerceAtLeast(1600)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(android.graphics.Color.rgb(255, 253, 250), android.graphics.Color.rgb(248, 244, 252), android.graphics.Color.rgb(255, 255, 255)),
            null, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(90, 129, 93, 164)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(RectF(26f, 26f, width - 26f, height - 26f), 38f, 38f, cardPaint)
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lilac; strokeWidth = 5f }
    canvas.drawLine(padding.toFloat(), 100f, (width - padding).toFloat(), 100f, accentPaint)
    var y = top.toFloat()
    layouts.forEachIndexed { index, layout ->
        canvas.save()
        canvas.translate(padding.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + blocks[index].gap
    }
    return bitmap
}

private fun storeReportBitmap(context: Context, bitmap: Bitmap): Uri? {
    val fileName = "Nasbology_Report_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val directory = File(baseDirectory, "Nasbology")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد التقرير")
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IllegalStateException("تعذر حفظ الصورة")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nasbology")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IllegalStateException("تعذر حفظ الصورة")
        } ?: throw IllegalStateException("تعذر فتح ملف الصورة")
        val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, ready, null, null)
        uri
    } catch (error: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}

private fun saveReportImage(
    context: Context,
    name: String,
    answers: List<SubmittedAnswer>,
    biometricResult: String,
    faceResult: String
) {
    try {
        val bitmap = createReportBitmap(name, answers, biometricResult, faceResult)
        storeReportBitmap(context, bitmap) ?: throw IllegalStateException("تعذر إنشاء صورة التقرير")
        bitmap.recycle()
        Toast.makeText(context, "تم حفظ صورة التقرير في مجلد الصور/Nasbology", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر حفظ صورة التقرير", Toast.LENGTH_LONG).show()
    }
}

private fun shareReportImage(
    context: Context,
    name: String,
    answers: List<SubmittedAnswer>,
    biometricResult: String,
    faceResult: String
) {
    try {
        val bitmap = createReportBitmap(name, answers, biometricResult, faceResult)
        val uri = storeReportBitmap(context, bitmap)
        bitmap.recycle()
        if (uri == null) throw IllegalStateException("تعذر إنشاء صورة التقرير")
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, "تقرير نسبولوجي", uri)
        }
        context.startActivity(android.content.Intent.createChooser(send, "مشاركة التقرير المصوّر"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذرت مشاركة صورة التقرير", Toast.LENGTH_LONG).show()
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
