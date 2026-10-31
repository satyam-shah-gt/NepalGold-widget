plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.goldrate.widget"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.goldrate.widget"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            // R8 + resource shrinking keeps the shipped APK as small as possible.
            // Keep rules live in app/src/main/keepRules/*.keep, which AGP 9 combines automatically.
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Scheduling layer.
    implementation(libs.androidx.work.runtime)

    // Storage layer.
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    // Real org.json implementation so the parser can be unit tested on the JVM.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
