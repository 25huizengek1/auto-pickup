plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
}

val clean by tasks.registering(Delete::class) {
    delete(rootProject.layout.buildDirectory.asFile)
}

allprojects {
    group = "me.huizengek.autopickup"
    version = "1.0.0"
}
