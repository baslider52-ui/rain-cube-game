# Keep Firestore data models and annotations for reflection-based deserialization
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.model.** { *; }
