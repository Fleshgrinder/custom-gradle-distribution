@file:Suppress("UnstableApiUsage")

import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf

// https://github.com/gradle/gradle/issues/12388
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> T?.sneakyNull(): T = this as T

val objects: ObjectFactory = serviceOf()
val providers: ProviderFactory = serviceOf()
val javaVersion = (gradle as GradleInternal)
    .root
    .owner
    .buildRootDir
    .let(objects.fileProperty()::fileValue)
    .let(providers::fileContents)
    .asText
    .map { it.trim().removePrefix("1.").substringBefore('.').ifBlank { null }.sneakyNull() }

beforeSettings {
    dependencyResolutionManagement.versionCatalogs {
        (if ("libs" in names) named("libs") else register("libs")).configure {
            javaVersion.orNull?.let { version("java", it) }
        }
    }
}

beforeProject {
    val extensions = extensions

    // TODO all of these things should go into dedicated plugins.
    pluginManager.apply {
        withPlugin("java") {
            extensions.configure<JavaPluginExtension> {
                consistentResolution {
                    useCompileClasspathVersions()
                }

                toolchain.languageVersion.set(javaVersion.map(JavaLanguageVersion::of))

                withPlugin("maven-publish") {
                    withJavadocJar()
                    withSourcesJar()
                }
            }
        }

        withPlugin("maven-publish") {
            val repository = "Fleshgrinder/${rootProject.name}"
            val browserUrl = "https://github.com/$repository"
            extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    suppressAllPomMetadataWarnings()
                    pom {
                        url.set(browserUrl)
                        project.description?.ifBlank { null }?.let(::setDescription)
                        // TODO publication should contain commit ID property.
                        ciManagement {
                            system.set("GitHub")
                            url.set("$browserUrl/actions")
                        }
                        developers {
                            developer {
                                id.set("Fleshgrinder")
                                name.set("Richard Fussenegger")
                                email.set("github@fleshgrinder.com")
                                url.set("https://fleshgrinder.com/")
                            }
                        }
                        issueManagement {
                            system.set("GitHub")
                            url.set("$browserUrl/issues")
                        }
                        // TODO how to deal with other licenses, especially dual?
                        rootProject.layout.projectDirectory.file("UNLICENSE").asFile.takeIf { it.exists() }?.let {
                            licenses {
                                license {
                                    name.set("Unlicense")
                                    distribution.set("repo")
                                    url.set("$browserUrl/blob/main/UNLICENSE")
                                }
                            }
                        } ?: logger.error("Publication of {} does not have a license!", project.path)
                        scm {
                            url.set(browserUrl)
                            connection.set("scm:$browserUrl.git")
                            developerConnection.set("scm:git@github.com:$repository.git")
                        }
                    }
                }
            }
        }
    }

    tasks.apply {
        withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }

        withType<Test>().configureEach {
            systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
            testLogging.apply {
                events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                showCauses = true
                showExceptions = true
                showStackTraces = true
                showStandardStreams = true
            }
        }
    }
}
