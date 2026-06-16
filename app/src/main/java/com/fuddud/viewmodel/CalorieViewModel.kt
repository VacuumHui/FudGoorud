package com.fuddud.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.fuddud.data.*
import com.fuddud.network.ProductDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DailyTotal(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
)

class CalorieViewModel(
    private val repository: CalorieRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    // --- Настройки профиля (считывание из SharedPreferences) ---
    var isDarkTheme by mutableStateOf(prefs.getBoolean("is_dark_theme", false))
        private set

    var targetCalories by mutableStateOf(prefs.getFloat("target_calories", 2000f).toDouble())
        private set
    var targetProtein by mutableStateOf(prefs.getFloat("target_protein", 130f).toDouble())
        private set
    var targetCarbs by mutableStateOf(prefs.getFloat("target_carbs", 230f).toDouble())
        private set
    var targetFat by mutableStateOf(prefs.getFloat("target_fat", 65f).toDouble())
        private set

    fun toggleTheme(isDark: Boolean) {
        isDarkTheme = isDark
        prefs.edit().putBoolean("is_dark_theme", isDark).apply()
    }

    fun updateGoals(calories: Double, protein: Double, carbs: Double, fat: Double) {
        targetCalories = calories
        targetProtein = protein
        targetCarbs = carbs
        targetFat = fat
        prefs.edit()
            .putFloat("target_calories", calories.toFloat())
            .putFloat("target_protein", protein.toFloat())
            .putFloat("target_carbs", carbs.toFloat())
            .putFloat("target_fat", fat.toFloat())
            .apply()
    }

    // --- Собственные локальные продукты ---
    val customFoods: StateFlow<List<CustomFood>> = repository.getAllCustomFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCustomFood(name: String, calories: Double, protein: Double, carbs: Double, fat: Double) {
        viewModelScope.launch {
            repository.insertCustomFood(CustomFood(name = name, calories = calories, protein = protein, carbs = carbs, fat = fat))
        }
    }

    fun removeCustomFood(food: CustomFood) {
        viewModelScope.launch {
            repository.deleteCustomFood(food)
        }
    }

    // --- Калории и логи питания ---
    fun changeDate(date: Long) {
        _selectedDate.value = date
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyLogs: Flow<List<FoodLog>> = _selectedDate
        .flatMapLatest { date ->
            repository.getLogsForDay(getStartOfDay(date), getEndOfDay(date))
        }

    val dailyTotal: StateFlow<DailyTotal> = dailyLogs.map { logs ->
        DailyTotal(
            calories = logs.sumOf { it.calories },
            protein = logs.sumOf { it.protein },
            carbs = logs.sumOf { it.carbs },
            fat = logs.sumOf { it.fat }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyTotal())

    val weeklyChartData: StateFlow<List<Pair<String, Double>>> = combine(
        repository.getAllSummaries(),
        repository.getLogsSince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    ) { summaries, recentLogs ->
        val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
        val keySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val list = mutableListOf<Pair<String, Double>>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val label = sdf.format(cal.time)
            val key = keySdf.format(cal.time)

            val dayStart = getStartOfDay(cal.timeInMillis)
            val dayEnd = getEndOfDay(cal.timeInMillis)

            val dayLogsSum = recentLogs
                .filter { it.timestamp in dayStart..dayEnd }
                .sumOf { it.calories }

            val valToUse = if (dayLogsSum > 0) {
                dayLogsSum
            } else {
                summaries.find { it.date == key }?.calories ?: 0.0
            }
            list.add(label to valToUse)
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Сетевой поиск ---
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<ProductDto>>(emptyList())
    var isSearching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    // Хранение найденного по штрих-коду продукта для автооткрытия диалога ввода веса
    var scannedProduct by mutableStateOf<ProductDto?>(null)

    fun performSearch() {
        if (searchQuery.isBlank()) return
        viewModelScope.launch {
            isSearching = true
            searchError = null
            scannedProduct = null
            try {
                val response = repository.searchOnline(searchQuery)
                searchResults = response.products ?: emptyList()
            } catch (e: Exception) {
                searchError = "Ошибка подключения"
            } finally {
                isSearching = false
            }
        }
    }

    // Логика сканирования штрих-кода
    fun searchByBarcode(barcode: String) {
        viewModelScope.launch {
            isSearching = true
            searchError = null
            scannedProduct = null
            try {
                val response = repository.searchBarcodeOnline(barcode)
                if (response.status == 1 && response.product != null) {
                    scannedProduct = response.product
                } else {
                    searchError = "Продукт со штрих-кодом $barcode не найден"
                }
            } catch (e: Exception) {
                searchError = "Ошибка сетевого поиска по штрих-коду"
            } finally {
                isSearching = false
            }
        }
    }

    fun addLog(name: String, cals100g: Double, prot100g: Double, carb100g: Double, fat100g: Double, weight: Double) {
        viewModelScope.launch {
            val factor = weight / 100.0
            val log = FoodLog(
                name = name,
                calories = cals100g * factor,
                protein = prot100g * factor,
                carbs = carb100g * factor,
                fat = fat100g * factor,
                weightGrams = weight,
                timestamp = System.currentTimeMillis()
            )
            repository.insertLog(log)
        }
    }

    fun deleteLog(log: FoodLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
        }
    }

    fun clearCache(context: Context) {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }

    fun optimizeDatabase(context: Context) {
        viewModelScope.launch {
            repository.archiveOldLogsAndOptimize(context)
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getEndOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}

class ViewModelFactory(
    private val repository: CalorieRepository,
    private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalorieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalorieViewModel(repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
