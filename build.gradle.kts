@file:Suppress("UnstableApiUsage")

import org.gradle.internal.hash.ChecksumService
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

val config = configurations.detachedConfiguration(dependencies.create("gradle:bin:+@zip"))

config.resolutionStrategy.componentSelection.all {
    if ('-' in candidate.version) {
        reject("Pre-release: ${candidate.version}")
    }
}

repositories.ivy("https://services.gradle.org/distributions/") {
    content { onlyForConfigurations(config.name) }
    metadataSources { artifact() }
    patternLayout { artifact("[organization]-[revision]-[artifact].[ext]") }
}

tasks {
    val projectDir = layout.projectDirectory
    val buildDir = layout.buildDirectory
    val outputDir = objects.directoryProperty().value(buildDir.dir("gradle-distribution"))
    outputDir.finalizeValue()

    val compileGradleDistribution by registering {
        group = "gradle distribution"
        val zip = config.incoming.artifacts.resolvedArtifacts.map { it.single().file }
        val init = projectDir.file("src/main/kotlin/fleshgrinder.init.gradle.kts")
        val repositories = projectDir.file("src/main/kotlin/repositories.init.gradle.kts")
        val properties = projectDir.file("src/main/resources/gradle.properties")
        val srcDir = outputDir.dir("src")
        inputs.files(zip, init, repositories, properties)
        outputs.dir(srcDir)
        doLast {
            delete(srcDir)
            copy {
                from(zipTree(zip))
                into(srcDir)
            }
            copy {
                from(init) { into("init.d") }
                from(repositories) { into("init.d") }
                from(properties)
                into(srcDir.get().asFile.listFiles()!!.single())
            }
        }
    }

    val assembleGradleDistribution by registering(Zip::class) {
        group = "gradle distribution"
        finalizedBy("checksumGradleDistribution")
        from(compileGradleDistribution.map { it.outputs.files.single() })
        val version = config.incoming.artifacts.resolvedArtifacts.map {
            (it.single().id.componentIdentifier as ModuleComponentIdentifier).version
        }
        archiveBaseName.set("fleshgrinder")
        archiveAppendix.set("gradle")
        archiveVersion.set(version)
        archiveClassifier.set("bin")
        destinationDirectory.set(outputDir)
    }

    register("checksumGradleDistribution") {
        group = "gradle distribution"
        dependsOn(assembleGradleDistribution)
        val archiveFile = assembleGradleDistribution.flatMap { it.archiveFile }
        val checksumFile = archiveFile.map { archive ->
            archive.asFile.let { it.resolveSibling("${it.name}.sha256") }
        }
        inputs.file(archiveFile)
        outputs.file(checksumFile)
        doLast {
            checksumFile.get().writeText(
                serviceOf<ChecksumService>().sha256(archiveFile.get().asFile).toString()
            )
        }
    }

    assemble {
        dependsOn(assembleGradleDistribution)
    }
}
