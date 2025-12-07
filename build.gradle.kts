@file:OptIn(ExperimentalDistributionDsl::class)

import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl

plugins {
  kotlin("multiplatform") version "2.2.21"
}

group = "nl.astraeus"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://gitea.astraeus.nl/api/packages/rnentjes/maven")
    }
    maven {
        url = uri("https://gitea.astraeus.nl:8443/api/packages/rnentjes/maven")
    }
}

kotlin {
    jvmToolchain(17)
    jvm()
    js {
      binaries.library()
        browser {
            distribution {
                outputDirectory.set(File("$projectDir/web/"))
            }
        }
    }
    sourceSets {
      val commonMain by getting
        val commonTest by getting
        val jvmMain by getting {
            dependencies {
              implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")

                implementation("io.undertow:undertow-core:2.3.19.Final")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
            }
        }
      val jvmTest by getting
      val jsMain by getting
        val jsTest by getting
    }
}
