package com.mreil.easy

abstract class DefaultEasyExtension : EasyExtension {
    companion object : Named {
        override val name: String = EasyExtension.name
    }
}
