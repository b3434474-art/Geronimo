plugins {
    id("com.android.application")
}

android {
    namespace = "com.geronimo.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geronimo.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
}
