import java.net.URI
import java.security.MessageDigest
import java.security.DigestInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.aitronics.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.aitronics.assistant"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
val wakeAssets = layout.buildDirectory.dir("generated/wakeAssets")
val prepareWakeModel by tasks.registering {
    val archive = wakeAssets.map { it.file("wake-en-015.zip") }
    outputs.file(archive)
    doLast {
        val file = archive.get().asFile
        file.parentFile.mkdirs()
        val temp = file.resolveSibling("wake-download.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            URI("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip").toURL().openStream().use { raw ->
                DigestInputStream(raw, digest).use { input -> temp.outputStream().use { input.copyTo(it) } }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498")
            temp.copyTo(file, overwrite = true)
        } finally { temp.delete() }
    }
}
android.sourceSets["main"].assets.srcDir(wakeAssets)
tasks.named("preBuild") { dependsOn(prepareWakeModel) }

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("androidx.work:work-runtime:2.10.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.material3:material3")
}
