plugins {
    kotlin("jvm") version "1.9.21"
    `maven-publish`
    signing
}

group = "com.github.jhalak"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Core dependencies
    implementation("com.squareup.okio:okio:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Kotlin HAProxy SPOE")
                description.set("A Kotlin library for implementing HAProxy Stream Processing Offload Engine (SPOE) agents")
                url.set("https://github.com/jhalak/kotlin-haproxy-spoe")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("jhalak")
                        name.set("Jhalak K Gurung")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/jhalak/kotlin-haproxy-spoe.git")
                    developerConnection.set("scm:git:ssh://github.com/jhalak/kotlin-haproxy-spoe.git")
                    url.set("https://github.com/jhalak/kotlin-haproxy-spoe")
                }
            }
        }
    }
}
