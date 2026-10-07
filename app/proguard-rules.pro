# R8 rules for the release build. Debug is never minified, so nothing here is exercised
# until assembleRelease — verify any change against a release APK, not a debug run.

# --- Persisted JSON: field names ARE the format -------------------------------------------
# BackupRepositoryImpl writes the Drive backup with plain Gson reflection, and none of the
# Backup* DTOs carry @SerializedName. Without these rules R8 renames every field to a/b/c, and
# the short names shift whenever the classes change — so a backup written by one release stops
# restoring on the next, and backups from debug builds never restore at all.
-keep class com.vamshi.field.domain.model.backup.** { <fields>; <init>(...); }

# Custom stopwatch presets are stored in SharedPreferences as Gson JSON (same reasoning).
-keep class com.vamshi.field.data.storage.CustomPresetsStore$StoredPreset { <fields>; <init>(...); }

# Gson needs generic signatures to resolve TypeToken<List<StoredPreset>>.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Google API client (Drive) -------------------------------------------------------------
# google-http-client maps JSON onto the Drive model classes through @Key field reflection.
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
# ...but -keepclassmembers only protects classes R8 already thinks are instantiated. Response
# types such as FileList are only ever created reflectively by the JSON parser, so R8 treated
# FileList as never-instantiated, deleted its `files` field, and every files().list() call in
# release returned an empty list: backups uploaded fine but restore said "No backups were
# found". Keep every GenericJson model outright, including its no-arg constructor.
-keep class * extends com.google.api.client.json.GenericJson {
    <init>();
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# --- Logging --------------------------------------------------------------------------------
# Strip debug/verbose logcat output from release. Warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Keep line numbers so crash reports from the field map back to source via mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
