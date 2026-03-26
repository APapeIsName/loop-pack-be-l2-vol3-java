rootProject.name = "loopers-java-spring-template"

include(
    ":domain",
    ":application:commerce-service",
    ":presentation:commerce-api",
    ":presentation:commerce-batch",
    ":presentation:commerce-streamer",
    ":presentation:fcfs-demo",
    ":infrastructure:jpa",
    ":infrastructure:redis",
    ":infrastructure:kafka",
    ":infrastructure:security",
    ":supports:jackson",
    ":supports:logging",
    ":supports:monitoring",
    ":supports:error",
)

// configurations
pluginManagement {
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings

    repositories {
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        gradlePluginPortal()
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.springframework.boot" -> useVersion(springBootVersion)
                "io.spring.dependency-management" -> useVersion(springDependencyManagementVersion)
            }
        }
    }
}
