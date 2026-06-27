plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

group = "vang.truong"
version = "1.0.0"

android {
    namespace = "vang.truong.jankdetector"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    // Expose release sources so the published AAR is usable
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

// Maven publishing — runs AFTER the android block resolves the release component
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId    = "vang.truong"
                artifactId = "jankdetector"
                version    = "1.0.0"

                pom {
                    name        = "JankDetector"
                    description = "In-app Compose jank / dropped frame detector with floating HUD"
                    url         = "https://github.com/your-org/JankDetector"
                }
            }
        }

        // publishToMavenLocal uses ~/.m2/repository by default.
        // To publish to a custom local directory instead, uncomment:
        // repositories {
        //     maven { url = uri("${rootProject.buildDir}/local-repo") }
        // }
    }
}

