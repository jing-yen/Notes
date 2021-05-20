// Top-level build file where you can add configuration options common to all sub-projects/modules.
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.google.gms:google-services:4.3.5")
            classpath("com.android.tools.build:gradle:7.0.0-alpha15")
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
            classpath("org.jetbrains.kotlin:kotlin-serialization:1.5.0")
            classpath("com.squareup.sqldelight:gradle-plugin:1.5.0")
        }
    }

    tasks.register("clean", Delete::class) {
        delete(rootProject.buildDir)
    }