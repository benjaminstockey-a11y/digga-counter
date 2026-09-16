package com.example.diggacounter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diggacounter.data.AppDatabase
import com.example.diggacounter.data.Person
import com.example.diggacounter.data.PersonRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class DiggaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(AppDatabase.get(application).personDao())

    val persons: StateFlow<List<Person>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPerson(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addPerson(name.trim()) }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch { repository.deletePerson(person) }
    }

    /** steps = +1 or -1, always moves the balance by one 50-cent step. */
    fun adjustBalance(personId: Long, steps: Int) {
        viewModelScope.launch { repository.adjustBalance(personId, steps) }
    }
}
