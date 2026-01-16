plugins {
    `java-library`
}

group = "me.internalizable"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

