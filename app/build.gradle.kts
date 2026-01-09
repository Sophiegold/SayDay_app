plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.sophiegold.app_sayday"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sophiegold.app_sayday"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("com.google.android.material:material:1.10.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.prolificinteractive:material-calendarview:1.4.3")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.github.Baseflow:PhotoView:2.3.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
// Animation support
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
// If you want to use Lottie animations for more advanced splash screens
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.glide)
    kapt(libs.glide.compiler)
}