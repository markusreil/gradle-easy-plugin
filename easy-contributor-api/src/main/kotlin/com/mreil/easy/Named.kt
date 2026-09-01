package com.mreil.easy

import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

interface Named {
    val name: String

    companion object {
        /**
         * Extracts the extension registration name from the class's companion object, requiring that it implements [Named].
         *
         * @throws IllegalArgumentException if the class does not define a companion object implementing [Named].
         */
        fun extensionName(kClass: KClass<*>): String {
            val companion = kClass.companionObjectInstance
            require(companion is Named) {
                "Extension class ${kClass.qualifiedName} must have a companion object implementing Named"
            }
            return companion.name
        }
    }
}

/** Extension shorthand for [Named.extensionName] — uniform access for every `Named` companion. */
fun KClass<*>.extensionName(): String = Named.extensionName(this)
