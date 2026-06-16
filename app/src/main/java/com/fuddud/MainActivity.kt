package com.fuddud

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuddud.data.AppDatabase
import com.fuddud.data.CalorieRepository
import com.fuddud.data.CustomFood
import com.fuddud.data.FoodLog
import com.fuddud.network.NutrimentsDto
import com.fuddud.network.ProductDto
import com.fuddud.network.RetrofitClient
import com.fuddud.viewmodel.CalorieViewModel
import com.fuddud.viewmodel.ViewModelFactory
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions

enum class Screen { Dashboard, Search, Settings }

class MainActivity : ComponentActivity() {

    private val viewModel: CalorieViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = CalorieRepository(database.calorieDao(), database, RetrofitClient.api)
        val sharedPrefs = getSharedPreferences("fuddud_prefs", MODE_PRIVATE)
        ViewModelFactory(repository, sharedPrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = viewModel.isDarkTheme

            // Реактивно меняем тему во всем приложении при изменении настройки
            MaterialTheme(
                colorScheme = if (isDark) {
                    darkColorScheme(
                        primary = Color(0xFF81C784),
                        background = Color(0xFF121212),
                        surface = Color(0xFF1E1E1E),
                        onBackground = Color(0xFFFFFFFF),
                        onSurface = Color(0xFFFFFFFF)
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF81C784),
                        background = Color(0xFFF7F9FA),
                        surface = Color(0xFFFFFFFF),
                        onBackground = Color(0xFF1C1B1F),
                        onSurface = Color(0xFF1C1B1F)
                    )
                }
            ) {
                MainAppScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: CalorieViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentScreen == Screen.Dashboard,
                    onClick = { currentScreen = Screen.Dashboard },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Search,
                    onClick = { currentScreen = Screen.Search },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                    label = { Text("Поиск", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Settings,
                    onClick = { currentScreen = Screen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Очистка") },
                    label = { Text("Очистка", fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(viewModel)
                Screen.Search -> SearchScreen(viewModel)
                Screen.Settings -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: CalorieViewModel) {
    val dailyTotal by viewModel.dailyTotal.collectAsState()
    val logs by viewModel.dailyLogs.collectAsState(initial = emptyList())
    val chartData by viewModel.weeklyChartData.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Сводка за сегодня",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalorieProgressRing(
                        consumed = dailyTotal.calories,
                        target = viewModel.targetCalories,
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    Column {
                        Text("Белки: ${dailyTotal.protein.toInt()} г / ${viewModel.targetProtein.toInt()} г", fontSize = 14.sp)
                        Text("Жиры: ${dailyTotal.fat.toInt()} г / ${viewModel.targetFat.toInt()} г", fontSize = 14.sp)
                        Text("Углеводы: ${dailyTotal.carbs.toInt()} г / ${viewModel.targetCarbs.toInt()} г", fontSize = 14.sp)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Тренд за неделю",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MinimalistWeeklyChart(
                        weeklyData = chartData,
                        targetCalories = viewModel.targetCalories,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "Дневник питания",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "Список пуст. Добавьте продукты во вкладке Поиск",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(logs) { log ->
                LogItem(log = log, onDelete = { viewModel.deleteLog(log) })
            }
        }
    }
}

@Composable
fun LogItem(log: FoodLog, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(log.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("${log.weightGrams.toInt()} г • Б: ${log.protein.toInt()} Ж: ${log.fat.toInt()} У: ${log.carbs.toInt()}", fontSize = 12.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${log.calories.toInt()} ккал", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: CalorieViewModel) {
    val context = LocalContext.current
    var showWeightDialog by remember { mutableStateOf<ProductDto?>(null) }
    var showCreateCustomDialog by remember { mutableStateOf(false) }
    
    val customFoods by viewModel.customFoods.collectAsState()

    // Настройка GmsBarcodeScanner от Google (работает без разрешений в манифесте)
    val barcodeScanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    // Авто-открытие диалога ввода веса при успешном сканировании
    LaunchedEffect(viewModel.scannedProduct) {
        viewModel.scannedProduct?.let {
            showWeightDialog = it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Поиск и Кнопка сканера
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                label = { Text("Поиск продуктов") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.performSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )

            // Кнопка сканера штрих-кода
            Button(
                onClick = {
                    barcodeScanner.startScan()
                        .addOnSuccessListener { barcode ->
                            barcode.rawValue?.let { code ->
                                viewModel.searchByBarcode(code)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Ошибка сканирования", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Сканер", fontSize = 12.sp)
            }
        }

        // Кнопка создания личного продукта
        Button(
            onClick = { showCreateCustomDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        ) {
            Text("Создать свой продукт", fontWeight = FontWeight.Bold)
        }

        if (viewModel.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        viewModel.searchError?.let {
            Text(it, color = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        // Список результатов
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Сначала показываем созданные пользователем продукты
            if (customFoods.isNotEmpty()) {
                item {
                    Text("Свои продукты:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                items(customFoods) { food ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showWeightDialog = ProductDto(
                                    code = null,
                                    productName = food.name,
                                    nutriments = NutrimentsDto(food.calories, food.protein, food.carbs, food.fat),
                                    imageThumbUrl = null
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(food.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${food.calories.toInt()} ккал • Б: ${food.protein.toInt()} Ж: ${food.fat.toInt()} У: ${food.carbs.toInt()}", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.removeCustomFood(food) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Поисковые продукты из интернета
            if (viewModel.searchResults.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Найденное в сети:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                items(viewModel.searchResults) { product ->
                    ProductSearchItem(product = product, onClick = { showWeightDialog = product })
                }
            }
        }
    }

    // Диалог ввода веса продукта
    showWeightDialog?.let { product ->
        WeightInputDialog(
            product = product,
            onDismiss = {
                showWeightDialog = null
                viewModel.scannedProduct = null
            },
            onConfirm = { weight ->
                viewModel.addLog(
                    name = product.productName ?: "Неизвестный продукт",
                    cals100g = product.nutriments?.energyKcal100g ?: 0.0,
                    prot100g = product.nutriments?.proteins100g ?: 0.0,
                    carb100g = product.nutriments?.carbohydrates100g ?: 0.0,
                    fat100g = product.nutriments?.fat100g ?: 0.0,
                    weight = weight
                )
                showWeightDialog = null
                viewModel.scannedProduct = null
            }
        )
    }

    // Диалог создания своего продукта
    if (showCreateCustomDialog) {
        CreateCustomFoodDialog(
            onDismiss = { showCreateCustomDialog = false },
            onConfirm = { name, kcal, p, f, c ->
                viewModel.saveCustomFood(name, kcal, p, f, c)
                showCreateCustomDialog = false
            }
        )
    }
}

@Composable
fun ProductSearchItem(product: ProductDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = product.imageThumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.LightGray, RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(product.productName ?: "Без названия", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Калорийность: ${product.nutriments?.energyKcal100g?.toInt() ?: 0} ккал / 100г", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun WeightInputDialog(product: ProductDto, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var weightStr by remember { mutableStateOf("100") }
    val weight = weightStr.toDoubleOrNull() ?: 0.0
    val calories100 = product.nutriments?.energyKcal100g ?: 0.0
    val totalCalories = (calories100 * (weight / 100.0)).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.productName ?: "Ввод веса") },
        text = {
            Column {
                Text("Введите вес порции в граммах:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Итоговая калорийность: $totalCalories ккал", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = { if (weight > 0) onConfirm(weight) }) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CreateCustomFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать свой продукт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = kcal, onValueChange = { kcal = it }, label = { Text("Калории на 100г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Белки на 100г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = f, onValueChange = { f = it }, label = { Text("Жиры на 100г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("Углеводы на 100г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            kcal.toDoubleOrNull() ?: 0.0,
                            p.toDoubleOrNull() ?: 0.0,
                            f.toDoubleOrNull() ?: 0.0,
                            c.toDoubleOrNull() ?: 0.0
                        )
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// --- НАСТРОЙКИ ПРОФИЛЯ, ЛИМИТОВ И ОЧИСТКА ---
@Composable
fun SettingsScreen(viewModel: CalorieViewModel) {
    val context = LocalContext.current
    val isDark = viewModel.isDarkTheme

    // Локальные состояния для формы редактирования целей
    var editKcal by remember { mutableStateOf(viewModel.targetCalories.toInt().toString()) }
    var editP by remember { mutableStateOf(viewModel.targetProtein.toInt().toString()) }
    var editF by remember { mutableStateOf(viewModel.targetFat.toInt().toString()) }
    var editC by remember { mutableStateOf(viewModel.targetCarbs.toInt().toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Настройки и память",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Выбор темы
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Тёмная тема", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Переключить тему оформления", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.toggleTheme(it) }
                    )
                }
            }
        }

        // 2. Редактирование целей (нормы питания)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Дневная норма питания", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    
                    OutlinedTextField(
                        value = editKcal,
                        onValueChange = { editKcal = it },
                        label = { Text("Калории (ккал)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editP, onValueChange = { editP = it }, label = { Text("Белки") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = editF, onValueChange = { editF = it }, label = { Text("Жиры") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = editC, onValueChange = { editC = it }, label = { Text("Углеводы") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
                            viewModel.updateGoals(
                                editKcal.toDoubleOrNull() ?: 2000.0,
                                editP.toDoubleOrNull() ?: 130.0,
                                editF.toDoubleOrNull() ?: 65.0,
                                editC.toDoubleOrNull() ?: 230.0
                            )
                            Toast.makeText(context, "Цели сохранены", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить изменения")
                    }
                }
            }
        }

        // 3. Кэш изображений
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Кэш изображений", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Очищает временные эскизы продуктов, загруженных при поиске в интернете.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.clearCache(context)
                            Toast.makeText(context, "Кэш картинок очищен", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Очистить кэш картинок")
                    }
                }
            }
        }

        // 4. Оптимизация БД
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Сжатие и архивация данных", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Сжимает базу данных. Детальная история старше 30 дней переносится в архив (взамен удаляются подробные списки продуктов за прошлый месяц, чтобы не засорять диск, но графики сохранятся).", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.optimizeDatabase(context)
                            Toast.makeText(context, "База данных оптимизирована (VACUUM)", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Оптимизировать память базы")
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalistWeeklyChart(
    weeklyData: List<Pair<String, Double>>,
    targetCalories: Double,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.background
    val targetColor = Color.Red.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingBottom = 40f
        val chartHeight = height - paddingBottom

        val maxVal = maxOf(targetCalories, weeklyData.maxOfOrNull { it.second } ?: 0.0)
        val barWidth = 36f
        val stepX = width / (weeklyData.size + 1)

        val targetY = chartHeight - ((targetCalories / maxVal) * chartHeight).toFloat()
        drawLine(
            color = targetColor,
            start = Offset(0f, targetY),
            end = Offset(width, targetY),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        )

        weeklyData.forEachIndexed { index, pair ->
            val x = stepX * (index + 1)
            val barHeight = ((pair.second / maxVal) * chartHeight).toFloat()
            val top = chartHeight - barHeight

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x - barWidth / 2, 0f),
                size = Size(barWidth, chartHeight),
                cornerRadius = CornerRadius(16f, 16f)
            )

            if (barHeight > 0) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x - barWidth / 2, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(16f, 16f)
                )
            }

            drawContext.canvas.nativeCanvas.drawText(
                pair.first,
                x,
                height - 10f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

@Composable
fun CalorieProgressRing(
    consumed: Double,
    target: Double,
    modifier: Modifier = Modifier
) {
    val progress = (consumed / target).coerceIn(0.0, 1.0).toFloat()
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.background

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18f
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${consumed.toInt()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "/ ${target.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
