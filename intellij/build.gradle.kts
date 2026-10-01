plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "org.gnu.ymir"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val local = providers.gradleProperty("platformLocalPath").orNull
        if (local != null) {
            local(local)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }

        bundledPlugin("org.jetbrains.plugins.textmate")
        plugin("com.redhat.devtools.lsp4ij", providers.gradleProperty("lsp4ijVersion").get())
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks {
    // The TextMate bundle is read from the disk, so it ships beside the jar, not in it
    prepareSandbox {
        from(layout.projectDirectory.dir("textmate")) {
            into(intellijPlatform.projectName.map { "$it/textmate" })
        }
    }
}
