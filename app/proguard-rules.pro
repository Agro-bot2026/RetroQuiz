# Reglas de ProGuard/R8 para RetroQuiz
# App simple (WebView + AppCompat), no se necesitan reglas especiales.

-keepattributes JavascriptInterface
-keep public class * extends android.webkit.WebViewClient
-keep public class * extends android.webkit.WebChromeClient
