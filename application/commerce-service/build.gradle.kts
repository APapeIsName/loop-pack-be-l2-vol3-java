plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))

    // @Service, @Transactional
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-context")

    // Jackson ObjectMapper
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(testFixtures(project(":domain")))
}
