# Upstream JNI names and native-instantiated payload constructors must remain stable.
-keepclasseswithmembernames,includedescriptorclasses class com.nuvio.engine.** {
    native <methods>;
}
-keep class com.nuvio.engine.internal.NativeEventPayload { *; }
-keep class com.nuvio.engine.internal.NativeFilePayload { *; }
# nativeGetFiles constructs this result container through FindClass/GetMethodID too.
-keep class com.nuvio.engine.internal.NativeFilesPayload { *; }
