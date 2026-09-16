package com.example.diggacounter.data

import kotlinx.coroutines.flow.Flow

/** 50 cents, the fixed unit every automatic detection and manual +/- step uses. */
const val STEP_CENTS = 50

class PersonRepository(private val dao: PersonDao) {

    fun observeAll(): Flow<List<Person>> = dao.observeAll()

    suspend fun getAll(): List<Person> = dao.getAll()

    suspend fun getById(id: Long): Person? = dao.getById(id)

    suspend fun addPerson(name: String): Long = dao.insert(Person(name = name))

    suspend fun deletePerson(person: Person) = dao.delete(person)

    suspend fun setVoiceProfilePath(personId: Long, path: String?) =
        dao.setVoiceProfilePath(personId, path)

    /** Positive [steps] adds, negative subtracts, always in STEP_CENTS increments. */
    suspend fun adjustBalance(personId: Long, steps: Int) =
        dao.adjustBalance(personId, steps * STEP_CENTS)

    /** Called each time "Digga" is attributed to this person by voice matching. */
    suspend fun registerDigga(personId: Long) =
        dao.adjustBalance(personId, STEP_CENTS)
}
