apply(plugin = "org.springframework.boot")

dependencies {
    // layers
    implementation(project(":domain"))
    implementation(project(":application:commerce-service"))
    implementation(project(":infrastructure:jpa"))
    implementation(project(":infrastructure:redis"))
    implementation(project(":infrastructure:security"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // resilience4j
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":infrastructure:jpa")))
    testImplementation(testFixtures(project(":infrastructure:redis")))
}
