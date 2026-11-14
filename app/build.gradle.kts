plugins { id("com.android.application") }

android {
    namespace = "mg.rmlala.cpv"
    compileSdk = 35
    defaultConfig {
        applicationId = "mg.rmlala.cpv"
        minSdk = 23
        targetSdk = 34
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toIntOrNull() ?: 1
        versionName = "2.0"
    }
    flavorDimensions += "compatibility"
    productFlavors {
        create("api23") { dimension = "compatibility"; minSdk = 23 }
        create("api29") { dimension = "compatibility"; minSdk = 29 }
    }
    signingConfigs {
        create("releaseCI") {
            val keyFile = System.getenv("RELEASE_KEYSTORE_FILE")
            if (!keyFile.isNullOrBlank()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("releaseCI") }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
tasks.register("verifyReleaseCredentials") {
    doLast {
        check(listOf("RELEASE_KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }) { "Signing credentials unavailable" }
    }
}
tasks.matching { it.name in listOf("packageApi23Release", "packageApi29Release") }.configureEach { dependsOn("verifyReleaseCredentials") }
