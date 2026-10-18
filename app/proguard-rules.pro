# Reglas para la versión release (R8). MapLibre y Compose traen las suyas; esto solo evita avisos.
-dontwarn org.maplibre.**
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn com.google.android.gms.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
