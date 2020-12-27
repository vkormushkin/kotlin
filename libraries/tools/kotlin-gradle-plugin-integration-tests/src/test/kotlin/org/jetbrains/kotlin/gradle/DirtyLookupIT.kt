/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import org.junit.Test

public class DirtyLookupIT : BaseGradleIT() {

    @Test //https://youtrack.jetbrains.com/issue/KT-28233
    fun testChangeTypeAlias() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.build(":lib:build") {
            assertSuccessful()
        }

        project.projectDir.resolve("src/kotlin/test/types.kt").writeText("typealias Type = Int")
        project.projectDir.resolve("src/kotlin/test/FooImpl").writeText("package test\n" +
                                                                                "class FooImpl : Foo {\n" +
                                                                                "    override val values: Type\n" +
                                                                                "        get() = 0\n" +
                                                                                "}")
        project.build("build") {
            assertSuccessful()
        }
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-40656
    fun testCompanionObjectChanges() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.resolve("src/kotlin/test/TestClass")
            .writeText(
                "package test\n" +
                        "class TestClass {\n" +
                        "    companion object {\n" +
                        "        privateconst val text = \"some simple text...\"\n" +
                        "    }\n" +
                        "}"
            )

        project.build("build") {
            assertFailed()
        }
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-25455
    fun testOverrideMethod() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.build("build") {
            assertSuccessful()
        }

        project.projectDir.resolve("src/kotlin/test/FooExtended")
            .writeText(
                "package test\n" +
                        "class FooExtended {}"
            )

        project.build("build") {
            assertFailed()
        }
    }

    @Test //https://youtrack.jetbrains.com/issue/KT-13677
    fun changeMemberVisibility() {

    }
}
