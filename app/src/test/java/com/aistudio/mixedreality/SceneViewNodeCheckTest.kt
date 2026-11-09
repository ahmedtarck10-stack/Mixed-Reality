package com.aistudio.mixedreality

import io.github.sceneview.SceneScope
import io.github.sceneview.node.Node
import org.junit.Test

class SceneViewNodeCheckTest {
    @Test
    fun inspectSceneScope() {
        println("=== SceneScope methods ===")
        for (m in SceneScope::class.java.methods) {
            if (m.declaringClass != Any::class.java) {
                println("${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
            }
        }
        println("=== Node setters ===")
        for (m in Node::class.java.methods) {
            if (m.name.contains("Rotation", ignoreCase = true) || m.name.contains("Scale", ignoreCase = true)) {
                println("${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
            }
        }
    }
}
