package com.fuddud.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_logs")
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val weightGrams: Double,
    val timestamp: Long
)

@Entity(tableName = "daily_summaries")
data class DailySummary(
    @PrimaryKey val date: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

// Новая таблица для пользовательских локальных продуктов
@Entity(tableName = "custom_foods")
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double, // на 100г
    val protein: Double,  // на 100г
    val carbs: Double,    // на 100г
    val fat: Double       // на 100г
)

@Dao
interface CalorieDao {
    @Insert
    suspend fun insertLog(log: FoodLog)

    @Delete
    suspend fun deleteLog(log: FoodLog)

    @Query("SELECT * FROM food_logs WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getLogsForDay(startOfDay: Long, endOfDay: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE timestamp >= :since")
    fun getLogsSince(since: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_logs WHERE timestamp < :limitTime")
    suspend fun getLogsOlderThan(limitTime: Long): List<FoodLog>

    @Query("DELETE FROM food_logs WHERE timestamp < :limitTime")
    suspend fun deleteLogsOlderThan(limitTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: DailySummary)

    @Query("SELECT * FROM daily_summaries WHERE date = :date")
    suspend fun getSummaryForDate(date: String): DailySummary?

    @Query("SELECT * FROM daily_summaries ORDER BY date ASC")
    fun getAllSummaries(): Flow<List<DailySummary>>

    // Методы для управления вашими личными продуктами
    @Query("SELECT * FROM custom_foods ORDER BY name ASC")
    fun getAllCustomFoods(): Flow<List<CustomFood>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFood(food: CustomFood)

    @Delete
    suspend fun deleteCustomFood(food: CustomFood)
}

// Добавили CustomFood в список сущностей БД
@Database(entities = [FoodLog::class, DailySummary::class, CustomFood::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calorieDao(): CalorieDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calorie_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
