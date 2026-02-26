apply(plugin = "org.springframework.boot")

dependencies {
    // add-ons
    implementation(project(":infrastructure:jpa"))
    implementation(project(":infrastructure:redis"))
    implementation(project(":infrastructure:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":infrastructure:jpa")))
    testImplementation(testFixtures(project(":infrastructure:redis")))
    testImplementation(testFixtures(project(":infrastructure:kafka")))
}
