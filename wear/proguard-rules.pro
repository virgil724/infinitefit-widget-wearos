# Add project specific ProGuard rules here.

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-keep public class org.jsoup.** { public *; }

# ZXing
-keep class com.google.zxing.** { *; }

# Wear Tiles
-keep class androidx.wear.tiles.** { *; }

# Guava (used by Wear Tiles)
-dontwarn com.google.common.**
