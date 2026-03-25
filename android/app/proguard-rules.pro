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
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.logging.**
-dontwarn org.slf4j.**

# Handle missing Java Desktop classes (AWT)
# POI references java.awt.geom.Rectangle2D in XSLF/SVG code (unused on Android)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.graphbuilder.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.poi.xslf.**
