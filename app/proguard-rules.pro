# Add project specific ProGuard rules here.
# You can use the wildcard to match all content in a package.

# Suppress warnings for SLF4J (common in Ktor/Supabase dependencies)
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# Keep generic serialization classes if needed (safety net)
-keep class kotlinx.serialization.** { *; }
