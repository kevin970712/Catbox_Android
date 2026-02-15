-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

-keepattributes !RuntimeVisibleAnnotations,!RuntimeInvisibleAnnotations,!Signature
-dontwarn kotlin.reflect.**

-keep class com.google.zxing.qrcode.QRCodeWriter { public <init>(); public com.google.zxing.common.BitMatrix encode(...); }
-keep class com.google.zxing.BarcodeFormat { com.google.zxing.BarcodeFormat QR_CODE; }
-keep class com.google.zxing.common.BitMatrix { public boolean get(int,int); public int getWidth(); public int getHeight(); }

-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.material3.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
-keepattributes !SourceFile,!LineNumberTable