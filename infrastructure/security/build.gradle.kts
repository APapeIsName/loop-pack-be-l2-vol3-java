plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    implementation("org.springframework.security:spring-security-crypto")
}
