import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.serverpod.idea"
version = "0.2.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))

        // Declared as required in plugin.xml, so the sandbox and the verifier both
        // need them present. Versions come from the Marketplace API against the
        // platform above, so a platform bump does not need matching pins here.
        compatiblePlugins("Dart", "io.flutter")
    }

    // The BOM keeps the Jupiter API and the platform launcher on one version.
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            // since-build defaults to the major build of the platform above, so
            // restating it here would only be a second copy to forget to update.
            // Left open at the top so the plugin survives future builds.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
