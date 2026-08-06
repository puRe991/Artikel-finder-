# kotlinx.serialization erzeugt Serializer als statische Felder der annotierten Klassen.
# R8 wuerde sie sonst als ungenutzt entfernen.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class de.artikelfinder.app.**$$serializer { *; }
-keepclassmembers class de.artikelfinder.app.** {
    *** Companion;
}
-keepclasseswithmembers class de.artikelfinder.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit haelt Rueckgabetypen und Annotationen der Service-Schnittstellen zur Laufzeit vor.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
