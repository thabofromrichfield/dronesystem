# GHOSTGRID UAV — ProGuard rules
# Base set required by DJI Mobile SDK V4. Do not remove the -keep rules for dji.*/com.dji.*

-keepattributes Exceptions,InnerClasses,*Annotation*,Signature,EnclosingMethod

-dontoptimize
-dontpreverify
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn dji.**
-dontwarn com.dji.**
-dontwarn sun.**
-dontwarn java.**
-dontwarn com.amap.api.**
-dontwarn com.here.**
-dontwarn com.mapbox.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**

-keepclassmembers enum * {
    public static <methods>;
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep class * extends android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keep,allowshrinking class * extends dji.publics.DJIUI.** {
    public <methods>;
}

-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.* { *; }
-keep class dji.** { *; }
-keep class com.dji.** { *; }
-keep class com.google.** { *; }
-keep class org.bouncycastle.** { *; }
-keep,allowshrinking class org.** { *; }
-keep class com.squareup.wire.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.cySdkyc.** { *; }
-keep class org.greenrobot.eventbus.** { *; }

# GHOSTGRID's own classes are referenced from XML layouts.
-keep class drone.test.ui.widget.** { *; }
