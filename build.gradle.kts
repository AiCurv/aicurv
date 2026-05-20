import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:c4ccc5d351")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

configure<CloudstreamExtension> {
    namespace = "com.hdpornfull"
    compileSdk = 35

    sourceSets["main"].setMirrored(true)
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")

    configure<BaseExtension> {
        namespace = project.namespace
        compileSdkVersion(35)

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}