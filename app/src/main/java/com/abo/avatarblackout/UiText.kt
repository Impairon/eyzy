package com.abo.avatarblackout

object UiText {
    private val arabic = mapOf(
        "Lowery" to "لويري", "Sibha" to "السبحة", "Prayers" to "الصلاة",
        "Qibla" to "القبلة", "Settings" to "الإعدادات", "Language" to "اللغة",
        "App lock" to "قفل التطبيق",
        "Lock app with fingerprint" to "قفل التطبيق بالبصمة",
        "Protect Swizy when you open it. It locks again after 60 seconds in the background." to "احمِ سويزي عند فتحه. يُقفل مجددًا بعد ٦٠ ثانية من بقائه في الخلفية.",
        "Swizy is locked" to "سويزي مقفل", "Authenticate to continue." to "أكّد هويتك للمتابعة.",
        "Unlock with fingerprint" to "فتح بالبصمة", "Use device PIN or password" to "استخدام رمز أو كلمة مرور الجهاز",
        "Fingerprint is unavailable. Use your device screen lock." to "البصمة غير متاحة. استخدم قفل شاشة الجهاز.",
        "Fingerprint not recognized. Try again." to "لم يتم التعرف على البصمة. حاول مجددًا.",
        "Authentication required to open Swizy." to "يلزم تأكيد الهوية لفتح سويزي.", "Unlock Swizy" to "فتح سويزي",
        "Confirm it is you to continue" to "أكّد هويتك للمتابعة.",
        "Set a PIN, pattern, or password in Android Settings first." to "اضبط رمزًا أو نمطًا أو كلمة مرور من إعدادات أندرويد أولًا.",
        "Device screen-lock authentication is unavailable." to "تأكيد قفل شاشة الجهاز غير متاح.",
        "Set up device security first" to "أمّن جهازك أولًا",
        "Add a fingerprint or a PIN, pattern, or password in Android Settings before enabling the app lock." to "أضف بصمة أو رمزًا أو نمطًا أو كلمة مرور من إعدادات أندرويد قبل تفعيل قفل التطبيق.",
        "Open Settings" to "فتح الإعدادات",
        "Choose the language used by Swizy." to "اختر لغة واجهة سويزي.",
        "swizy's touch ✨" to "لمسة سويزي ✨",
        "swizy protects profile pictures with a narrow bar while keeping chats, buttons, and the keyboard usable." to "يحمي سويزي صور الحسابات بشريط صغير دون حجب المحادثات أو الأزرار أو لوحة المفاتيح.",
        "swizy is patrol" to "تفعيل سويزي", "Open Accessibility settings" to "فتح إعدادات إمكانية الوصول",
        "Hide an area manually" to "إخفاء منطقة يدويًا",
        "Choose any part of WhatsApp or another supported app and keep it completely black. The small side control can hide or restore it, choose a new area, or remove it." to "اختر أي جزء من واتساب أو تطبيق مدعوم لإخفائه. استخدم أداة التحكم الجانبية لإظهار المنطقة أو إخفائها أو تعديلها أو حذفها.",
        "Summon the paw" to "إظهار أداة سويزي", "Enable the swizy accessibility service first" to "فعّل خدمة إمكانية الوصول الخاصة بسويزي أولًا",
        "Use the ball menu, then choose Add" to "افتح قائمة الأداة ثم اختر إضافة",
        "SIBHA" to "السبحة", "Tap to count" to "اضغط للتسبيح", "Reset" to "تصفير",
        "Undo" to "تراجع", "Reset count?" to "تصفير العدّاد؟",
        "This will clear the current count." to "سيتم مسح العدد الحالي.",
        "Cycle progress: %1$d / 33" to "تقدّم الدورة: %1$d / ٣٣",
        "Qibla finder" to "اتجاه القبلة", "Point your phone to find the direction of the Kaaba in Mecca." to "وجّه هاتفك لمعرفة اتجاه الكعبة المشرفة.",
        "Set your location in the Prayers page first, or enable GPS there, then come back." to "حدّد موقعك من صفحة الصلاة أو فعّل GPS ثم عُد إلى هنا.",
        "Go to Prayers" to "الانتقال إلى الصلاة", "The saved location is invalid. Update it from the Prayers page." to "الموقع المحفوظ غير صالح. حدّثه من صفحة الصلاة.",
        "Finding compass direction…" to "جارٍ تحديد اتجاه البوصلة…",
        "Refreshing compass for a moment…" to "لحظة، جارٍ تحديث البوصلة…",
        "Good" to "جيد", "Medium" to "متوسط", "Low" to "ضعيف", "Unreliable" to "غير موثوق",
        "Your location: " to "موقعك: ",
        "Hold the phone at your comfortable upward angle (around 45° is fine). Keep the screen facing you and use the top edge as the forward direction. The compass compensates for tilt." to "أمسك الهاتف بالزاوية المريحة لك (حوالي ٤٥ درجة مناسبة). اجعل الشاشة باتجاهك واستخدم الحافة العلوية كاتجاه أمامي. البوصلة تعوّض ميل الهاتف.",
        "A live compass sensor is unavailable. The calculated Qibla bearing is shown, but the arrow cannot track your phone." to "مستشعر البوصلة المباشر غير متاح. سيظهر اتجاه القبلة المحسوب، لكن السهم لن يتتبع حركة الهاتف.",
        "About Qibla" to "عن القبلة",
        "The Qibla is the direction faced during prayer, toward the Kaaba in Mecca. The compass shows this direction relative to north as detected by your phone." to "القبلة هي الاتجاه نحو الكعبة المشرفة في مكة. تعرض البوصلة هذا الاتجاه نسبةً إلى الشمال كما يحدده هاتفك.",
        "Live compass unavailable — showing the calculated bearing only." to "البوصلة المباشرة غير متاحة — يظهر الاتجاه المحسوب فقط.",
        "Magnetic interference detected. Move away from your laptop, magnets or metal." to "تم رصد تداخل مغناطيسي. ابتعد عن الحاسوب والمغناطيس والمعادن.",
        "Compass is unreliable. Move away from laptops, magnets and metal, then hold still while it settles." to "قراءة البوصلة غير موثوقة. ابتعد عن الحاسوب والمغناطيس والمعادن وثبّت الهاتف حتى تستقر القراءة.",
        "Compass signal is weak. Move away from metal and electronics." to "إشارة البوصلة ضعيفة. ابتعد عن المعادن والأجهزة الإلكترونية.",
        "Facing Qibla" to "أنت باتجاه القبلة", "Hold steady — checking alignment…" to "اثبت قليلًا — جارٍ التأكد من الاتجاه…",
        "Qibla is behind you — turn around." to "القبلة خلفك — استدر للجهة الأخرى.",
        "Control how swizy masks profile pictures." to "تحكّم في طريقة إخفاء صور الحسابات.",
        "swizy mask mode" to "طريقة الإخفاء", "Simple line" to "خط بسيط",
        "Dynamic circle" to "دائرة متحركة", "Do nothing (manual only)" to "بدون إخفاء تلقائي",
        "Mask color" to "لون الإخفاء", "Black (privacy safe)" to "أسود (أفضل للخصوصية)",
        "Purple" to "بنفسجي", "White" to "أبيض",
        "Mask opacity (black boxes)" to "شفافية مناطق الإخفاء", "Paw opacity when idle" to "شفافية أداة التحكم",
        "Line thickness" to "سُمك الخط", "Dynamic circle size" to "حجم الدائرة المتحركة",
        "Show privacy indicator while masking" to "إظهار مؤشر الخصوصية أثناء الإخفاء",
        "Battery saver mode" to "وضع توفير البطارية", "Quick Settings action" to "إجراء الإعدادات السريعة",
        "Start app" to "فتح التطبيق", "Start app and summon the paw" to "فتح التطبيق وإظهار أداة التحكم",
        "Settings for the prayer counter." to "إعدادات عدّاد التسبيح.",
        "Peep every N counts (0 disables)" to "تنبيه كل عدد محدد (0 لإيقافه)",
        "Save Sibha reminder" to "حفظ تذكير التسبيح", "Sibha reminder saved" to "تم حفظ تذكير التسبيح",
        "Prayer time calculation and announcement settings." to "إعدادات حساب مواقيت الصلاة والإعلان عنها.",
        "Prayer calculation method" to "طريقة حساب مواقيت الصلاة",
        "Egyptian General Authority" to "الهيئة المصرية العامة للمساحة",
        "Muslim World League" to "رابطة العالم الإسلامي", "Umm Al-Qura" to "أم القرى",
        "Karachi" to "كراتشي", "ISNA" to "الجمعية الإسلامية لأمريكا الشمالية",
        "Use 24-hour prayer times" to "استخدام نظام ٢٤ ساعة",
        "Speak the time on long-press headset button" to "نطق الوقت عند الضغط المطوّل على زر السماعة",
        "How to change voice" to "طريقة تغيير الصوت", "Say the time now" to "انطق الوقت الآن",
        "Profiles & backup" to "الملفات الشخصية والنسخ الاحتياطي",
        "Manual boxes are saved separately for each app profile and screen orientation." to "يتم حفظ مناطق الإخفاء لكل تطبيق واتجاه شاشة بشكل منفصل.",
        "Remove" to "إزالة", "Remove profile?" to "إزالة الملف الشخصي؟", "Cancel" to "إلغاء",
        "Export layouts" to "تصدير الإعدادات", "Import layouts" to "استيراد الإعدادات", "Release" to "الإصدار",
        "Today's prayer times" to "مواقيت الصلاة اليوم", "No current schedule cached" to "لا توجد مواقيت محفوظة لليوم",
        "Set a location below and refresh. The last saved schedule remains available offline." to "حدّد موقعك أدناه ثم حدّث المواقيت. ستظل آخر مواقيت محفوظة متاحة دون إنترنت.",
        "Location and updates" to "الموقع والتحديثات",
        "Prayer times are fetched once per day and cached for offline use." to "يتم تحديث مواقيت الصلاة مرة يوميًا وحفظها للاستخدام دون إنترنت.",
        "Use device location" to "استخدام موقع الجهاز", "Update prayer times" to "تحديث مواقيت الصلاة",
        "Enter valid latitude and longitude" to "أدخل خط عرض وخط طول صحيحين",
        "Updating prayer times…" to "جارٍ تحديث مواقيت الصلاة…", "Prayer times updated" to "تم تحديث مواقيت الصلاة",
        "Could not update. Cached times remain available." to "تعذّر التحديث. لا تزال المواقيت المحفوظة متاحة.",
        "Enable location services or enter coordinates." to "فعّل خدمات الموقع أو أدخل الإحداثيات يدويًا.",
        "Finding your location…" to "جارٍ تحديد موقعك…",
        "Location unavailable. Enter coordinates instead." to "الموقع غير متاح. أدخل الإحداثيات يدويًا.",
        "Open Qibla compass" to "فتح بوصلة القبلة",
        "Fajr" to "الفجر", "Sunrise" to "الشروق", "Dhuhr" to "الظهر",
        "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء",
        "Latitude, e.g. 30.0444" to "خط العرض، مثال 30.0444",
        "Longitude, e.g. 31.2357" to "خط الطول، مثال 31.2357",
        "Create profiles from the paw menu: choose Add profile in the target app, draw your boxes, then choose Save profile. swizy will apply that profile automatically whenever the app is open." to "أنشئ الملفات الشخصية من قائمة أداة سويزي داخل التطبيق المستهدف: اختر إضافة ملف، وارسم مناطق الإخفاء، ثم احفظ الملف. سيُطبّق سويزي الإعداد تلقائيًا عند فتح التطبيق.",
        "swizy 1.7.0" to "سويزي 1.7.0",
        "Changelog: faster scanning, rotation fixes, Qibla compass, refined UI, per-orientation control position." to "التغييرات: فحص أسرع، إصلاحات للدوران، بوصلة القبلة، وتحسينات للواجهة ومواقع عناصر التحكم.",
        "Crash logs are stored locally and are never uploaded automatically." to "تُحفظ سجلات الأعطال على الجهاز ولا يتم رفعها تلقائيًا.",
        "Layouts exported" to "تم تصدير الإعدادات", "Layouts imported" to "تم استيراد الإعدادات",
        "Change the announcement voice" to "تغيير صوت الإعلان",
        "Open Android Settings" to "فتح إعدادات أندرويد",
        "Close" to "إغلاق",
        "Open Android Settings, then search for Text-to-speech output." to "افتح إعدادات أندرويد ثم ابحث عن إخراج تحويل النص إلى كلام.",
        "Turn on the swizy accessibility service first" to "فعّل خدمة إمكانية الوصول الخاصة بسويزي أولًا",
        "1. Go to Settings and search for Text to speech\n2. Choose a language you like\n3. For voices, go to the favorite engine and click the settings or gear tab on the side and choose Install voice data\n4. Choose the language and voice\n5. Reboot the phone & enjoy" to "١. افتح الإعدادات وابحث عن تحويل النص إلى كلام\n٢. اختر اللغة التي تفضلها\n٣. افتح إعدادات محرّك النطق واختر تثبيت بيانات الصوت\n٤. اختر اللغة والصوت المناسبين\n٥. أعد تشغيل الهاتف واستمتع.",
        "Backup failed" to "فشل النسخ الاحتياطي",
        "Updating prayer times…" to "جارٍ تحديث مواقيت الصلاة…",
    )

    fun translate(value: String, language: String): String {
        // Brand and feature names are localized in both directions.
        if (language == "ar") {
            when (value) {
                "Swizy", "swizy" -> return "سويزي"
                "Lowery" -> return "لويري"
            }
        } else {
            when (value) {
                "سويزي" -> return "Swizy"
                "لويري" -> return "Lowery"
            }
        }
        if (language != "ar") return value
        arabic[value]?.let { return it }
        return when {
            value.startsWith("Turn right ") -> "استدر يمينًا " + value.removePrefix("Turn right ")
            value.startsWith("Turn left ") -> "استدر يسارًا " + value.removePrefix("Turn left ")
            value.startsWith("Heading: ") -> value.replaceFirst("Heading:", "الاتجاه:")
                .replace("magnetic", "مغناطيسي").replace("Tilt:", "الميل:")
                .replace("Field:", "المجال:").replace("Good", "جيد")
                .replace("Medium", "متوسط").replace("Low", "ضعيف").replace("Unreliable", "غير موثوق")
            value.startsWith("Your location: ") -> value.replaceFirst("Your location:", "موقعك:")
            value.startsWith("Qibla from true north: ") -> value.replaceFirst("Qibla from true north:", "اتجاه القبلة من الشمال الحقيقي:")
            value.startsWith("Magnetic interference suspected") -> "يُشتبه بوجود تداخل مغناطيسي. ابتعد عن الحاسوب والمعادن."
            value.startsWith("Backup failed: ") -> "فشل النسخ الاحتياطي: " + value.removePrefix("Backup failed: ")
            else -> value
        }
    }
}
