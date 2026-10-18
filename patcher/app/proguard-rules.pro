# The two libraries a patch is read with are small and reached by name nowhere;
# what they must not lose is their own tables.
-keep class org.tukaani.xz.** { *; }
-keep class com.jcraft.jzlib.** { *; }
-dontwarn org.tukaani.xz.**
