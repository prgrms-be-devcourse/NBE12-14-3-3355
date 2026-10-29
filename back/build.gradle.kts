plugins {
    java
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    kotlin("plugin.jpa") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.gamelog"
version = "0.0.1-SNAPSHOT"
description = "NBE-12-14-2-3355"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.sentry:sentry-spring-boot-4:8.58.0")
    implementation("io.sentry:sentry-logback:8.58.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("com.cloudinary:cloudinary-http44:1.39.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations")
    }
}

tasks.register<JavaExec>("recommendationPrecompute") {
    group = "application"
    description = "로컬 연관 추천을 전체 또는 지정 게임 ID로 사전 계산합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.gamelog.nbe121423355.domain.game.init.RelatedGamePrecomputeCommand")
    systemProperty("spring.devtools.restart.enabled", "false")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}
