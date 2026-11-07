plugins {
    id("com.android.application")
}

android {
    namespace = "gr.teo.kidscard"
    compileSdk = 34

    defaultConfig {
        applicationId = "gr.teo.kidscard"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Σταθερό κλειδί, ώστε οι νέες εκδόσεις να περνάνε πάνω από τις παλιές
    signingConfigs {
        create("fixed") {
            storeFile = file("kids.keystore")
            storePassword = "kidscard123"
            keyAlias = "kids"
            keyPassword = "kidscard123"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    androidResources {
        noCompress += listOf("ogg")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
