plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.newlink.kboard.testinjector"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.newlink.kboard.testinjector"
        minSdk = 31
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
    }
}

System.getenv("KBOARD_REAL_INPUT_BUILD_ROOT")?.let {
    layout.buildDirectory.set(file("$it/injector"))
}
