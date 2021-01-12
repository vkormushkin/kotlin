/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.target.ClangArgs

plugins {
    `kotlin`
    `native-interop-plugin`
    `native`
}


val libclangextProject = project(":kotlin-native:libclangext")
val libclangextTask = libclangextProject.path + ":build"
val libclangextDir = libclangextProject.buildDir
val libclangextIsEnabled = false //libclangextProject.isEnabled
val llvmDir = project.findProperty("llvmDir")


val libclang =
    if (HostManager.hostIsMingw) {
        "bin/libclang.dll"
    } else {
        "lib/${System.mapLibraryName("clang")}"
    }

val cflags = mutableListOf( "-I$llvmDir/include",
        "-I${project(":kotlin-native:libclangext").projectDir.absolutePath}/src/main/include")

val ldflags = mutableListOf("$llvmDir/$libclang", "-L${libclangextDir.absolutePath}", "-lclangext")

if (libclangextIsEnabled) {
    assert(HostManager.hostIsMac)
    ldflags.addAll(listOf("-Wl,--no-demangle", "-Wl,-search_paths_first", "-Wl,-headerpad_max_install_names", "-Wl,-U,_futimens",
                       "-Wl,-U,__ZN4llvm7remarks11parseFormatENS_9StringRefE",
                       "-Wl,-U,__ZN4llvm7remarks22createRemarkSerializerENS0_6FormatENS0_14SerializerModeERNS_11raw_ostreamE",
                       "-Wl,-U,__ZN4llvm7remarks14YAMLSerializerC1ERNS_11raw_ostreamENS0_14UseStringTableE"))

    val llvmLibs = listOf(
            "clangAST", "clangASTMatchers", "clangAnalysis", "clangBasic", "clangDriver", "clangEdit",
            "clangFrontend", "clangFrontendTool", "clangLex", "clangParse", "clangSema", "clangEdit",
            "clangRewrite", "clangRewriteFrontend", "clangStaticAnalyzerFrontend",
            "clangStaticAnalyzerCheckers", "clangStaticAnalyzerCore", "clangSerialization",
            "clangToolingCore",
            "clangTooling", "clangFormat", "LLVMTarget", "LLVMMC", "LLVMLinker", "LLVMTransformUtils",
            "LLVMBitWriter", "LLVMBitReader", "LLVMAnalysis", "LLVMProfileData", "LLVMCore",
            "LLVMSupport", "LLVMBinaryFormat", "LLVMDemangle"
    ).map { "$llvmDir/lib/lib${it}.a" }

    ldflags.addAll(llvmLibs)
    ldflags.addAll(listOf("-lpthread", "-lz", "-lm", "-lcurses"))
}

val solib = when{
    HostManager.hostIsMingw -> "dll"
    HostManager.hostIsMac -> "dylib"
    else -> "so"
}


native {
    val obj = if (HostManager.hostIsMingw) "obj" else "o"
    val host = rootProject.project(":kotlin-native").extra["hostName"]
    val hostLibffiDir = rootProject.project(":kotlin-native").extra["${host}LibffiDir"]
    suffixes {
        (".c" to ".$obj") {
            tool(*platformManager.hostPlatform.clang.clangC("").toTypedArray())
            when (org.jetbrains.kotlin.konan.target.HostManager.host.family) {
                org.jetbrains.kotlin.konan.target.Family.LINUX -> {
                    flags(*cflags.toTypedArray(),  *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.MINGW -> {
                    flags(*cflags.toTypedArray(),  *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.OSX -> {
                    flags(*cflags.toTypedArray(), "-g", *platformManager.hostPlatform.clang.hostCompilerArgsForJni,
                          "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
            }
        }
        (".cpp" to ".$obj") {
            tool(*platformManager.hostPlatform.clang.clangCXX("").toTypedArray())
            when (org.jetbrains.kotlin.konan.target.HostManager.host.family) {
                org.jetbrains.kotlin.konan.target.Family.LINUX -> {
                    flags("-std=c++11",  *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.MINGW -> {
                    flags("-std=c++11",  *platformManager.hostPlatform.clang.hostCompilerArgsForJni, "-c", "-o", ruleOut(), ruleInFirst())
                }
                org.jetbrains.kotlin.konan.target.Family.OSX -> {
                    flags("-std=c++11", "-g", *platformManager.hostPlatform.clang.hostCompilerArgsForJni,
                          "-fPIC", "-c", "-o", ruleOut(), ruleInFirst())
                }
            }
        }

    }
    sourceSet {
        "main-c" {
            dir("prebuilt/nativeInteropStubs/c")
        }
        "main-cpp" {
            dir("src/nativeInteropStubs/cpp")
        }
    }
    val objSet = arrayOf(sourceSets["main-c"]!!.transform(".c" to ".$obj"),
                         sourceSets["main-cpp"]!!.transform(".cpp" to ".$obj"))

    target("libclangstubs.$solib", *objSet) {
        tool(*platformManager.hostPlatform.clang.clangCXX("").toTypedArray())
        flags(
            "-shared",
            "-o", ruleOut(), *ruleInAll(),
            *ldflags.toTypedArray())
    }
}

tasks["libclangstubs.$solib"].apply {
    dependsOn(":kotlin-native:libclangext:libclangext.a")
}
/*
model {
    tasks.compileClangstubsSharedLibraryClangstubsC {
        UtilsKt.configureNativePluginTask(project, delegate)
    }
    tasks.compileClangstubsSharedLibraryClangstubsCpp {
        UtilsKt.configureNativePluginTask(project, delegate)
    }
    components {
        clangstubs(NativeLibrarySpec) {
            sources {
                c.source.srcDir 'prebuilt/nativeInteropStubs/c'
                cpp.source.srcDir 'src/nativeInteropStubs/cpp'
            }

            binaries.all {
                cCompiler.args hostPlatform.clang.hostCompilerArgsForJni
                cCompiler.args.addAll(cflags)
                cppCompiler.args.add("-std=c++11")
            }

            binaries.withType(SharedLibraryBinarySpec) {
                linker.args.addAll(ldflags)
            }
        }
   }

  toolChains {
    clang(Clang) {
        path UtilsKt.getClangPath(project)
        eachPlatform {
            cppCompiler.withArguments(ClangArgs.&filterGradleNativeSoftwareFlags)
            cCompiler.withArguments(ClangArgs.&filterGradleNativeSoftwareFlags)
        }
    }
  }
}*/

sourceSets {
    "main" {
        java {
            srcDirs("prebuilt/nativeInteropStubs/kotlin")
        }
        kotlin{

            target {

            }
        }
    }
}


dependencies {
    compile(project(":kotlin-stdlib"))
    compile(project(":kotlin-native:Interop:Runtime"))
}

val nativelibs = project.tasks.create<Copy>("nativelibs") {
    dependsOn("libclangstubs.$solib")

    from("$buildDir/")
    into("$buildDir/nativelibs/")
}

//classes.dependsOn nativelibs

kotlinNativeInterop {
    this.create("clang") {
        defFile("clang.def")
        compilerOpts(cflags)
        linkerOpts = ldflags
        genTask.dependsOn(libclangextTask)
        genTask.inputs.dir(libclangextDir)
    }
}
val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks

compileKotlin.kotlinOptions.allWarningsAsErrors=true

tasks.matching { it.name == "linkClangstubsSharedLibrary" }.all {
    dependsOn(libclangextTask)
    inputs.dir(libclangextDir)
}

tasks.create("updatePrebuilt") {
    dependsOn("genClangInteropStubs")

    doLast {
        copy {
            from("$buildDir/nativeInteropStubs/clang/kotlin") {
                include("clang/clang.kt")
            }
            into("prebuilt/nativeInteropStubs/kotlin")
        }

        copy {
            from("$buildDir/interopTemp") {
                include("clangstubs.c")
            }
            into("prebuilt/nativeInteropStubs/c")
        }
    }
}
