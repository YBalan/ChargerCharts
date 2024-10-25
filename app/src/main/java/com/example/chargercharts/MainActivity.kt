package com.example.chargercharts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.chargercharts.ui.theme.ChargerChartsTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.opencsv.CSVReader
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.Manifest
import android.content.Context
import android.net.Uri
import java.time.LocalDateTime
import java.time.format.*
import java.time.format.DateTimeParseException
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.ZoneOffset
import android.widget.CheckBox
import android.widget.TextView
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.OnBackPressedCallback
import android.util.Log
import java.util.Locale

private val REQUEST_READ_EXTERNAL_STORAGE = 100
private val FILE_PICKER_REQUEST_CODE = 1

data class CsvData(
    var maxV: Float,
    var minV: Float,
    val values: List<CsvDataValues>,
    val dateTimeChartFormat: String = "yyyy-MM-dd HH:mm",
    val dateTimeCsvFormat: String = "uuuu-MM-dd HH:mm:ss",
    var voltageLabel: String = "Voltage",
    var voltageVisible: Boolean = true,
    var relayLabel: String = "Relay",
    var relayVisible: Boolean = true,
)

data class CsvDataValues(
    val dateTime: LocalDateTime,
    val voltage: Float,
    val relay: Float,
)

class MainActivity : ComponentActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var fileNameLabel: TextView
    private lateinit var checkboxVoltage: CheckBox
    private lateinit var checkboxRelay: CheckBox

    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onStart() {
        super.onStart()
        // Add an OnBackPressedCallback to intercept the back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, "Back to File Picker", Toast.LENGTH_SHORT).show()
                try {
                    pickUpCsv() // Open the file picker when the back button is pressed
                }
                catch (e: Exception){
                    e.printStackTrace();
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChargerChartsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Username",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_EXTERNAL_STORAGE)
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Request the permission
                requestPermission()
            } else {
                pickUpCsv()
            }
        } else{
            pickUpCsv()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted)
        {
            // Permission granted, you can access external storage
            pickUpCsv()
            //filePickerCheck()
        } else {
            // Permission denied
            Toast.makeText(this, "Permission denied!!!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private fun filePickerCheck(){
        val pickCsvFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                // File is selected, process the URI
                Toast.makeText(this, "File Selected: $uri", Toast.LENGTH_SHORT).show()
                // You can now read the file using contentResolver
            } else {
                Toast.makeText(this, "Picker Check: No file selected", Toast.LENGTH_SHORT).show()
            }
        }

        // Launch the file picker with the correct MIME type for CSV files
        pickCsvFile.launch(arrayOf("*/*"))
    }

    private fun pickUpCsv() {
        // Register the file picker to open a CSV file
        val pickCsvFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val parsedData = parseCsvFile(this, uri)
                if(parsedData.values.isNotEmpty()) {
                    setContentView(R.layout.activity_main)
                    lineChart = findViewById(R.id.lineChart)
                    fileNameLabel = findViewById(R.id.fileNameLabel)
                    checkboxVoltage = findViewById(R.id.checkboxVoltage)
                    checkboxRelay = findViewById(R.id.checkboxRelay)
                    checkboxVoltage.isChecked = parsedData.voltageVisible
                    checkboxRelay.isChecked = parsedData.relayVisible
                    checkboxVoltage.text = parsedData.voltageLabel
                    checkboxRelay.text = parsedData.relayLabel

                    fileNameLabel.text = getFileNameFromUri(this, uri)

                    // Set up CheckBox listeners to toggle chart visibility
                    checkboxVoltage.setOnCheckedChangeListener { _, isChecked ->
                        toggleDataSetVisibility(parsedData.voltageLabel, isChecked)
                    }

                    checkboxRelay.setOnCheckedChangeListener { _, isChecked ->
                        toggleDataSetVisibility(parsedData.relayLabel, isChecked)
                    }

                    if (!plotData(lineChart, parsedData)) {
                        Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show()
                    }
                }else
                    Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }

        // Launch file picker for CSV file
        pickCsvFile.launch("*/*")
    }

    private fun toggleDataSetVisibility(label: String, isVisible: Boolean) {
        // Find the dataset by label and set its visibility
        val dataSet = lineChart.data?.getDataSetByLabel(label, true)
        dataSet?.isVisible = isVisible
        lineChart.invalidate() // Refresh chart to apply changes
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && it.moveToFirst()) {
                    fileName = it.getString(nameIndex)
                }
            }
        } else if (uri.scheme == "file") {
            fileName = uri.lastPathSegment
        }
        return fileName
    }

    private fun parseCsvFile(context: Context, uri: Uri): CsvData {
        val resultList = mutableListOf<CsvDataValues>()
        val result = CsvData(0.0f, 0.0f, resultList)

        val rows = try {
            // Open input stream from the content resolver
            val inputStream = context.contentResolver.openInputStream(uri) ?: return result

            // Read CSV using OpenCSV
            val reader = BufferedReader(InputStreamReader(inputStream))
            val csvReader = CSVReader(reader)
            csvReader.readAll()
        }catch (e: FileSystemException){
            e.printStackTrace()
            return result
        }

        // Skip the header (first row)
        val header = rows.firstOrNull() ?: return result
        if(header.count() >= 3){
            result.voltageLabel = readHeader(header[1]) ?: result.voltageLabel
            result.relayLabel = readHeader(header[2]) ?: result.relayLabel
        }

        // Set the expected DateTime format
        val dateTimeFormatter = DateTimeFormatter.ofPattern(result.dateTimeCsvFormat)
            .withResolverStyle(ResolverStyle.STRICT)

        var rowIndex = 1
        // Iterate over the CSV rows, starting from the second row
        for (row in rows.drop(rowIndex)) {
            // Parse DateTime from the first column
            var dateTimeString = row[0].trim()
            val firstDigitIdx = dateTimeString.indexOfFirst { c -> c.isDigit() }
            if(firstDigitIdx >= 0)
                dateTimeString = dateTimeString.substring(firstDigitIdx)
            try {
                val dateTime = try { LocalDateTime.parse(dateTimeString, dateTimeFormatter)
                }catch (e: DateTimeParseException) {
                    Log.e("MainActivity", "RowIndex: $rowIndex text: '$dateTimeString'")
                    e.printStackTrace()
                    continue
                }

                // Parse other columns (assuming integers for Value1 and Value2)
                val voltage = row[1].trim().toFloat()
                val relay = row[2].trim().toFloat()

                result.maxV = chooseValue(result.maxV < voltage, voltage, result.maxV)
                result.minV = chooseValue(result.minV == 0.0f || result.minV > voltage, voltage, result.minV)

                // Add to the result list
                resultList.add(CsvDataValues(dateTime, voltage, relay))
            }
            catch (e: NumberFormatException) {
                Log.e("MainActivity", "RowIndex: $rowIndex text: '$dateTimeString'")
                // Handle parsing errors for value1 or value2
                e.printStackTrace()
                continue
            }
            rowIndex++
        }

        return result
    }

    private fun readHeader(value: String) : String?{
        var result: String? = null
        try{
            if (value.isEmpty()) return result
            value.toFloat();
        }
        catch (e: NumberFormatException){
            result = value;
        }
        return result;
    }

    // Convert LocalDateTime to epoch milliseconds
    private fun LocalDateTime.toEpochMillis(): Long {
        return this.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
    }

    val chooseValue: (Boolean, Float, Float) -> Float = { condition, trueValue, falseValue ->
        if (condition) trueValue else falseValue
    }

    private fun plotData(chart: LineChart, data: CsvData) : Boolean {
        if(data.values.isEmpty()) return false

        val voltage = data.values.map { csvData ->
            Entry(csvData.dateTime.toEpochMillis().toFloat(), csvData.voltage) // X = epoch millis, Y = voltage
        }

        val relayOffset = 0.1f
        val relay = data.values.map { csvData ->
            Entry(csvData.dateTime.toEpochMillis().toFloat(),
                chooseValue(csvData.relay > 0.0f, data.maxV + relayOffset,
                    chooseValue(data.minV - relayOffset > 0f, data.minV - relayOffset, 0f))) // X = epoch millis, Y = relay
        }

        val dataSetVoltage = LineDataSet(voltage, data.voltageLabel)
        dataSetVoltage.isVisible = data.voltageVisible
        dataSetVoltage.color = Color.BLUE
        dataSetVoltage.setCircleColor(Color.BLUE)

        val dataSetRelay = LineDataSet(relay, data.relayLabel)
        dataSetRelay.isVisible = data.relayVisible
        dataSetRelay.color = Color.GRAY
        dataSetRelay.setCircleColor(Color.GRAY)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        //xAxis.valueFormatter = IndexAxisValueFormatter(xValues)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateTimeFormatter = DateTimeFormatter.ofPattern(data.dateTimeChartFormat)

            override fun getFormattedValue(value: Float): String {
                // Convert float (epoch millis) back to LocalDateTime and format it
                val millis = value.toLong()
                val dateTime = LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC)
                return dateTimeFormatter.format(dateTime)
            }
        }
        val desiredLabelsCount = 48;
        var granularity =
            ((data.values.last().dateTime.toEpochMillis() - data.values.first().dateTime.toEpochMillis()) / desiredLabelsCount).toFloat()

        xAxis.granularity = granularity // in milliseconds
        xAxis.isGranularityEnabled = granularity > 0f
        xAxis.labelRotationAngle = -45f // Rotate labels for better visibility

        chart.viewPortHandler.setMaximumScaleX(5f) // Allow max zoom level of 5x
        chart.viewPortHandler.setMinimumScaleX(1f) // Allow minimum zoom level of 1x

        val lineData = LineData(dataSetVoltage, dataSetRelay)

        chart.data = lineData
        chart.description.isEnabled = false

        val markerView = CustomMarkerView(this, R.layout.custom_marker_view)
        lineChart.marker = markerView

        chart.invalidate() // Refresh the chart

        return true
    }

    // Custom marker view class
    private class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
        private val tvContent: TextView = findViewById(R.id.tvContent)
        val data = CsvData(0f, 0f, emptyList())
        private val dateTimeFormatter = DateTimeFormatter.ofPattern(data.dateTimeCsvFormat)

        override fun refreshContent(e: Entry, highlight: Highlight?) {
            tvContent.text =
                String.format(Locale.getDefault(), "DT: %s\n%s: %.2f",
                    dateTimeFormatter.format(LocalDateTime.ofEpochSecond(e.x.toLong() / 1000, 0, ZoneOffset.UTC)),
                    "Val",
                    e.y
                ) // Customize the content displayed in the tooltip
            super.refreshContent(e, highlight)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChargerChartsTheme {
        Greeting("Username")
    }
}