plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api("jakarta.persistence:jakarta.persistence-api")
    api(project(":supports:error"))
}
