plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.intentcoach.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.intentcoach.app"
        minSdk = 26        // Android 8.0 — needed for foreground service APIs used here
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources/manifest to build its fake Android.
            isIncludeAndroidResources = true
        }
    }
}

// Room writes each database version's schema here (commit the folder). Migrations can
// then be checked against the real generated schema instead of hand-copied SQL.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // ViewModels for Compose screens. Same 2.8.4 line as lifecycle-runtime-ktx above, so no
    // version skew. (Not lifecycle-runtime-compose: its collectAsStateWithLifecycle expects
    // Compose UI 1.7, and the BOM below is Compose 1.6.8.)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    // 2.7.x is the last line built against Kotlin 1.9, which this project uses.
    implementation("androidx.navigation:navigation-compose:2.7.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room (local database — no cloud, ever)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JVM unit tests only — no instrumented tests in this project.
    testImplementation("junit:junit:4.13.2")
    // Robolectric gives SharedPreferences-backed code a real Context on the JVM.
    // Anything that does not need a Context is tested as a plain JUnit test instead.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
