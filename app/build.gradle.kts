import java.util.Properties

plugins {
    id("com.android.application")
}

// AMap (Gaode) Web Service key for the nearby-vets search. Kept out of git: put AMAP_KEY=... in
// local.properties (or pass -PAMAP_KEY=...). Without one, the app says vet search isn't set up.
val amapKey: String = (project.findProperty("AMAP_KEY") as String?) ?: Properties().let { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    props.getProperty("AMAP_KEY", "")
}

android {
    namespace = "com.litterboxd.app"
    compileSdk = 33
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.litterboxd.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Loads and caches Reading Corner thumbnails and full-size diary photos.
    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests: the copy inside android.jar is a stub that throws "not mocked".
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
