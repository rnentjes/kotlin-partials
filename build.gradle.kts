@file:OptIn(ExperimentalDistributionDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl

plugins {
  kotlin("multiplatform") version "2.2.21"
  signing
  id("org.jetbrains.dokka") version "2.0.0"
  id("com.vanniktech.maven.publish") version "0.35.0"
}

group = "nl.astraeus"
version = "1.8.1"

repositories {
  mavenCentral()
  maven {
    url = uri("https://gitea.astraeus.nl/api/packages/rnentjes/maven")
  }
  maven {
    url = uri("https://gitea.astraeus.nl:8443/api/packages/rnentjes/maven")
  }
  maven {
    name = "Sonatype Releases"
    url = uri("https://central.sonatype.com/api/v1/publisher/deployments/download/")
  }
}

kotlin {
  jvm {}
  js {
    compilerOptions {
      target.set("es2015")
      sourceMap.set(true)
      sourceMapEmbedSources.set(JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS)
    }
    binaries.library()
    browser {
      distribution {
        outputDirectory.set(File("$projectDir/src/jvmMain/resources/partials"))
      }
    }
  }
  sourceSets {
    val commonMain by getting
    val commonTest by getting
    val jvmMain by getting {
      dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")

        api("io.undertow:undertow-core:2.3.22.Final")
        api("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
      }
    }
    val jvmTest by getting
    val jsMain by getting
    val jsTest by getting
  }
}

publishing {
  repositories {
    mavenLocal()
    maven {
      name = "gitea"
      setUrl("https://gitea.astraeus.nl/api/packages/rnentjes/maven")

      credentials {
        val giteaUsername: String? by project
        val giteaPassword: String? by project

        username = giteaUsername
        password = giteaPassword
      }
    }
  }
}

tasks.withType<AbstractPublishToMaven> {
  dependsOn(tasks.withType<Sign>())
}

signing {
  sign(publishing.publications)
}

tasks.named("jvmProcessResources") {
  // Ensure JS artifacts are generated before processing JVM resources that include them
  dependsOn(tasks.named("jsBrowserProductionLibraryDistribution"))
  dependsOn(tasks.named("jsBrowserDevelopmentLibraryDistribution"))
}

tasks.named("jsBrowserDevelopmentLibraryDistribution") {
  mustRunAfter(tasks.named("jsDevelopmentLibraryCompileSync"))
  mustRunAfter(tasks.named("jsProductionLibraryCompileSync"))
}

tasks.named("jsBrowserProductionLibraryDistribution") {
  mustRunAfter(tasks.named("jsDevelopmentLibraryCompileSync"))
  mustRunAfter(tasks.named("jsProductionLibraryCompileSync"))
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)

  signAllPublications()

  coordinates(group.toString(), name, version.toString())

  pom {
    name.set("kotlin-partials")
    description.set("Kotlin Partials")
    inceptionYear.set("2025")
    url.set("https://gitea.astraeus.nl/rnentjes/kotlin-partials")
    licenses {
      license {
        name.set("MIT")
        url.set("https://opensource.org/licenses/MIT")
      }
    }
    developers {
      developer {
        id.set("rnentjes")
        name.set("Rien Nentjes")
        email.set("info@nentjes.com")
      }
    }
    scm {
      url.set("https://gitea.astraeus.nl/rnentjes/kotlin-partials")
    }
  }
}
