# Keep shared kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep class com.agent.shared.** { *; }
