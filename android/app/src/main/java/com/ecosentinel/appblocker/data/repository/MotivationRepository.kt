package com.ecosentinel.appblocker.data.repository

import com.ecosentinel.appblocker.data.dao.MotivationDao
import com.ecosentinel.appblocker.data.entity.MotivationEntity
import kotlinx.coroutines.flow.Flow

class MotivationRepository(private val dao: MotivationDao) {

    val allMotivations: Flow<List<MotivationEntity>> = dao.getAll()

    suspend fun getRandomMotivation(): String {
        ensureDefaultMotivations()
        val random = dao.getRandom()
        return random?.text ?: "Подумай о том, что ты сейчас делаешь."
    }

    suspend fun addCustomMotivation(text: String) {
        dao.insert(MotivationEntity(text = text, isDefault = false))
    }

    suspend fun deleteMotivation(entity: MotivationEntity) {
        dao.delete(entity)
    }

    private suspend fun ensureDefaultMotivations() {
        if (dao.getCount() == 0) {
            val defaults = listOf(
                "Твой конкурент сейчас работает.",
                "Отложив это на завтра, ты предаёшь себя сегодняшнего.",
                "Каждая секунда прокрастинации отдаляет тебя от цели.",
                "Тебе действительно это нужно, или ты просто бежишь от реальности?",
                "Твоя сила воли слабее твоего желания полистать ленту?",
                "Время, которое ты сейчас тратишь, уже не вернуть.",
                "Не обманывай себя, ты не 'отдыхаешь', ты деградируешь.",
                "Ещё 5 минут здесь не сделают тебя счастливее.",
                "Ты выбираешь дешевый дофамин вместо реальных достижений.",
                "Посмотри правде в глаза: ты просто сдаешься.",
                "Будущий 'ты' будет ненавидеть тебя за это решение."
            ).map { MotivationEntity(text = it, isDefault = true) }
            
            dao.insertAll(defaults)
        }
    }
}
