# R8 rules for the release build.
#
# Most dependencies (Compose, Coil, OkHttp, Ktor) ship their own consumer rules, so this file only
# covers what R8 cannot infer on its own: reflection-driven serialization, and warnings from optional
# dependencies that are never on the Android classpath.

# ── kotlinx.serialization ────────────────────────────────────────────────────────────────────────
# Serializers are looked up reflectively through the generated $$serializer classes and the
# Companion.serializer() functions, so neither can be renamed or stripped.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our own @Serializable DTOs (network/**) and settings models.
-keep,includedescriptorclasses class dev.icedtea.kodex.**$$serializer { *; }
-keepclassmembers class dev.icedtea.kodex.** {
    *** Companion;
}
-keepclasseswithmembers class dev.icedtea.kodex.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Optional/absent dependencies ─────────────────────────────────────────────────────────────────
# Ktor and OkHttp reference logging and platform APIs that aren't packaged for Android; the calls are
# guarded at runtime, so the references are safe to leave unresolved.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.debug.**
