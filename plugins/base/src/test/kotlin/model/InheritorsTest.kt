package model

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.base.transformers.documentables.InheritorsExtractorTransformer
import org.jetbrains.dokka.base.transformers.documentables.InheritorsInfo
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utils.AbstractModelTest
import utils.assertNotNull

class InheritorsTest : AbstractModelTest("/src/main/kotlin/inheritors/Test.kt", "inheritors") {

    object InheritorsPlugin : DokkaPlugin() {
        val inheritors by extending {
            CoreExtensions.documentableTransformer with InheritorsExtractorTransformer()
        }
    }

    @Test
    fun simple() {
        inlineModelTest(
            """|interface A{}
               |class B() : A {}
            """.trimMargin(),
            pluginsOverrides = listOf(InheritorsPlugin)
        ) {
            with((this / "inheritors" / "A").cast<DInterface>()) {
                val map = extra[InheritorsInfo].assertNotNull("InheritorsInfo").value.map
                with(map.keys.also { it counts 1 }.find { it.platformType == Platform.jvm }.assertNotNull("jvm key").let { map[it]!! }
                ) {
                    this counts 1
                    first().classNames equals "B"
                }
            }
        }
    }

    @Test
    fun multiplatform() {
        val configuration = dokkaConfiguration {
            passes {
                pass {
                    sourceRoots = listOf("common/src/", "jvm/src/")
                    analysisPlatform = "jvm"
                    targets = listOf("jvm")
                }
                pass {
                    sourceRoots = listOf("common/src/", "js/src/")
                    analysisPlatform = "js"
                    targets = listOf("js")
                }
            }
        }

        testInline(
            """
            |/common/src/main/kotlin/inheritors/Test.kt
            |package inheritors
            |interface A{}
            |/jvm/src/main/kotlin/inheritors/Test.kt
            |package inheritors
            |class B() : A {}
            |/js/src/main/kotlin/inheritors/Test.kt
            |package inheritors
            |class B() : A {}
            |class C() : A {}
        """.trimMargin(),
            configuration,
            cleanupOutput = false,
            pluginOverrides = listOf(InheritorsPlugin)
        ) {
            documentablesTransformationStage = { m ->
                with((m / "inheritors" / "A").cast<DInterface>()) {
                    val map = extra[InheritorsInfo].assertNotNull("InheritorsInfo").value.map
                    with(map.keys.also { it counts 2 }) {
                        with(find { it.platformType == Platform.jvm }.assertNotNull("jvm key").let { map[it]!! }) {
                            this counts 1
                            first().classNames equals "B"
                        }
                        with(find { it.platformType == Platform.js }.assertNotNull("js key").let { map[it]!! }) {
                            this counts 2
                            val classes = listOf("B", "C")
                            assertTrue(all { classes.contains(it.classNames) }, "One of subclasses missing in js" )
                        }
                    }

                }
            }
        }
    }
}