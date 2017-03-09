# kotlin-frontend-plugin
Gradle plugin for kotlin frontend development

The plugin provides easy way to gather maven and npm dependencies, pack bundles (via webpack) and test frontend application using karma. By default the plugin generates all required configs for webpack, karma and manages the corresponding daemons.

By using gradle continuous build you also can get hot module replacement feature (apply code changes in browser on the fly). See corresponding [section below](#hot-module-replacement).

# Howto

### Configure gradle project

Fist of all you have to apply plugin `org.jetbrains.kotlin.frontend` and setup kotlin

```gradle
buildscript {
    ext.kotlin_version = '1.1.0'

    repositories {
        jcenter()
        maven {
            url "https://jitpack.io"
        }
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "com.github.cy6erGn0m:kotlin-frontend-plugin:568610baa1"
    }
}


// apply plugin
apply plugin: 'org.jetbrains.kotlin.frontend'

// apply kotlin2js
apply plugin: 'kotlin2js'

// configure kotlin compiler
compileKotlin2Js {
    kotlinOptions.metaInfo = true
    kotlinOptions.outputFile = "$project.buildDir.path/js/${project.name}.js"
    kotlinOptions.sourceMap = true
    kotlinOptions.moduleKind = 'commonjs'
    kotlinOptions.main = "call"
}
```

### Setup npm dependencies

All frontend plugin settings are applied in `kotlinFrontend` section:

```
kotlinFrontend {
    npm {
        dependency "style-loader" // production dependency
        devDependency "karma"     // development dependency
    }
}
```

### webpack bundler

To create webpack bundle (for both packaing and running dev server)

```
kotlinFrontend {
    webpackBundle {
        bundleName = "main"
    }
}
```

### complete example

See [test/build.gradle](test/build.gradle) for full example

# Building and running

To run dev server (that also will build kotlin sources)

`gradlew run`

to pack bundle

`gradle bundle`

to stop running webpack and karma daemon

`gradle stop`

# Webpack

webpack configuration 

```
kotlinFrontend {
    webpackBundle {
        bundleName = "main"
        sourceMapEnabled = true | false   // enable/disable source maps 
        contentPath = file(...) // a file that represents a directory to be served by dev server)
        publicPath = "/"  // web prefix
        port = 8088   // dev server port
        proxyUrl = "" | "http://...."  // URL to be proxied, useful to proxy backend webserver
        stats = "errors-only"  // log level
    }
}
```

dev server log is located at `build/logs/webpack-dev-server.log`

config file is generated at `build/webpack.config.js`

## webpack config customization

For webpack cnfig you can apply additional script by placing a small scripts to directory `webpack.config.d` that will be appended to the end of config script. Use number prefix to change order (it is very similar to UNIX rc.d config directories)

Sample structure:

- [DIR] webpack.config.d
  - css.js
  - minify.js
  - 10-apply-ealier.js
  - 20-apply-later.js

# Karma

karma configuration

```
kotlinFrontend {
    karma {
        port = 9876
        runnerPort = 9100
        reporters = listOf("progress") 
        frameworks = listOf("qunit") // for now only qunit works as intended
        preprocessors = listOf("...")
    }
}
```

karma log is located at `build/logs/karma.log`

config file is generated at `build/karma.conf.js`

# Hot module replacement

Webpack provides ability to apply code changes on the fly with no page reload (if possible). For reference see [Webpack Hot Module Replacement documentation](https://webpack.js.org/concepts/hot-module-replacement/)

Webpack does a lot of work for you however to get it working well most likely you have to implement state save and restore functionality via webpack's API. See [HMR.kt](test/src/main/kotlin/test/hello/HMR.kt) for corresponding Kotlin external declarations for webpack API and [main.kt](test/src/main/kotlin/test/hello/main.kt) for sample save/load.

Briefly at module load accept HMR feature and listen for disposal

```kotlin
module.hot?.let { hot ->
    hot.accept() // accept hot reload
    
    hot.dispose { data -> // listen for disposal events
        data.my-fields = [your application state] // put your state in the 'data' object
    }
}
```

To get previously saved state at module load use `module.hot?.data`

```kotlin
    module.hot?.data?.let { data -> // if we have previous state then we are in the middle of HMR
        myRestoreFunction(data) // so get state from the 'data' object
    }
```

Finally use gradle continuous build with run task to get live replacement every time you change your code.

```
gradlew -t run
```
