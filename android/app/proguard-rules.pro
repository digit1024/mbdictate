# Annotation types referenced by Tink (via androidx.security-crypto) are
# compile-time only and are not present on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Tink ships consumer keep rules, but keep the entry points used through
# reflection by EncryptedSharedPreferences explicit.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**