package com.example.diggacounter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceCents: Int = 0,
    // Path to the enrolled Picovoice Eagle voice profile file for this person, null until trained.
    val voiceProfilePath: String? = null
)
