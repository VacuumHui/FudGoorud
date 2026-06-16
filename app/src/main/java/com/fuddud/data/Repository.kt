package com.fuddud.data

import android.content.Context
import com.fuddud.network.OpenFoodFactsApi
import com.fuddud.network.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalorieRepository(
    private val dao: CalorieDao,
    private val db: AppDatabase,
    private val api: OpenFoodFactsApi
) {
    fun getLogsForDay(start: Long, end: Long): Flow<List<FoodLog>> = dao.getLogsForDay(start, end)
    
    fun getLogsSince(since: Long): Flow<List<FoodLog>> = dao.getLogsSince(since)

    fun getAllSummaries(): Flow<List<DailySummary>> = dao.getAllSummaries()

    suspend fun insertLog(log: FoodLog) = dao.insertLog(log)

    suspend fun deleteLog(log: FoodLog) = dao.deleteLog(log)

    suspend fun searchOnline(query: String): SearchResponse {
        return withContext(Dispatchers.IO) {
            api.searchProducts(query)
        }
    }

    suspend fun archiveOldLogsAndOptimize(context: Context) {
        withContext(Dispatchers.IO) {
            val limitTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val oldLogs = dao.getLogsOlderThan(limitTime)

            if (oldLogs.isNotEmpty()) {
                val keySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val grouped = oldLogs.groupBy { keySdf.format(Date(it.timestamp)) }

                grouped.forEach { (date, logs) ->
                    val totalCals = logs.sumOf { it.calories }
                    val totalPro = logs.sumOf { it.protein }
                    val totalCarb = logs.sumOf { it.carbs }
                    val totalFat = logs.sumOf { it.fat }

                    val existingSummary = dao.getSummaryForDate(date)
                    if (existingSummary != null) {
                        dao.insertSummary(
                            existingSummary.copy(
                                calories = existingSummary.calories + totalCals,
                                protein = existingSummary.protein + totalPro,
                                carbs = existingSummary.carbs + totalCarb,
                                fat = existingSummary.fat + totalFat
                            )
                        )
                    } else {
                        dao.insertSummary(
                            DailySummary(date, totalCals, totalPro, totalCarb, totalFat)
                        )
                    }
                }
                dao.deleteLogsOlderThan(limitTime)
            }
            db.openHelper.writableDatabase.execSQL("VACUUM")
        }
    }
}
