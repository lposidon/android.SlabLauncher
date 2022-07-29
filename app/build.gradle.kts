plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "io.posidon.android.slablauncher"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "22.0"

        val plainName = "SlabLauncher"
        setProperty("archivesBaseName", "$plainName-v$versionName-c$versionCode")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    namespace = "io.posidon.android.slablauncher"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    implementation("com.willowtreeapps:fuzzywuzzy-kotlin-jvm:0.9.0")

    implementation("io.posidon:android.launcherutil:22.0")
    implementation("io.posidon:android.libduckduckgo:22.0")
    implementation("io.posidon:android.conveniencelib:22.0")
    implementation("com.github.zaguragit:ksugar:fb31214198")

    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.5.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    testImplementation("junit:junit:4.13.2")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
