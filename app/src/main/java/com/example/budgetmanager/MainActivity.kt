package com.example.budgetmanager

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.budgetmanager.network.ExchangeRateViewModel
import com.example.budgetmanager.ui.theme.AOSFinalProjectBudgetManagerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// SharedPreferences 工具函数
fun saveStringList(context: Context, key: String, list: List<String>) {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    sharedPreferences.edit().putString(key, list.joinToString(",")).apply()
}

fun getStringList(context: Context, key: String, defaultList: List<String>): MutableList<String> {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    val savedString = sharedPreferences.getString(key, null)
    return if (savedString.isNullOrEmpty()) {
        defaultList.toMutableList()
    } else {
        savedString.split(",").toMutableList()
    }
}

// 定義支出記錄資料類
data class ExpenseRecord(val date: Long, val amount: Double, val type: String, val currency: String)

// 將支出紀錄列表存入SharedPreferences
fun saveExpenseRecords(context: Context, records: List<ExpenseRecord>) {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    // 簡單以 | 分隔欄位, \n 分隔多筆記錄
    val dataStr = records.joinToString("\n") {
        "${it.date}|${it.amount}|${it.type}|${it.currency}"
    }
    sharedPreferences.edit().putString("expense_records", dataStr).apply()
}

// 從SharedPreferences讀取支出紀錄
fun loadExpenseRecords(context: Context): MutableList<ExpenseRecord> {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    val dataStr = sharedPreferences.getString("expense_records", "") ?: ""
    if (dataStr.isBlank()) return mutableListOf()
    return dataStr.split("\n").mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size == 4) {
            val date = parts[0].toLongOrNull()
            val amount = parts[1].toDoubleOrNull()
            val type = parts[2]
            val currency = parts[3]
            if (date != null && amount != null) {
                ExpenseRecord(date, amount, type, currency)
            } else null
        } else null
    }.toMutableList()
}

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)

        setContent {
            AOSFinalProjectBudgetManagerTheme {
                val navController = rememberNavController()
                val username = sharedPreferences.getString("username", null)

                if (username.isNullOrEmpty()) {
                    UsernameInputScreen { enteredUsername ->
                        saveUsername(enteredUsername)
                    }
                } else {
                    AppNavigation(navController, username)
                }
            }
        }
    }

    private fun saveUsername(username: String) {
        sharedPreferences.edit().putString("username", username).apply()
        recreate()
    }
}

@Composable
fun AppNavigation(navController: NavHostController, username: String) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, username) }
        composable("income") { IncomeScreen() }
        composable("expense") { ExpenseScreen() }
        composable("set_budget") { SetBudgetScreen() }
        composable("set_saving_goal") { SetSavingGoalScreen() }
        composable("monthly_report") { MonthlyReportScreen() }
    }
}

@Composable
fun UsernameInputScreen(onUsernameEntered: (String) -> Unit) {
    var username by remember { mutableStateOf("") }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("請輸入您的暱稱", fontSize = 24.sp)
            TextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("暱稱") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { if (username.isNotEmpty()) onUsernameEntered(username) }) {
                Text("開始")
            }
        }
    }
}

@Composable
fun HomeScreen(navController: NavController, username: String) {
    val currentDate = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "歡迎 $username 使用 BudgetManager",
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "今天是 $currentDate",
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.index_bg),
                    contentDescription = "按鈕區域背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.5f) // 設定透明度，值在 0.0（完全透明）到 1.0（完全不透明）之間
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { navController.navigate("income") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.income),
                                contentDescription = "新增收入",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "新增收入",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = { navController.navigate("expense") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.expenditure),
                                contentDescription = "新增支出",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "新增支出",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = Color.Gray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))
        }


        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.money_bg),
                    contentDescription = "按鈕區域背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.5f) // 設定透明度，值在 0.0（完全透明）到 1.0（完全不透明）之間
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { navController.navigate("set_budget") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.budget),
                                contentDescription = "設定預算",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "設定預算",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = { navController.navigate("set_saving_goal") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.saving_goal),
                                contentDescription = "儲蓄目標",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "儲蓄目標",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = Color.Gray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))
        }


        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.monthly_report_bg),
                    contentDescription = "按鈕區域背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.5f) // 設定透明度，值在 0.0（完全透明）到 1.0（完全不透明）之間
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { navController.navigate("monthly_report") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.monthly_report),
                                contentDescription = "月度報告",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = "月度報告",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = Color.Gray, thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))
        }




    }
}


// 在檔案中與 ExpenseRecord 類似的位置定義 IncomeRecord
data class IncomeRecord(val date: Long, val amount: Double, val type: String, val currency: String)

// 將收入紀錄列表存入SharedPreferences
fun saveIncomeRecords(context: Context, records: List<IncomeRecord>) {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    // 使用與ExpenseRecord類似的格式儲存
    val dataStr = records.joinToString("\n") {
        "${it.date}|${it.amount}|${it.type}|${it.currency}"
    }
    sharedPreferences.edit().putString("income_records", dataStr).apply()
}

// 從SharedPreferences讀取收入紀錄
fun loadIncomeRecords(context: Context): MutableList<IncomeRecord> {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    val dataStr = sharedPreferences.getString("income_records", "") ?: ""
    if (dataStr.isBlank()) return mutableListOf()
    return dataStr.split("\n").mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size == 4) {
            val date = parts[0].toLongOrNull()
            val amount = parts[1].toDoubleOrNull()
            val type = parts[2]
            val currency = parts[3]
            if (date != null && amount != null) {
                IncomeRecord(date, amount, type, currency)
            } else null
        } else null
    }.toMutableList()
}


@Composable
fun IncomeScreen() {
    val context = LocalContext.current
    val viewModel: ExchangeRateViewModel = viewModel<ExchangeRateViewModel>() // 使用 ViewModel 1229add

    // 從 SharedPreferences 讀取保存的收入類型列表與貨幣種類
    val incomeTypes = remember { getStringList(context, "income_types", listOf("薪水", "投資", "獎金", "自訂")) }
    remember { getStringList(context, "expense_types", listOf("飲食", "交通", "娛樂", "自訂")) }
    val currencyTypes = remember { getStringList(context, "currency_types", listOf("TWD", "USD", "EUR", "自訂")) }
    remember { getStringList(context, "currency_types", listOf("TWD", "USD", "EUR", "自訂")) }
    // 已存在的收入記錄，從SharedPreferences載入
    var incomeRecords by remember { mutableStateOf(loadIncomeRecords(context)) }

    // 记录用户输入的数据
    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedIncomeType by remember { mutableStateOf(incomeTypes.first()) }
    var selectedCurrency by remember { mutableStateOf(currencyTypes.first()) }
    var selectedDisplayCurrency by remember { mutableStateOf(currencyTypes.first()) } // 新增顯示貨幣選擇 //1229
    var showDatePicker by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var customIncomeTypeText by remember { mutableStateOf("") }
    var customCurrencyText by remember { mutableStateOf("") }

    // 日期格式
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val formattedDate = dateFormat.format(selectedDate)

    //1229 begin
    // 匯率轉換函數
    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val fromRate = viewModel.currencyRates.value[fromCurrency]?.Exrate
        val toRate = viewModel.currencyRates.value[toCurrency]?.Exrate

        if (fromRate == null || toRate == null) {
            Log.d("CurrencyConversion", "匯率資料缺失: from $fromCurrency -> $toCurrency")
            return amount // 回傳原金額
        }

        // 計算最終的轉換金額
        val result = amount * (toRate / fromRate)

        Log.d("CurrencyConversion", "從 $fromCurrency 到 $toCurrency: $amount -> $result (匯率: $fromRate -> $toRate)")
        return result
    }


    // 當前月份與年份

    //1229 end
    val currentMonth: Int
    val currentYear: Int
    run {
        val cal = Calendar.getInstance()
        cal.time = selectedDate
        currentMonth = cal.get(Calendar.MONTH)
        currentYear = cal.get(Calendar.YEAR)
    }

    // 依日期篩選當月收入
    val monthlyRecords = incomeRecords.filter { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
    }



    // 當月總收入（統一轉成台幣）
    val monthlyTotal = monthlyRecords.sumOf { record ->
        val amount = record.amount
        val currency = record.currency
        convertCurrency(amount, currency, "TWD") // 將金額轉換為台幣
    }


    // 載入當月儲蓄目標
    val savingGoal = loadMonthlySavingGoal(context, currentYear, currentMonth) ?: 0.0

    // 計算剩餘儲蓄目標
    val remainingSavingGoal = savingGoal - monthlyTotal

    val displaySavingGoal = convertCurrency(savingGoal, "TWD", selectedDisplayCurrency)
    val displayRemainingSavingGoal = convertCurrency(remainingSavingGoal, "TWD", selectedDisplayCurrency)
    val displayMonthlyTotal = convertCurrency(monthlyTotal, "TWD", selectedDisplayCurrency)

    // 日期選擇器Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, monthOfYear, dayOfMonth)
                selectedDate = cal.time
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    //1229 begin
    // 觸發匯率請求
    LaunchedEffect(Unit) {
        viewModel.fetchExchangeRates()// 請求匯率
        if (viewModel.currencyRates.value.isNotEmpty()) {
            Log.d("CurrencyRates", "匯率載入成功: ${viewModel.currencyRates.value}")
        } else {
            Log.d("CurrencyRates", "匯率載入失敗")
        }
    }

    // 顯示錯誤訊息
    if (viewModel.errorMessage.value.isNotBlank()) {
        Toast.makeText(context, viewModel.errorMessage.value, Toast.LENGTH_SHORT).show()
    }
    //1229 end
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "新增收入", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        item {
            // 日期选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "日期：", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showDatePicker = true }) {
                    Text(text = formattedDate)
                }
            }
        }

        item {
            // 金額輸入
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "金額：", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = amountText,
                    onValueChange = { input ->
                        // 僅允許數字與小數點輸入
                        if (input.matches(Regex("^\\d*\\.?\\d*\$"))) {
                            amountText = input
                        }
                    },
                    placeholder = { Text("請輸入金額") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            // 收入類型選擇
            Text(text = "收入類型：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = incomeTypes,
                selectedOption = selectedIncomeType,
                onOptionSelected = { selectedIncomeType = it },
                customText = customIncomeTypeText,
                onCustomTextChange = { customIncomeTypeText = it },
                onConfirmCustom = {
                    if (customIncomeTypeText.isNotBlank()) {
                        incomeTypes.add(customIncomeTypeText)
                        selectedIncomeType = customIncomeTypeText
                        customIncomeTypeText = ""
                        saveStringList(context, "income_types", incomeTypes)
                    }
                }
            )
        }

        item {
            // 貨幣種類選擇
            Text(text = "貨幣種類：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = currencyTypes,
                selectedOption = selectedCurrency,
                onOptionSelected = { selectedCurrency = it },
                customText = customCurrencyText,
                onCustomTextChange = { customCurrencyText = it },
                onConfirmCustom = {
                    if (customCurrencyText.isNotBlank()) {
                        currencyTypes.add(customCurrencyText)
                        selectedCurrency = customCurrencyText
                        customCurrencyText = ""
                        saveStringList(context, "currency_types", currencyTypes)
                    }
                }
            )
        }

        //1229 begin
        item {
            // 顯示貨幣選擇
            Text(text = "顯示貨幣：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = currencyTypes,
                selectedOption = selectedDisplayCurrency,
                onOptionSelected = { selectedDisplayCurrency = it },
                customText = customCurrencyText,
                onCustomTextChange = { customCurrencyText = it },
                onConfirmCustom = {
                    if (customCurrencyText.isNotBlank()) {
                        // 將自訂貨幣加入選項列表
                        currencyTypes.add(customCurrencyText)
                        // 更新選擇為自訂貨幣
                        selectedDisplayCurrency = customCurrencyText
                        // 清空輸入框
                        customCurrencyText = ""
                        // 保存更新到 SharedPreferences
                        saveStringList(context, "currency_types", currencyTypes)
                    }
                }

            )
            Text(text = "此貨幣將用來顯示所有金額", fontSize = 12.sp, color = Color.Gray)
        }
        //1229 end


        item {
            // 確定按鈕
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && selectedIncomeType.isNotBlank() && selectedCurrency.isNotBlank()) {
                        // 新增記錄
                        val newRecord = IncomeRecord(
                            date = selectedDate.time,
                            amount = amount,
                            type = selectedIncomeType,
                            currency = selectedCurrency
                        )
                        incomeRecords = (incomeRecords + newRecord).toMutableList()
                        saveIncomeRecords(context, incomeRecords)

                        // 清空輸入
                        amountText = ""
                    }
                },
                enabled = amountText.isNotBlank() && amountText.toDoubleOrNull() != null
            ) {
                Text("確定")
            }
        }

        item {
            Button(
                onClick = {
                    // 清空收入紀錄
                    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
                    sharedPreferences.edit().remove("income_records").apply()

                    // 更新UI
                    incomeRecords = mutableListOf()
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("清空所有收入紀錄")
            }
        }

        if (savingGoal > 0) {
            item {
                // 顯示儲蓄目標
                Text(
                    text = "當月儲蓄目標總金額：${String.format("%.2f", displaySavingGoal)} ${selectedDisplayCurrency}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "當月儲蓄目標剩餘金額：${String.format("%.2f", displayRemainingSavingGoal.coerceAtLeast(0.0))} ${selectedDisplayCurrency}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Text(
                text = "當月收入總金額：${String.format("%.2f", displayMonthlyTotal)} ${selectedDisplayCurrency}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(text = "當月收入記錄：", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (monthlyRecords.isEmpty()) {
            item {
                Text(text = "本月尚無收入記錄", fontSize = 16.sp)
            }
        } else {
            item {
                // 標題列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("編號", fontWeight = FontWeight.Bold)
                    Text("日期", fontWeight = FontWeight.Bold)
                    Text("類型", fontWeight = FontWeight.Bold)
                    Text("貨幣", fontWeight = FontWeight.Bold)
                    Text("金額", fontWeight = FontWeight.Bold)
                }
            }

            items(monthlyRecords.size) { index ->
                val record = monthlyRecords[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((index + 1).toString())
                    Text(dateFormat.format(Date(record.date)))
                    Text(record.type)
                    Text(record.currency) // 顯示貨幣種類
                    Text(record.amount.toString()) // 顯示原金額
                }
            }
        }
    }
}









@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen() {
    val context = LocalContext.current
    val viewModel: ExchangeRateViewModel = viewModel<ExchangeRateViewModel>() // 使用 ViewModel 1229add

    // 從 SharedPreferences 讀取保存的支出類型列表與貨幣種類
    val expenseTypes =
        remember { getStringList(context, "expense_types", listOf("飲食", "交通", "娛樂", "自訂")) }
    val currencyTypes =
        remember { getStringList(context, "currency_types", listOf("TWD", "USD", "EUR", "自訂")) }

    // 已存在的支出記錄，從SharedPreferences載入
    var expenseRecords by remember { mutableStateOf(loadExpenseRecords(context)) }

    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedExpenseType by remember { mutableStateOf(expenseTypes.first()) }
    var selectedCurrency by remember { mutableStateOf(currencyTypes.first()) }
    var selectedDisplayCurrency by remember { mutableStateOf(currencyTypes.first()) } // 新增顯示貨幣選擇 //1229
    var showDatePicker by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var customExpenseTypeText by remember { mutableStateOf("") }
    var customCurrencyText by remember { mutableStateOf("") }

    // 日期格式
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val formattedDate = dateFormat.format(selectedDate)

    //1229 begin
    // 匯率轉換函數
    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val fromRate = viewModel.currencyRates.value[fromCurrency]?.Exrate
        val toRate = viewModel.currencyRates.value[toCurrency]?.Exrate

        if (fromRate == null || toRate == null) {
            Log.d("CurrencyConversion", "匯率資料缺失: from $fromCurrency -> $toCurrency")
            return amount // 回傳原金額
        }

        // 計算最終的轉換金額
        val result = amount * (toRate / fromRate)

        Log.d("CurrencyConversion", "從 $fromCurrency 到 $toCurrency: $amount -> $result (匯率: $fromRate -> $toRate)")
        return result
    }


    // 當前月份與年份

    //1229 end
    val currentMonth: Int
    val currentYear: Int
    run {
        val cal = Calendar.getInstance()
        cal.time = selectedDate
        currentMonth = cal.get(Calendar.MONTH)
        currentYear = cal.get(Calendar.YEAR)
    }

    // 當月支出記錄（依日期篩選）
    val monthlyRecords = expenseRecords.filter { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
    }

// 當月總支出（統一轉成台幣）
    val monthlyTotal = monthlyRecords.sumOf { record ->
        val amount = record.amount
        val currency = record.currency
        convertCurrency(amount, currency, "TWD") // 將金額轉換為台幣
    }

// 載入當月預算資訊
    val (totalBudget, categoryBudgets) = loadMonthlyBudget(context, currentYear, currentMonth)

// 計算剩餘總預算（台幣）
    var remainingTotalBudget = totalBudget - monthlyTotal

// 計算每個類別的剩餘預算（台幣）
    val categoryRemainingBudgets = categoryBudgets.mapValues { (category, budget) ->
        val categoryTotalSpent = monthlyRecords
            .filter { it.type == category }
            .sumOf { record ->
                val amount = record.amount
                val currency = record.currency
                convertCurrency(amount, currency, "TWD") // 轉換為台幣
            }
        budget - categoryTotalSpent
    }

// 將總預算、剩餘總預算和類別預算轉換為顯示貨幣
    val displayTotalBudget = convertCurrency(totalBudget, "TWD", selectedDisplayCurrency)
    val displayRemainingTotalBudget = convertCurrency(remainingTotalBudget, "TWD", selectedDisplayCurrency)
    val displayCategoryBudgets = categoryBudgets.mapValues { (category, budget) ->
        convertCurrency(budget, "TWD", selectedDisplayCurrency)
    }
    val displayCategoryRemainingBudgets = categoryRemainingBudgets.mapValues { (category, remainingBudget) ->
        convertCurrency(remainingBudget, "TWD", selectedDisplayCurrency)
    }
    val displayMonthlyTotal = convertCurrency(monthlyTotal, "TWD", selectedDisplayCurrency)

    // 日期選擇器Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, monthOfYear, dayOfMonth)
                selectedDate = cal.time
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    //1229 begin
    // 觸發匯率請求
    LaunchedEffect(Unit) {
        viewModel.fetchExchangeRates()// 請求匯率
        if (viewModel.currencyRates.value.isNotEmpty()) {
            Log.d("CurrencyRates", "匯率載入成功: ${viewModel.currencyRates.value}")
        } else {
            Log.d("CurrencyRates", "匯率載入失敗")
        }
    }

    // 顯示錯誤訊息
    if (viewModel.errorMessage.value.isNotBlank()) {
        Toast.makeText(context, viewModel.errorMessage.value, Toast.LENGTH_SHORT).show()
    }
    //1229 end
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "新增支出", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        item {
            // 日期选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "日期：", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showDatePicker = true }) {
                    Text(text = formattedDate)
                }
            }
        }

        item {
            // 金額輸入
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "金額：", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*\\.?\\d*\$"))) amountText = input
                    },
                    placeholder = { Text("請輸入金額") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            // 消費類型選擇
            Text(text = "消費類型：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = expenseTypes,
                selectedOption = selectedExpenseType,
                onOptionSelected = { selectedExpenseType = it },
                customText = customExpenseTypeText,
                onCustomTextChange = { customExpenseTypeText = it },
                onConfirmCustom = {
                    if (customExpenseTypeText.isNotBlank()) {
                        expenseTypes.add(customExpenseTypeText)
                        selectedExpenseType = customExpenseTypeText
                        customExpenseTypeText = ""
                        saveStringList(context, "expense_types", expenseTypes)
                    }
                }
            )
        }

        item {
            // 貨幣種類選擇
            Text(text = "貨幣種類：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = currencyTypes,
                selectedOption = selectedCurrency,
                onOptionSelected = { selectedCurrency = it },
                customText = customCurrencyText,
                onCustomTextChange = { customCurrencyText = it },
                onConfirmCustom = {
                    if (customCurrencyText.isNotBlank()) {
                        currencyTypes.add(customCurrencyText)
                        selectedCurrency = customCurrencyText
                        customCurrencyText = ""
                        saveStringList(context, "currency_types", currencyTypes)
                    }
                }
            )
        }

        //1229 begin
        item {
            // 顯示貨幣選擇
            Text(text = "顯示貨幣：", fontSize = 16.sp)
            DropdownWithCustomOption(
                options = currencyTypes,
                selectedOption = selectedDisplayCurrency,
                onOptionSelected = { selectedDisplayCurrency = it },
                customText = customCurrencyText,
                onCustomTextChange = { customCurrencyText = it },
                onConfirmCustom = {
                    if (customCurrencyText.isNotBlank()) {
                        // 將自訂貨幣加入選項列表
                        currencyTypes.add(customCurrencyText)
                        // 更新選擇為自訂貨幣
                        selectedDisplayCurrency = customCurrencyText
                        // 清空輸入框
                        customCurrencyText = ""
                        // 保存更新到 SharedPreferences
                        saveStringList(context, "currency_types", currencyTypes)
                    }
                }

            )
            Text(text = "此貨幣將用來顯示所有金額", fontSize = 12.sp, color = Color.Gray)
        }
        //1229 end


        item {
            // 確定按鈕
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && selectedExpenseType.isNotBlank() && selectedCurrency.isNotBlank()) {
                        val newRecord = ExpenseRecord(
                            date = selectedDate.time,
                            amount = amount,
                            type = selectedExpenseType,
                            currency = selectedCurrency
                        )
                        expenseRecords = (expenseRecords + newRecord).toMutableList()
                        saveExpenseRecords(context, expenseRecords)

                        amountText = ""
                    }
                },
                enabled = amountText.isNotBlank() && amountText.toDoubleOrNull() != null
            ) {
                Text("確定")
            }
        }

        item {
            Button(
                onClick = {
                    // 清空支出紀錄
                    val sharedPreferences =
                        context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
                    sharedPreferences.edit().remove("expense_records").apply()

                    // 更新UI
                    expenseRecords = mutableListOf()
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("清空所有支出紀錄")
            }
        }

        if (totalBudget > 0) {
            item {
                // 顯示總預算與剩餘預算，轉換為所選顯示貨幣
                val displayTotalBudget = convertCurrency(totalBudget, "TWD", selectedDisplayCurrency)
                val displayRemainingTotalBudget = convertCurrency(remainingTotalBudget, "TWD", selectedDisplayCurrency)

                Text(
                    text = "本月總預算：${String.format("%.2f", displayTotalBudget)} ${selectedDisplayCurrency}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "本月剩餘總預算：${String.format("%.2f", displayRemainingTotalBudget)} ${selectedDisplayCurrency}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // 顯示各分類預算剩餘，轉換為所選顯示貨幣
                categoryRemainingBudgets.forEach { (category, remaining) ->
                    val displayRemaining = convertCurrency(remaining, "TWD", selectedDisplayCurrency)
                    Text(
                        text = "${category} 預算剩餘：${String.format("%.2f", displayRemaining)} ${selectedDisplayCurrency}",
                        fontSize = 16.sp
                    )
                }
            }
        }
        item {
            Text(
                text = "當月支出總金額：${String.format("%.2f", displayMonthlyTotal)} ${selectedDisplayCurrency}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                text = "當月支出記錄：",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }


        if (monthlyRecords.isEmpty()) {
            item {
                Text(text = "本月尚無支出記錄", fontSize = 16.sp)
            }
        } else {
            item {
                // 標題行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("編號", fontWeight = FontWeight.Bold)
                    Text("日期", fontWeight = FontWeight.Bold)
                    Text("類型", fontWeight = FontWeight.Bold)
                    Text("貨幣", fontWeight = FontWeight.Bold)
                    Text("金額", fontWeight = FontWeight.Bold)
                }
            }

            items(monthlyRecords.size) { index ->
                val record = monthlyRecords[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((index + 1).toString())
                    Text(dateFormat.format(Date(record.date)))
                    Text(record.type)
                    Text(record.currency) // 顯示貨幣種類
                    Text(record.amount.toString()) // 顯示原金額
                }
            }
        }
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownWithCustomOption(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onConfirmCustom: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedOption,
                onValueChange = { },
                readOnly = true,
                label = { Text("請選擇一項") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        text = { Text(text = option) }
                    )
                }
            }
        }

        // 若當前選擇是「自訂」則顯示輸入框
        if (selectedOption == "自訂") {
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = customText,
                onValueChange = onCustomTextChange,
                placeholder = { Text("請輸入自訂項目") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onConfirmCustom() }) {
                Text("加入自訂項目")
            }
        }
    }
}

@Composable
fun SetBudgetScreen() {
    val context = LocalContext.current

    var yearText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var totalBudgetText by remember { mutableStateOf("") }

    // 預設有一個「無」與「自訂」兩種類別
    val defaultBudgetCategories = remember { mutableStateListOf("無", "自訂") }
    // 選擇的類別
    var selectedCategory by remember { mutableStateOf(defaultBudgetCategories.first()) }
    // 如果選擇自訂，使用者可以輸入新的類別名稱
    var customCategoryName by remember { mutableStateOf("") }
    // 輸入類別預算金額
    var categoryBudgetAmountText by remember { mutableStateOf("") }

    // 類別預算列表(暫存於記憶體，儲存時一併存入)
    val categoryBudgets = remember { mutableStateMapOf<String, Double>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("設定預算", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // 年份輸入
        Text("年份：", fontSize = 16.sp)
        TextField(
            value = yearText,
            onValueChange = { input -> if (input.matches(Regex("\\d*"))) yearText = input },
            placeholder = { Text("請輸入年份(如2024)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))
        // 月份輸入
        Text("月份：", fontSize = 16.sp)
        TextField(
            value = monthText,
            onValueChange = { input ->
                if (input.matches(Regex("\\d*"))) monthText = input
            },
            placeholder = { Text("請輸入月份(1-12)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))
        // 總預算輸入
        Text("當月總預算：", fontSize = 16.sp)
        TextField(
            value = totalBudgetText,
            onValueChange = { input ->
                if (input.matches(Regex("^\\d*\\.?\\d*\$"))) totalBudgetText = input
            },
            placeholder = { Text("請輸入當月總預算") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 類別預算設定區
        Text("設定類別預算：", fontSize = 16.sp)
        DropdownWithCustomOption(
            options = defaultBudgetCategories,
            selectedOption = selectedCategory,
            onOptionSelected = { selectedCategory = it },
            customText = customCategoryName,
            onCustomTextChange = { customCategoryName = it },
            onConfirmCustom = {
                if (customCategoryName.isNotBlank()) {
                    defaultBudgetCategories.add(customCategoryName)
                    selectedCategory = customCategoryName
                    customCategoryName = ""
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = categoryBudgetAmountText,
            onValueChange = { input ->
                if (input.matches(Regex("^\\d*\\.?\\d*\$"))) categoryBudgetAmountText = input
            },
            placeholder = { Text("請輸入該類別預算金額") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val amt = categoryBudgetAmountText.toDoubleOrNull()
            if (amt != null && selectedCategory.isNotBlank()) {
                categoryBudgets[selectedCategory] = amt
                categoryBudgetAmountText = ""
            }
        }, enabled = categoryBudgetAmountText.isNotBlank() && categoryBudgetAmountText.toDoubleOrNull() != null) {
            Text("新增/更新類別預算")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 顯示目前已設定的類別預算列表
        if (categoryBudgets.isNotEmpty()) {
            Text("已設定的類別預算：", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categoryBudgets.forEach { (cat, amt) ->
                    Text("$cat：$amt 元")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 儲存按鈕
        Button(onClick = {
            val y = yearText.toIntOrNull()
            val m = monthText.toIntOrNull()
            val total = totalBudgetText.toDoubleOrNull()
            if (y != null && m != null && total != null) {
                saveMonthlyBudget(context, y, m - 1, total, categoryBudgets.toMap())
                // 注意：Calendar.MONTH 為0起算，如果使用者輸入1代表1月，實際存取時需用m-1
            }
        }, enabled = yearText.isNotBlank() && monthText.isNotBlank() && totalBudgetText.toDoubleOrNull() != null) {
            Text("儲存當月預算")
        }
    }
}


@Composable
fun SetSavingGoalScreen() {
    val context = LocalContext.current

    var yearText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }
    var savingGoalText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("設定儲蓄目標", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Text("年份：", fontSize = 16.sp)
        TextField(
            value = yearText,
            onValueChange = { input -> if (input.matches(Regex("\\d*"))) yearText = input },
            placeholder = { Text("請輸入年份(如2024)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text("月份：", fontSize = 16.sp)
        TextField(
            value = monthText,
            onValueChange = { input ->
                if (input.matches(Regex("\\d*"))) monthText = input
            },
            placeholder = { Text("請輸入月份(1-12)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Text("儲蓄目標金額：", fontSize = 16.sp)
        TextField(
            value = savingGoalText,
            onValueChange = { input ->
                if (input.matches(Regex("^\\d*\\.?\\d*\$"))) savingGoalText = input
            },
            placeholder = { Text("請輸入目標金額") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = {
                val y = yearText.toIntOrNull()
                val m = monthText.toIntOrNull()
                val goal = savingGoalText.toDoubleOrNull()
                if (y != null && m != null && goal != null) {
                    saveMonthlySavingGoal(context, y, m - 1, goal)
                }
            },
            enabled = yearText.isNotBlank() && monthText.isNotBlank() && savingGoalText.toDoubleOrNull() != null
        ) {
            Text("儲存儲蓄目標")
        }
    }
}


@Composable
fun MonthlyReportScreen() {
    // 用於解構賦值的資料類別
    data class RecordData(val date: Long, val amount: Double, val type: String, val currency: String)

    val context = LocalContext.current

    var yearText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf("") }

    // 取得使用者輸入的年份與月份後再進行報表計算
    // 假設使用者輸入完成後按下查詢鈕才顯示圖表
    var showReport by remember { mutableStateOf(false) }

    // 當月收入與支出紀錄計算用
    var dailyDiffData by remember { mutableStateOf(listOf<Double>()) }
    var categoryPercentData by remember { mutableStateOf(emptyMap<String, Double>()) }

    // 用於顯示本月所有收支記錄的清單
    var allMonthlyRecords by remember { mutableStateOf<List<Any>>(emptyList()) }

    // 當前查詢月份的天數
    var daysInMonth by remember { mutableStateOf(31) } // 預設31天

    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    var analysisResult by remember { mutableStateOf("") } // 儲存分析結果
    var isAnalyzing by remember { mutableStateOf(false) }
    val apiKey = BuildConfig.OPENAI_API_KEY // 從 BuildConfig 取得 API 金鑰

    Column(modifier = Modifier.padding(16.dp)) {
        Text("月度收支報告", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text("年份：")
        TextField(
            value = yearText,
            onValueChange = { if (it.matches(Regex("\\d*"))) yearText = it },
            placeholder = { Text("例如 2024") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("月份：")
        TextField(
            value = monthText,
            onValueChange = { if (it.matches(Regex("\\d*"))) monthText = it },
            placeholder = { Text("1-12") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))


        Spacer(modifier = Modifier.height(24.dp))

        // 修改此處：查詢與匯出按鈕放在同一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    val y = yearText.toIntOrNull()
                    val m = monthText.toIntOrNull()
                    if (y != null && m != null && m in 1..12) {

                        val (diffData, categoryData) = calculateMonthlyReportData(context, y, m - 1)
                        dailyDiffData = diffData
                        categoryPercentData = categoryData
                        showReport = true


                        val calendar = Calendar.getInstance()
                        calendar.set(y, m - 1, 1)
                        daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)


                        val expenseRecordsAll = loadExpenseRecords(context)
                        val incomeRecordsAll = loadIncomeRecords(context)

                        val monthlyExpenseRecords = expenseRecordsAll.filter { r ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = r.date
                            cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == (m - 1)
                        }

                        val monthlyIncomeRecords = incomeRecordsAll.filter { r ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = r.date
                            cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == (m - 1)
                        }


                        allMonthlyRecords = (monthlyExpenseRecords + monthlyIncomeRecords).sortedBy { record ->
                            when (record) {
                                is ExpenseRecord -> record.date
                                is IncomeRecord -> record.date
                                else -> Long.MAX_VALUE
                            }
                        }
                    }

                },
                enabled = yearText.isNotBlank() && monthText.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("查詢")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (allMonthlyRecords.isNotEmpty()) {
                        exportMonthlyReportToCSV(
                            context = context,
                            allMonthlyRecords = allMonthlyRecords
                        )
                    }
                },
                enabled = allMonthlyRecords.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("匯出")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    // 開始分析
                    val y = yearText.toIntOrNull()
                    val m = monthText.toIntOrNull()

                    if (y != null && m != null && m in 1..12) {
                        isAnalyzing = true
                        val (monthlyIncomeList, categoryData) = getMonthlyExpenseData(context, y, m - 1)
                        val monthlyIncome = monthlyIncomeList
                        val categoryExpenses = categoryData
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val totalIncome = monthlyIncome.filter { it > 0 }.sum()
                                //val totalExpenses = categoryPercentData.mapValues { it.value / 100 * totalIncome }

                                val result = OpenAIAnalysis.analyzeSpending(
                                    apiKey = apiKey,
                                    income = totalIncome,
                                    expenses = categoryExpenses
                                )

                                analysisResult = result
                            } catch (e: Exception) {
                                analysisResult = "分析失敗：${e.message}"
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    }
                },
                enabled = !isAnalyzing && yearText.isNotBlank() && monthText.isNotBlank() && allMonthlyRecords.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isAnalyzing) "分析中... " else "分析收支")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (analysisResult.isNotBlank()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())){
                Text(
                    text = "分析結果：",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = analysisResult,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showReport) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text("折線圖 (每日收入 - 支出)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))


                    LineChart(
                        records = dailyDiffData.mapIndexed { index, value -> index + 1 to value },
                        month = "$monthText 月",
                        daysInMonth = daysInMonth
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Text("消費類型比例圓餅圖", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    PieChart(categoryPercentData)

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "本月所有收支記錄：", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (allMonthlyRecords.isEmpty()) {
                    item {
                        Text("本月尚無收支記錄", fontSize = 16.sp)
                    }
                } else {
                    item {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("編號", fontWeight = FontWeight.Bold)
                            Text("日期", fontWeight = FontWeight.Bold)
                            Text("類型", fontWeight = FontWeight.Bold)
                            Text("貨幣", fontWeight = FontWeight.Bold)
                            Text("金額", fontWeight = FontWeight.Bold)
                        }
                    }

                    items(allMonthlyRecords.size) { index ->
                        val record = allMonthlyRecords[index]

                        val recordData = when (record) {
                            is ExpenseRecord -> RecordData(record.date, record.amount, record.type, record.currency)
                            is IncomeRecord -> RecordData(record.date, record.amount, record.type, record.currency)
                            else -> RecordData(0L, 0.0, "", "")
                        }

                        val (date, amount, type, currency) = recordData

                        val backgroundColor = when (record) {
                            is IncomeRecord -> Color(0xFFFFCDD2)
                            is ExpenseRecord -> Color(0xFFBBDEFB)
                            else -> Color.White
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text((index + 1).toString())
                            Text(dateFormat.format(Date(date)))
                            Text(type)
                            Text(currency)
                            Text("%.2f".format(amount))
                        }
                    }
                }
            }
        }
    }
}

fun getMonthlyExpenseData(context: Context, year: Int, month: Int): Pair<List<Double>, Map<String, Double>> {
    val expenseRecords = loadExpenseRecords(context)
    val incomeRecords = loadIncomeRecords(context)

    // 該月份的所有天數(簡化假設以31天為上限, 實際可依月份確定天數)
    val calendar = Calendar.getInstance()
    calendar.set(year, month, 1)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    // 計算每日總收入與總支出
    val dailyIncome = DoubleArray(daysInMonth) { 0.0 }
    val dailyExpense = DoubleArray(daysInMonth) { 0.0 }

    expenseRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        val day = cal.get(Calendar.DAY_OF_MONTH) - 1
        dailyExpense[day] += record.amount
    }

    incomeRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        val day = cal.get(Calendar.DAY_OF_MONTH) - 1
        dailyIncome[day] += record.amount
    }

    val dailyIncomeList = (0 until daysInMonth).map { dailyIncome[it] }

    // 計算各類別比例：從支出紀錄中統計
    val monthlyExpenseRecords = expenseRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }

    val totalExpense = monthlyExpenseRecords.sumOf { it.amount }
    val categoryMap = monthlyExpenseRecords.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.amount } }

    return dailyIncomeList to categoryMap
}

// 計算當月每日的(收入-支出)與各消費類型比例
fun calculateMonthlyReportData(context: Context, year: Int, month: Int): Pair<List<Double>, Map<String, Double>> {
    // 載入支出與收入紀錄
    val expenseRecords = loadExpenseRecords(context)
    val incomeRecords = loadIncomeRecords(context)

    // 該月份的所有天數(簡化假設以31天為上限, 實際可依月份確定天數)
    val calendar = Calendar.getInstance()
    calendar.set(year, month, 1)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    // 計算每日總收入與總支出
    val dailyIncome = DoubleArray(daysInMonth) { 0.0 }
    val dailyExpense = DoubleArray(daysInMonth) { 0.0 }

    expenseRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        val day = cal.get(Calendar.DAY_OF_MONTH) - 1
        dailyExpense[day] += record.amount
    }

    incomeRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }.forEach { record ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = record.date
        val day = cal.get(Calendar.DAY_OF_MONTH) - 1
        dailyIncome[day] += record.amount
    }

    // 計算 dailyDiff = 收入 - 支出
    val dailyDiff = (0 until daysInMonth).map { dailyIncome[it] - dailyExpense[it] }

    // 計算各類別比例：從支出紀錄中統計
    val monthlyExpenseRecords = expenseRecords.filter {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.date
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }

    val totalExpense = monthlyExpenseRecords.sumOf { it.amount }
    val categoryMap = monthlyExpenseRecords.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.amount } }

    val categoryPercent = if (totalExpense > 0) {
        categoryMap.mapValues { (it.value / totalExpense) * 100.0 }
    } else {
        emptyMap()
    }

    return dailyDiff to categoryPercent
}

@Composable
fun LineChart(records: List<Pair<Int, Double>>, month: String, daysInMonth: Int) {
    val width = 300.dp
    val height = 200.dp

    // 計算每日累積金額
    val dailyTotals = mutableListOf<Double>()
    var cumulativeTotal = 0.0
    for (day in 1..daysInMonth) {
        val dailyRecord = records.find { it.first == day }?.second ?: 0.0
        cumulativeTotal += dailyRecord
        dailyTotals.add(cumulativeTotal)
    }

    // 計算數據的最大、最小值和範圍
    val maxVal = dailyTotals.maxOrNull() ?: 0.0
    val minVal = dailyTotals.minOrNull() ?: 0.0
    val range = (maxVal - minVal).takeIf { it != 0.0 } ?: 1.0
    val yStep = 5000.0 // 每 5000 元一個刻度

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Y 軸標題
        Text(
            text = "",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .size(width, height)
                .border(1.dp, Color.Gray) // 外邊框
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartWidth = size.width
                val chartHeight = size.height
                val zeroY = chartHeight - ((0 - minVal) / range * chartHeight)

                // 繪製 Y 軸刻度與標籤
                val ySteps = ((maxVal - minVal) / yStep).toInt() + 1
                for (i in 0..ySteps) {
                    val yValue = minVal + i * yStep
                    val y = chartHeight - (yValue - minVal) / range * chartHeight
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(0f, y.toFloat()),
                        end = Offset(chartWidth, y.toFloat()),
                        strokeWidth = 4f
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "%.0f".format(yValue),
                        10f,
                        y.toFloat() - 10f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 30f
                        }
                    )
                }

                // 繪製 X 軸的刻度與標籤
                val stepX = chartWidth / (daysInMonth - 1)
                for (day in 1..daysInMonth step 5) {
                    val x = (day - 1) * stepX
                    drawContext.canvas.nativeCanvas.drawText(
                        "$day",
                        x.toFloat(),
                        chartHeight + 20f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 30f
                        }
                    )
                }

                // 繪製中心水平線 (金額為 0)
                drawLine(
                    color = Color.Red,
                    start = Offset(0f, zeroY.toFloat()),
                    end = Offset(chartWidth, zeroY.toFloat()),
                    strokeWidth = 10f
                )

                // 繪製折線
                val points = dailyTotals.mapIndexed { index, value ->
                    val x = index * stepX
                    val y = chartHeight - ((value - minVal) / range * chartHeight)
                    Offset(x.toFloat(), y.toFloat())
                }

                points.windowed(2).forEach { (p1, p2) ->
                    drawLine(color = Color.Blue, start = p1, end = p2, strokeWidth = 8f)
                }

                // 繪製資料點
                points.forEach { point ->
                    drawCircle(color = Color.Yellow, radius = 5f, center = point)
                }
            }
        }

        // X 軸標題：月份和天數顯示在圖表正下方
        Text(
            text = "$month ($daysInMonth 天)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}









@Composable
fun PieChart(categoryPercent: Map<String, Double>) {
    if (categoryPercent.isEmpty()) {
        Text("本月無支出資料，無法顯示圓餅圖")
        return
    }

    val totalPercent = categoryPercent.values.sum()
    val colors = listOf(Color.Blue, Color.Red, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan)
    val entries = categoryPercent.entries.toList()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 圓餅圖部分
        Canvas(modifier = Modifier.size(200.dp)) {
            val sizeMin = min(size.width, size.height)
            val radius = sizeMin / 2
            var startAngle = -90f

            entries.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / totalPercent * 360).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
                startAngle += sweepAngle
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 圖例部分
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            entries.forEachIndexed { index, (cat, percent) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 顏色標記方塊
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(colors[index % colors.size], shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 顯示類別名稱與百分比
                    Text(
                        text = "$cat：${"%.2f".format(percent)}%",
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 顯示合計
            Text(
                text = "合計：100%",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}




@Composable
fun CenteredTextScreen(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = text, fontSize = 24.sp, textAlign = TextAlign.Center)
    }
}


// 儲存當月預算(總預算 + 類別預算)
fun saveMonthlyBudget(context: Context, year: Int, month: Int, totalBudget: Double, categoryBudgets: Map<String, Double>) {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    // 總預算
    sharedPreferences.edit().putString("monthly_budget_${year}_$month", totalBudget.toString()).apply()

    // 類別預算 (category|amount 每行一筆)
    val catsData = categoryBudgets.entries.joinToString("\n") { "${it.key}|${it.value}" }
    sharedPreferences.edit().putString("monthly_budget_cats_${year}_$month", catsData).apply()
}

// 載入當月預算
fun loadMonthlyBudget(context: Context, year: Int, month: Int): Pair<Double, Map<String, Double>> {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    val totalStr = sharedPreferences.getString("monthly_budget_${year}_$month", null)
    val totalBudget = totalStr?.toDoubleOrNull() ?: 0.0

    val catsData = sharedPreferences.getString("monthly_budget_cats_${year}_$month", "") ?: ""
    val categoryBudgets = mutableMapOf<String, Double>()
    if (catsData.isNotBlank()) {
        catsData.split("\n").forEach { line ->
            val parts = line.split("|")
            if (parts.size == 2) {
                val cat = parts[0]
                val amt = parts[1].toDoubleOrNull()
                if (amt != null) {
                    categoryBudgets[cat] = amt
                }
            }
        }
    }

    return totalBudget to categoryBudgets
}

// 儲存當月儲蓄目標
fun saveMonthlySavingGoal(context: Context, year: Int, month: Int, goal: Double) {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    sharedPreferences.edit().putString("monthly_saving_goal_${year}_$month", goal.toString()).apply()
}

// 載入當月儲蓄目標
fun loadMonthlySavingGoal(context: Context, year: Int, month: Int): Double? {
    val sharedPreferences = context.getSharedPreferences("BudgetManagerPrefs", Context.MODE_PRIVATE)
    val goalStr = sharedPreferences.getString("monthly_saving_goal_${year}_$month", null)
    return goalStr?.toDoubleOrNull()
}

//匯出成csv
fun exportMonthlyReportToCSV(context: Context, allMonthlyRecords: List<Any>) {
    // 檔案名稱，格式為 "Monthly_Report_yyyy-MM.csv"
    val firstRecord = allMonthlyRecords.firstOrNull()
    val currentDate = if (firstRecord != null) {
        val date = Date((firstRecord as? ExpenseRecord)?.date ?: (firstRecord as? IncomeRecord)?.date ?: 0L)
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date)
    } else {
        // 若 records 為空，預設為當前日期
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    val fileName = "Monthly_Report_$currentDate.csv"

    // 將資料轉換為 CSV 格式
    val csvHeader = "日期,類型,類別,金額,貨幣\n"
    val csvContent = buildString {
        append(csvHeader)
        allMonthlyRecords.forEach { record ->
            when (record) {
                is ExpenseRecord -> append(
                    "${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(record.date))},${record.type},支出,${record.amount},${record.currency}\n"
                )
                is IncomeRecord -> append(
                    "${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(record.date))},${record.type},收入,${record.amount},${record.currency}\n"
                )
            }
        }
    }

    // 將 CSV 寫入檔案
    try {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw Exception("無法取得下載資料夾")
        val csvFile = File(downloadsDir, fileName)

        csvFile.writeText(csvContent)
        // 通知使用者成功
        Toast.makeText(context, "匯出成功！檔案儲存於：${csvFile.absolutePath}", Toast.LENGTH_LONG).show()

        //啟動檔案檢視器
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.provider", csvFile), "*/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)


    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "匯出失敗：${e.message}", Toast.LENGTH_LONG).show()
    }
}

object OpenAIAnalysis {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun analyzeSpending(apiKey: String, income: Double, expenses: Map<String, Double>): String {
        if (apiKey.isBlank()) return "Error: API key is missing."
        Log.d("OpenAIAnalysis", "Starting spending analysis...")

        // 構建 Prompt
        val expenseDetails = expenses.entries.joinToString("\n") { "${it.key}: \$${it.value}" }
        val prompt = """
            You are a financial assistant. Please analyze the following:
            Income: $income
            Expenses:
            $expenseDetails
            
            請將結果以中文呈現
            同時用條列式對收入及各項支出提供建議
            總共字數必須小於200字
            不需要輸出原有的數據。
        """.trimIndent()
        Log.d("OpenAIAnalysis", "Prompt created:\n$prompt")

        // 構建 JSON 請求
        val userMessage = JSONObject()
            .put("role", "user")
            .put("content", prompt)
        val messagesArray = JSONArray().put(userMessage)
        val jsonRequest = JSONObject()
            .put("model", "gpt-4")
            .put("messages", messagesArray)
            .put("max_tokens", 8000)
            .put("temperature", 0.7)
            .toString()

        val jsonRequestBody = jsonRequest.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonRequestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return "No response"
                    Log.d("OpenAIAnalysis", "Response received: $body")
                    JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    Log.e("OpenAIAnalysis", "Error: ${response.code} - ${response.message}")
                    "Error: ${response.code} - ${response.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("OpenAIAnalysis", "Exception occurred: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
}


