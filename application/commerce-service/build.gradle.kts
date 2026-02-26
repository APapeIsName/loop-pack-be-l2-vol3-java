plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))

    // @Service, @Transactional
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-context")

    testImplementation(testFixtures(project(":domain")))
}
