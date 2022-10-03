@file:Suppress("UnstableApiUsage")

import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_DEFAULT_URL
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.kotlin.dsl.support.serviceOf

val gradleProperties: GradleProperties = serviceOf()

fun RepositoryHandler.configure() {
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
}

beforeProject {
    buildscript.configure()
    dependencyLocking.lockAllConfigurations()
}
