package com.ecosentinel.appblocker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ecosentinel.appblocker.data.entity.MotivationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MotivationDao {
    @Query("SELECT * FROM motivations")
    fun getAll(): Flow<List<MotivationEntity>>

    @Query("SELECT * FROM motivations ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): MotivationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MotivationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MotivationEntity>)

    @Delete
    suspend fun delete(entity: MotivationEntity)

    @Query("SELECT COUNT(*) FROM motivations")
    suspend fun getCount(): Int
}
