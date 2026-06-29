pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kizuna-network-inspector"
include(":app")
include(":platform:vpn")
include(":platform:security")
include(":shared:database")
include(":shared:search")
include(":ui:compose")
