plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aram.nasbology"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aram.nasbology"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


// Download the official IBM Plex Sans Arabic TTF weights before Android resources are processed.
// This keeps the project archive small while ensuring the font is bundled into the APK on every build.
val ibmPlexArabicFontResources = mapOf(
    "ibm_plex_sans_arabic_regular.ttf" to "IBMPlexSansArabic-Regular.ttf",
    "ibm_plex_sans_arabic_medium.ttf" to "IBMPlexSansArabic-Medium.ttf",
    "ibm_plex_sans_arabic_semibold.ttf" to "IBMPlexSansArabic-SemiBold.ttf",
    "ibm_plex_sans_arabic_bold.ttf" to "IBMPlexSansArabic-Bold.ttf"
)

val downloadIbmPlexArabicFonts by tasks.registering {
    val fontDir = layout.projectDirectory.dir("src/main/res/font").asFile
    val licenseDir = layout.projectDirectory.dir("src/main/assets/licenses").asFile
    val fontFiles = ibmPlexArabicFontResources.keys.map { fontDir.resolve(it) }
    val licenseFile = licenseDir.resolve("IBM-Plex-Sans-Arabic-OFL.txt")
    outputs.files(fontFiles + licenseFile)
    outputs.upToDateWhen {
        fontFiles.all { it.isFile && it.length() > 10_000 } &&
            licenseFile.isFile && licenseFile.length() > 1_000
    }

    doLast {
        fontDir.mkdirs()
        licenseDir.mkdirs()
        val fontBaseUrl = "https://raw.githubusercontent.com/googlefonts/plex/master/Google-Fonts-Fixes/fonts/IBM-Plex-Sans-Arabic/fonts/complete/ttf/"
        ibmPlexArabicFontResources.forEach { (resourceName, upstreamName) ->
            val target = fontDir.resolve(resourceName)
            if (!target.isFile || target.length() < 10_000) {
                val temp = fontDir.resolve("$resourceName.download")
                try {
                    java.net.URL(fontBaseUrl + upstreamName).openConnection().apply {
                        connectTimeout = 20_000
                        readTimeout = 30_000
                    }.getInputStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (temp.length() < 20_000) throw GradleException("Downloaded IBM Plex Sans Arabic font looks incomplete: $upstreamName")
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) throw GradleException("Could not install font resource: $resourceName")
                } catch (error: Exception) {
                    temp.delete()
                    throw GradleException("Could not download IBM Plex Sans Arabic. Check the build machine's internet connection.", error)
                }
            }
        }

        if (!licenseFile.isFile || licenseFile.length() < 1_000) {
            val licenseUrl = "https://raw.githubusercontent.com/googlefonts/plex/master/packages/plex-sans-arabic/LICENSE.txt"
            val tempLicense = licenseDir.resolve("IBM-Plex-Sans-Arabic-OFL.txt.download")
            try {
                java.net.URL(licenseUrl).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                }.getInputStream().use { input ->
                    tempLicense.outputStream().use { output -> input.copyTo(output) }
                }
                if (tempLicense.length() < 1_000) throw GradleException("Downloaded OFL license looks incomplete")
                if (licenseFile.exists()) licenseFile.delete()
                if (!tempLicense.renameTo(licenseFile)) throw GradleException("Could not save IBM Plex Sans Arabic license")
            } catch (error: Exception) {
                tempLicense.delete()
                throw GradleException("Could not download the IBM Plex Sans Arabic license file.", error)
            }
        }
        logger.lifecycle("IBM Plex Sans Arabic fonts and OFL license are ready.")
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadIbmPlexArabicFonts)
}
