package com.mreil.easy.vcs

data class VcsInfo(
    val type: VcsType,
    val branch: String?,
    val clean: Boolean,
)
