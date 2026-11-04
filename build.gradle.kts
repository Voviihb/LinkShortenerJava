plugins {
    application
    checkstyle
}

group = "ru.linkshortener"
version = "1.0.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

application {
    mainClass = "ru.linkshortener.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

checkstyle {
    toolVersion = "10.26.1"
    isIgnoreFailures = false
    maxWarnings = 0
}
