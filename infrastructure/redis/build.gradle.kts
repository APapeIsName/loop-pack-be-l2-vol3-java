plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("com.github.ben-manes.caffeine:caffeine")

    testFixturesImplementation("com.redis:testcontainers-redis")
}
