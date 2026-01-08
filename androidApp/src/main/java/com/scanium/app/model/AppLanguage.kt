package com.scanium.app.model

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    EN("en"),
    ES("es"),
    IT("it"),
    FR("fr"),
    NL("nl"),
    DE("de"),
    PT_BR("pt-BR");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
    }
}
