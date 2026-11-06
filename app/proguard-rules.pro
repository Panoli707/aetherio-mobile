# Commons Compress registers ZIP extra fields through Class.newInstance(). These constructors
# are needed only when yt-dlp's Python runtime is first extracted, so warm-runtime tests miss it.
# Keep the concrete implementations/constructors, not the whole library or its original names.
-keep,allowobfuscation class org.apache.commons.compress.archivers.zip.** implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
