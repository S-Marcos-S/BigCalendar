# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 1. Atributos essenciais para depuração, reflexão e anotações
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# 2. Preservar modelos do aplicativo, repositórios e serialização
-keep class com.mss.thebigcalendar.data.model.** { *; }
-keep class com.mss.thebigcalendar.data.repository.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# 3. Preservar Protobuf e Proto DataStore
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class androidx.datastore.** { *; }
-dontwarn com.google.protobuf.**

# 4. Jetpack Compose (as regras necessarias ja estao inclusas no AAR do Compose)

# 5. Preservar Serviços, Receivers, Tile e Widgets do Aplicativo
-keep class com.mss.thebigcalendar.service.** { *; }
-keep class com.mss.thebigcalendar.widget.** { *; }
-keep class com.mss.thebigcalendar.ui.screens.AlarmActivity { *; }
-keep class com.mss.thebigcalendar.ui.screens.HighVisibilityNotificationActivity { *; }
-keep class androidx.work.** { *; }

# 6. Preservar iText 7 (Geração de PDF do Calendário)
-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }
-keepclassmembers class com.itextpdf.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.slf4j.**

# 7. Preservar Google APIs, OAuth e HTTP Client
-keep class com.google.api.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.client.**
-keep class com.google.api.client.** { *; }
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient
-keep class org.apache.http.** { *; }
-keep class android.net.http.AndroidHttpClient { *; }

# 8. Gráficos (MPAndroidChart)
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# 9. Suprimir avisos de bibliotecas opcionais/não utilizadas para permitir stripping pelo R8
-dontwarn com.ibm.icu.**
-dontwarn org.apache.commons.**
-dontwarn junit.**
-dontwarn androidx.test.**
-dontwarn androidx.xr.**
-dontwarn androidx.graphics.**
-dontwarn libimpress_api_jni.**
-dontwarn kotlinx.coroutines.**