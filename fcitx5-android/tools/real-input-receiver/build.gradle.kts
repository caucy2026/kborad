plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.newlink.kboard.testreceiver"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.newlink.kboard.testreceiver"
        minSdk = 31
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { debug { isDebuggable = true } }
}

System.getenv("KBOARD_REAL_INPUT_BUILD_ROOT")?.let {
    layout.buildDirectory.set(file("$it/receiver"))
}
