plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The binaries the app ships.
 *
 * The engine, the server and the helper tools are built by the scripts under
 * `../tools/` and dropped into `../prebuilt/`. They go into the assets,
 * because an APK entry cannot be executed in place. The app extracts them
 * into its own storage on the first launch.
 *
 * A build without them still works: the app reports which binary is missing
 * and the screens that do not need the server still open.
 */
val prebuiltBinaries = rootProject.layout.projectDirectory.dir("prebuilt")
val bundledAssets = layout.buildDirectory.dir("generated/bundledAssets")

val bundleBinaries by tasks.registering(Copy::class) {
    from(prebuiltBinaries)
    into(bundledAssets.map { it.dir("binaries") })
    doFirst {
        val names = prebuiltBinaries.asFile.list()?.sorted()?.filter { it.isNotEmpty() } ?: emptyList()
        if (names.isEmpty()) {
            logger.lifecycle("No binaries were found in $prebuiltBinaries. The app will report them as missing.")
        } else {
            logger.lifecycle("The app ships these binaries: ${names.joinToString(", ")}")
        }
    }
}

android {
    namespace = "com.openchamber.android"
    compileSdk = 35

    sourceSets.getByName("main") {
        assets.srcDir(bundledAssets)
    }

    defaultConfig {
        applicationId = "com.openchamber.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(bundleBinaries)
}

dependencies {
    implementation(project(":core:api"))
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:runtime"))
    implementation(project(":core:sync"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
