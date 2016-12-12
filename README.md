# kotlin-frontend-plugin
Gradle plugin for kotlin frontend development

# Howto
1. Install snapshot

```bash
git clone git@github.com:cy6erGn0m/kotlin-frontend-plugin.git
cd kotlin-frontend-plugin/kotlin-frontend
./gradlew publishToMavenLocal
```

2. Configure your project

```gradle
buildscript {
    ext.kotlin_version = '1.1.0-dev-5310'

    repositories {
        jcenter()
        mavenLocal()
        maven {
            url "https://dl.bintray.com/kotlin/kotlin-dev"
        }
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "org.jetbrains.kotlin:kotlin-frontend:0.0.1-SNAPSHOT"
    }
}


// apply plugin
apply plugin: 'org.jetbrains.kotlin.frontend'

kotlinFrontend {
    npm {
        // your application dependency
        dependency "style-loader"

        // development dependency
        devDependency("karma")
    }

    webpackBundle {
        bundleName = "main"
        contentPath = file('src/main/web')
    }
    
//    rollupBundle {
//        bundleName = "rolledUp"
//    }

//    allBundles {
//        /* set properties for all bundles */
//    }

//    bundle("someBundler") {
//        ....
//    }
}

```

