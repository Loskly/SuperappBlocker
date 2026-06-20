package com.ecosentinel.appblocker.tracker

enum class AppCategory(val id: String, val displayName: String) {
    GAME("game", "Игры"),
    AUDIO("audio", "Аудио"),
    VIDEO("video", "Видео"),
    IMAGE("image", "Фото"),
    SOCIAL("social", "Соцсети"),
    NEWS("news", "Новости"),
    MAPS("maps", "Карты"),
    PRODUCTIVITY("productivity", "Работа"),
    ACCESSIBILITY("accessibility", "Доступность"),
    BROWSER("browser", "Браузеры"),
    OTHER("other", "Другое");

    companion object {
        fun fromId(id: String): AppCategory? = entries.find { it.id == id }

        fun selectableCategories(): List<AppCategory> = entries.filter { it != ACCESSIBILITY }
    }
}
