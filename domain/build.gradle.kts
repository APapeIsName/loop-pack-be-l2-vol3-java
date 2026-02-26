plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api("jakarta.persistence:jakarta.persistence-api")
    api(project(":supports:error"))
    implementation("org.springframework:spring-context")

    // querydsl Q클래스 생성 (엔티티가 domain에 위치하므로 여기서 apt 실행)
    api("com.querydsl:querydsl-jpa::jakarta")
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
}
