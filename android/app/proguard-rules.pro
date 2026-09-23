# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep data classes used by Room
-keepclassmembers class com.pesatrack.data.local.database.entities.** { *; }

# Apache POI (Excel parsing)
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.logging.**
-dontwarn org.slf4j.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.**

# Handle missing Java Desktop classes (AWT)
# POI references java.awt.geom.Rectangle2D in XSLF/SVG code (unused on Android)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.graphbuilder.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.poi.xslf.**

# Apache PDFBox (M-PESA Statement PDF parsing)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**

# ============================================================================
# Moshi + Retrofit (AI Pro backend DTOs)
# ============================================================================
# Every DTO in services/ai/PesaTrackAiClient.kt is deserialized via Moshi
# codegen (@JsonClass(generateAdapter = true)). Without these rules R8 strips
# either the generated *JsonAdapter classes or the Kotlin metadata that
# KotlinJsonAdapterFactory reflects on when falling back — the symptom is
# JsonDataException at response.body() time on release builds, which
# runCatching turns into VerifyFailed and the user sees "we could not
# verify the purchase". Debug builds don't minify so this only bit us on
# the first end-to-end Play Billing test against a live signed AAB.
#
# Belt-and-braces: keep every AI/Pro service class + every generated adapter
# + Kotlin metadata + Moshi & Retrofit annotations.

# Every wire DTO — codegen adapters live in the same package.
-keep class com.pesatrack.services.ai.** { *; }
-keep class com.pesatrack.services.pro.ProState { *; }
-keep class com.pesatrack.services.pro.ProProduct { *; }
-keep class com.pesatrack.services.pro.ProState$Companion { *; }

# Moshi generated adapter classes. Pattern: <SourceClass>JsonAdapter.
-keep class **JsonAdapter { <init>(...); *; }
-keep class **_JsonAdapter { <init>(...); *; }

# @JsonClass-annotated classes.
-keep @com.squareup.moshi.JsonClass class * { *; }

# Kotlin metadata drives KotlinJsonAdapterFactory's reflective fallback and
# every codegen adapter's constructor lookup.
-keepnames class kotlin.Metadata
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Moshi field/method annotations.
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <methods>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Moshi runtime + Retrofit / OkHttp reflection helpers.
-dontwarn com.squareup.moshi.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepattributes Exceptions
