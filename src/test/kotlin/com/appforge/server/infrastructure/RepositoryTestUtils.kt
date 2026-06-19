package com.appforge.server.infrastructure

fun setRepositoryFactory(target: Any, factory: RepositoryFactory, fieldName: String = "repositoryFactory") {
    val field = target::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, factory)
}

fun setPrivateField(target: Any, fieldName: String, value: Any?) {
    val field = target::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, value)
}
