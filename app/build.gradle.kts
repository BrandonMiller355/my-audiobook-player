import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.brandonmiller.audiobookplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.brandonmiller.audiobookplayer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // The build runs on Studio's bundled JDK 25, but bytecode targets 17 —
    // Android's toolchain does not accept newer class files.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The bundled sample audiobook is AAC inside an MP4 container — already compressed. Deflating
    // it at build time costs time and saves nothing, and storing it uncompressed keeps the copy
    // out of the APK a straight read (`bundle-sample-audiobook` task 1.2).
    androidResources {
        noCompress += "m4b"
    }

    // Robolectric needs a real merged manifest to construct a working Context/Application.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Export the Room schema so future migrations have a committed baseline instead of
// falling back to destructive recreation.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * The app is entirely local and offline (PRD §24) and gets file access solely through Storage
 * Access Framework grants (PRD §16). Neither promise is enforced by the source manifest — a
 * dependency can contribute a permission during manifest merging, which is how Media3 brought in
 * ACCESS_NETWORK_STATE. This fails the build if any of these reach the merged manifest, so an
 * upgrade cannot reintroduce one silently.
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_AUDIO",
)

androidComponents {
    onVariants { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        val verify = tasks.register("verify${variantName}Permissions") {
            group = "verification"
            description = "Fails if a forbidden permission reached the merged $variantName manifest."
            inputs.file(mergedManifest)
            val forbidden = forbiddenPermissions
            doLast {
                val manifestText = mergedManifest.get().asFile.readText()
                val found = forbidden.filter { manifestText.contains("\"$it\"") }
                if (found.isNotEmpty()) {
                    throw GradleException(
                        buildString {
                            appendLine("Forbidden permission(s) in the merged $variantName manifest:")
                            found.forEach { appendLine("  - $it") }
                            appendLine()
                            appendLine("A dependency contributed these during manifest merging.")
                            appendLine("Either remove it with tools:node=\"remove\" in AndroidManifest.xml,")
                            appendLine("or justify keeping it and update the app-shell spec — do not")
                            appendLine("weaken this list without deciding deliberately.")
                        },
                    )
                }
            }
        }

        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(verify) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // Local image loading only — the library list decodes a cover per row while scrolling and the
    // Player decodes a large one, and Coil provides the asynchronous decoding, size-aware
    // downsampling, and per-image cache that would otherwise be hand-rolled against BitmapFactory.
    // Its core artifact pulls in no network dependency; verify<Variant>Permissions above proves the
    // app still declares no INTERNET permission.
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
