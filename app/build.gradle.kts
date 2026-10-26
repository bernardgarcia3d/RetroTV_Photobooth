plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val paymongoStagingBackendUrl = providers.gradleProperty("paymongoStagingBackendUrl")
    .orElse("")
    .get()

android {
    namespace = "com.eventphotobooth.app"
    compileSdk = 37

    flavorDimensions += "edition"

    defaultConfig {
        applicationId = "com.eventphotobooth.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://pzbovbahkoneiqctnsfe.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_cg3IVPx4L4dlal5bFO44Fg_ZKlrrjAv\"")
    }

    productFlavors {
        create("standard") {
            dimension = "edition"
            buildConfigField("String", "PAYMENT_EDITION", "\"STANDARD\"")
            buildConfigField("String", "PAYMONGO_ENVIRONMENT", "\"DISABLED\"")
            buildConfigField("boolean", "PAYMONGO_STAGING_ENABLED", "false")
            buildConfigField("String", "PAYMONGO_BACKEND_URL", "\"\"")
        }

        create("paymongo") {
            dimension = "edition"
            applicationIdSuffix = ".paymongo"
            versionNameSuffix = "-paymongo-staging"
            buildConfigField("String", "PAYMENT_EDITION", "\"PAYMONGO\"")
            buildConfigField("String", "PAYMONGO_ENVIRONMENT", "\"STAGING\"")
            buildConfigField("boolean", "PAYMONGO_STAGING_ENABLED", "true")
            buildConfigField(
                "String",
                "PAYMONGO_BACKEND_URL",
                "\"$paymongoStagingBackendUrl\"",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("androidx.camera:camera-video:1.6.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
