# Changelog

## 1.1.0 — NASBOLOGY
- Added the agreed Arabic welcome screen and “أنا جاهز” action.
- Added Android BiometricPrompt, an animated biometric progress screen, and a locally persisted randomized outcome.
- Added live front-camera preview and on-device ML Kit face detection for an 8-second scan without saving still images.
- Added a ready-to-use 20-question questionnaire with editable yes/no conclusions and the requested sixth question.
- Added the Aram Aoyal developer profile and a Telegram action.
- Added camera dependencies and runtime camera permission.


## 1.2.0 — الخط العربي وبنك الأسئلة
- عُيّن IBM Plex Sans Arabic خطًا افتراضيًا لأنماط Material 3 في التطبيق.
- أُضيفت مهمة Gradle لتنزيل الأوزان Regular وMedium وSemiBold وBold وترخيص OFL قبل بناء موارد Android.
- أُضيف ملف `QUESTION_BANK.md` لتوثيق قائمة الأسئلة العشرين الموجودة حاليًا، مع تثبيت نص السؤال السادس.
- رُفع رقم إصدار التطبيق إلى 1.2.0 (versionCode 3).

## 2026-10-27 — إضافة بنك الأسئلة المخصص
- استبدال بنك الأسئلة الافتراضي بالأسئلة العشرين واستنتاجات نعم/لا التي زوّد بها صاحب التطبيق، دون إعادة صياغة النصوص.
- تحديث صفوف بنك الأسئلة الافتراضي القديمة تلقائيًا فقط عندما لا تزال مطابقة للقيم الافتراضية السابقة، مع إبقاء الصفوف التي عدّلها المستخدم كما هي.
- إضافة اختبار وحدات يتحقق من نصوص الأسئلة والاستنتاجات العشرين وربط إجابات نعم/لا بالاستنتاج الصحيح.
