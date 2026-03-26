dependencies {
    // JPA + MySQL 직접 (infrastructure:jpa 의존 X — EntityScan 충돌 방지)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Redis, Kafka 인프라 재사용
    implementation(project(":infrastructure:redis"))
    implementation(project(":infrastructure:kafka"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jackson
    implementation(project(":supports:jackson"))
}
