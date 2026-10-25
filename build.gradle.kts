// AGP 9 compiles Kotlin itself; the Kotlin plugin is declared only to pin its version to the
// Compose compiler's, which must match.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
