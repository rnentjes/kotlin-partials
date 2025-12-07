@file:OptIn(ExperimentalDistributionDsl::class)

import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl

plugins {
    kotlin("multiplatform") version "2.2.20"
}

group = "nl.astraeus"
version = "0.1.0-SNAPSHOT"

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
        binaries.executable()
        browser {
            distribution {
                outputDirectory.set(File("$projectDir/web/"))
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("nl.astraeus:kotlin-simple-logging:1.1.1")
                api("nl.astraeus:kotlin-css-generator:1.1.0")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting
        val jvmMain by getting {
            dependencies {
                implementation("io.undertow:undertow-core:2.3.19.Final")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")

                implementation("org.xerial:sqlite-jdbc:3.50.3.0")
                implementation("com.zaxxer:HikariCP:7.0.2")
                implementation("nl.astraeus:simple-jdbc-stats:1.6.1") {
                    exclude(group = "org.slf4j", module = "slf4j-api")
                }
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("nl.astraeus:kotlin-komponent:1.2.8")
            }
        }
        val jsTest by getting
    }
}
