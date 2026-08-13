import org.apache.commons.lang3.SystemUtils
import java.util.zip.ZipFile

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.5"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val modid: String by project

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.gnuclient.json")
    }
    mixin {
        defaultRefmapName.set("mixins.gnuclient.refmap.json")
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
    maven("https://repo.polyfrost.cc/releases/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    shadowImpl(files("libs/viaforgeplus-2.2.jar"))
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
        exclude(module = "gson")
        exclude(module = "guava")
        exclude(module = "jarjar")
        exclude(module = "commons-codec")
        exclude(module = "commons-io")
        exclude(module = "launchwrapper")
        exclude(module = "asm-commons")
        exclude(module = "slf4j-api")
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(modid)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.gnuclient.json,mixins.viaforgeplus.json"
        this["FMLCorePlugin"] = "net.aspw.viaforgeplus.mixin.MixinLoader"
        this["FMLAT"] = "viaforgeplus_at.cfg"
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.gnuclient.json")) {
        expand(inputs.properties)
    }

    doLast {
        val vfpJar = file("libs/viaforgeplus-2.2.jar")
        if (!vfpJar.exists()) return@doLast
        val outputDir = sourceSets.main.get().output.resourcesDir ?: return@doLast
        val mcmodFile = File(outputDir, "mcmod.info")
        if (!mcmodFile.exists()) return@doLast

        try {
            val vfpJson = ZipFile(vfpJar).use { zip ->
                zip.getInputStream(zip.getEntry("mcmod.info")).bufferedReader().readText().trim()
            }
            val gnuJson = mcmodFile.readText().trim()
            // Both are JSON arrays: [{...}]  -> strip brackets and join
            val merged = if (gnuJson.startsWith("[") && gnuJson.endsWith("]") &&
                vfpJson.startsWith("[") && vfpJson.endsWith("]")) {
                "[" + gnuJson.substring(1, gnuJson.length - 1) + "," +
                    vfpJson.substring(1, vfpJson.length - 1) + "]"
            } else {
                gnuJson
            }
            mcmodFile.writeText(merged)
        } catch (e: Exception) {
            logger.warn("Failed to merge mcmod.info from ViaForgePlus: ${e.message}")
        }
    }
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    from(sourceSets.main.get().output)
    exclude(
        "dummyThing",
        "LICENSE.txt",
        "META-INF/MUMFREY.RSA",
        "META-INF/maven/**",
        "org/**/*.html",
        "LICENSE.md",
        "pack.mcmeta",
        "**/module-info.class",
        "*.so",
        "*.dylib",
        "*.dll",
        "*.jnilib",
        "ibxm/**",
        "com/jcraft/**",
        "org/lwjgl/**",
        "net/java/**",
        "META-INF/proguard/**",
        "META-INF/versions/**",
        "META-INF/com.android.tools/**",
        "fabric.mod.json"
    )
}

tasks.assemble.get().dependsOn(tasks.remapJar)
