# Room entities and DAOs are kept by the Room artifacts' consumer rules.
# Broadcast receivers are referenced from the manifest and kept automatically.

# Spotify App Remote: it can map with Jackson, which is not used here (Gson is), and ships an annotation it leaves out.
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.**
-keep class com.spotify.protocol.mappers.gson.** { *; }
