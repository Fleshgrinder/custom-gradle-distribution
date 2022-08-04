@file:Suppress("UnstableApiUsage")

import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_DEFAULT_URL
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.IOException

fun RepositoryHandler.configure() {
    val gradleProperties: GradleProperties = serviceOf()

    clear()

    maven(MAVEN_CENTRAL_URL) {
        name = "Maven Central Releases"
        mavenContent(MavenRepositoryContentDescriptor::releasesOnly)
        metadataSources.apply {
            gradleMetadata()
            mavenPom()
        }
    }
    if (gradleProperties.find("repositories.maven.central.snapshots.enabled") == "true") {
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "Maven Central Snapshots"
            mavenContent(MavenRepositoryContentDescriptor::snapshotsOnly)
            metadataSources.apply {
                gradleMetadata()
                mavenPom()
            }
        }
    }

    maven(PLUGIN_PORTAL_DEFAULT_URL) {
        name = "Gradle Plugin Portal"
        mavenContent(MavenRepositoryContentDescriptor::releasesOnly)
        metadataSources.apply {
            gradleMetadata()
            mavenPom()
        }
    }

    if (gradleProperties.find("repositories.maven.confluent.enabled") == "true") {
        exclusiveContent {
            filter {
                includeGroupByRegex(/* language=regexp */ """\Aio\.confluent(?:\..+)?\z""")
            }
            forRepositories(maven("https://packages.confluent.io/maven/") {
                name = "Confluent Releases"
                mavenContent {
                    releasesOnly()
                    includeGroupByRegex(/* language=regexp */ """\Acom\.linkedin\.camus(?:\..+)?\z""")
                    includeGroupByRegex(/* language=regexp */ """\Aorg\.apache\.kafka(?:\..+)?\z""")
                }
                metadataSources.apply {
                    gradleMetadata()
                    mavenPom()
                }
            })
        }
    }

    if (gradleProperties.find("repositories.maven.local.enabled") == "true") {
        mavenLocal()
    }
}

fun ScriptHandler.configure() {
    dependencyLocking.lockAllConfigurations()
    repositories.configure()
}

beforeSettings {
    buildscript.configure()
    pluginManagement.repositories.configure()
    dependencyResolutionManagement.repositories.configure()

    try {
        val javaVersion = settingsDir.resolve(".java-version").readText().trim().removePrefix("1.")
        dependencyResolutionManagement.versionCatalogs.maybeCreate("libs").version("java", javaVersion)
    } catch (e: IOException) {
        logger.warn("Could not find/read '.java-version' file.", e)
    }
}

beforeProject {
    buildscript.configure()
    dependencyLocking.lockAllConfigurations()

    tasks.apply {
        withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }

        withType<Test>().configureEach {
            systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
            testLogging.apply {
                events(TestLogEvent.SKIPPED, TestLogEvent.PASSED)
                showCauses = true
                showExceptions = true
                showStackTraces = true
                showStandardStreams = true
            }
        }
    }
}
