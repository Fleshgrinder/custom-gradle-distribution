@file:Suppress("UnstableApiUsage")

import org.gradle.api.internal.GradleInternal
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf

val gradle: GradleInternal = getGradle() as GradleInternal
val rootDir: File = gradle.owner.buildRootDir
val objects: ObjectFactory = gradle.serviceOf()
val providers: ProviderFactory = gradle.serviceOf()

val javaVersion: String? = when {
    // The environment variable provides the ability to test building the
    // project with a different Java version other than the one in the
    // `.java-version` file. This is useful in matrix builds.
    //
    // We have to resolve the file contents provider right away, to ensure that
    // it becomes an input of the configuration model. Not doing so would mean
    // that changes to the file are not picked up whenever configuration
    // caching is active. The file contents provider is also live, meaning, if
    // we use it in multiple places we might end up reading the file over and
    // over again, and even with different content at different stages. We do
    // not want that. Whatever Java version we find at the beginning of the
    // build is the Java version we use throughout the entire build.
    gradle.isRootBuild -> providers.environmentVariable("JAVA_VERSION")
        .orElse(
            rootDir.resolve(".java-version")
                .let(objects.fileProperty()::fileValue)
                .let(providers::fileContents)
                .asText
        )
        .orNull
        ?.trim()
        ?.removePrefix("1.")
        ?.substringBefore('.')
        ?.ifBlank { null }
        .also {
            when {
                it == null -> logger.warn("Could not find '.java-version' file in: {}", rootDir)
                logger.isInfoEnabled -> logger.info("Resolved {} from '.java-version' file in: {}", it, rootDir)
            }
        }

    else -> gradle.root.settings.extra["javaVersion"] as String?
}

beforeSettings {
    // We store the resolved version in the extra properties so that we can
    // retrieve it in included builds, without resolving it again.
    extra["javaVersion"] = javaVersion

    if (javaVersion != null) {
        dependencyResolutionManagement.versionCatalogs.maybeCreate("libs").version("java", javaVersion)
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

                if (javaVersion != null) {
                    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
                }

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
