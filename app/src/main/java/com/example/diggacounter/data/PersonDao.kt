package com.example.diggacounter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM persons")
    suspend fun getAll(): List<Person>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: Long): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("UPDATE persons SET balanceCents = balanceCents + :deltaCents WHERE id = :id")
    suspend fun adjustBalance(id: Long, deltaCents: Int)

    @Query("UPDATE persons SET voiceProfilePath = :path WHERE id = :id")
    suspend fun setVoiceProfilePath(id: Long, path: String?)
}
