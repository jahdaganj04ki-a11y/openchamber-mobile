# OpenChamber Android keeps its model classes reachable through kotlinx.serialization
# and the Compose runtime, so the default rules are enough. Nothing in the app
# obfuscates today; the file exists so a release build has a stable place to put
# rules that later need one.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
