apply(plugin = "org.springframework.boot")

dependencies {
    // add-ons
    implementation(project(":infrastructure:jpa"))
    implementation(project(":infrastructure:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // batch
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":infrastructure:jpa")))
    testImplementation(testFixtures(project(":infrastructure:redis")))
}
