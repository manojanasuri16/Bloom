package com.bloom.app.domain

data class Option(val id: String, val label: String)

/** Stable ids are persisted; labels may be reworded freely. */
object Options {
    val symptoms = listOf(
        Option("cramps", "Cramps"), Option("headache", "Headache"), Option("bloating", "Bloating"),
        Option("backache", "Back pain"), Option("breast_tender", "Tender breasts"), Option("acne", "Acne"),
        Option("fatigue", "Fatigue"), Option("nausea", "Nausea"), Option("cravings", "Cravings"),
        Option("insomnia", "Poor sleep"), Option("dizzy", "Dizziness"), Option("diarrhea", "Digestive upset"),
        Option("hot_flash", "Hot flashes"), Option("libido", "High libido"),
    )
    val moods = listOf(
        Option("happy", "Happy"), Option("calm", "Calm"), Option("energetic", "Energetic"),
        Option("sensitive", "Sensitive"), Option("anxious", "Anxious"), Option("irritable", "Irritable"),
        Option("sad", "Low"), Option("tired", "Tired"), Option("focused", "Focused"), Option("moody", "Mood swings"),
    )
    val discharge = listOf(
        Option("none", "None"), Option("sticky", "Sticky"), Option("creamy", "Creamy"),
        Option("watery", "Watery"), Option("eggwhite", "Egg white"), Option("unusual", "Unusual"),
    )

    fun symptomLabel(id: String) = symptoms.firstOrNull { it.id == id }?.label ?: id
    fun moodLabel(id: String) = moods.firstOrNull { it.id == id }?.label ?: id
    fun dischargeLabel(id: String) = discharge.firstOrNull { it.id == id }?.label ?: id
}
