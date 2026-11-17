plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsh.whale"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.whale"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("whale.p12")
            storePassword = "whale2026fish"
            keyAlias = "whale"
            keyPassword = "whale2026fish"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            // 用 debug 签名即可自用；要发布再换 release keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // 模型是 .moc3/.json/.png，不能被压缩，否则 WebView 读不出
    androidResources {
        noCompress += listOf("moc3")
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 用 WebViewAssetLoader 把 assets 映射到 https://appassets.androidplatform.net/
    // 否则 file:// 页面里的 XHR 会被 WebView 拦掉（模型加载失败：Network error）
    implementation("androidx.webkit:webkit:1.11.0")
}
