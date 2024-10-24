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

private val REQUEST_READ_EXTERNAL_STORAGE = 100

class MainActivity : ComponentActivity() {

    private lateinit var lineChart: LineChart

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

        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_READ_EXTERNAL_STORAGE)
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Show a rationale to the user
            } else {
                // Request the permission
                requestPermission()
                pickUpCsv()
                //filePickerCheck()
            }
        } else{
            //pickCsvFileAndBuildChart()
            pickUpCsv()
        }

        //pickUpCsv()
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
            //Toast.makeText(this, "Permission denied!!!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    @Deprecated("")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //pickCsvFileAndBuildChart()
                pickUpCsv()

            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
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
        lineChart = findViewById(R.id.lineChart)
        // Register the file picker to open a CSV file
        val pickCsvFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val parsedData = parseCsvFile(this, uri)
                plotData(lineChart, parsedData)
            } ?: Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }

        // Launch file picker for CSV file
        pickCsvFile.launch("*/*")
    }

    private fun parseCsvFile(context: Context, uri: Uri): CsvData {
        val resultList = mutableListOf<CsvDataValues>()
        val result = CsvData(0.0f, 0.0f, resultList)

        // Open input stream from the content resolver
        val inputStream = context.contentResolver.openInputStream(uri) ?: return result

        // Read CSV using OpenCSV
        val reader = BufferedReader(InputStreamReader(inputStream))
        val csvReader = CSVReader(reader)
        val rows = csvReader.readAll()

        // Skip the header (first row)
        val header = rows.firstOrNull() ?: return result

        // Set the expected DateTime format
        val dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT)

        // Iterate over the CSV rows, starting from the second row
        for (row in rows.drop(1)) {
            // Parse DateTime from the first column
            val dateTimeString = row[0].trim()
            try {
                val dateTime = try { LocalDateTime.parse(dateTimeString, dateTimeFormatter)
                }catch (e: DateTimeParseException) {
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
                // Handle parsing errors for value1 or value2
                e.printStackTrace()
                continue
            }
        }

        return result
    }

    data class CsvData(
        var maxV: Float,
        var minV: Float,
        val values: List<CsvDataValues>
    )

    data class CsvDataValues(
        val dateTime: LocalDateTime,
        val voltage: Float,
        val relay: Float,
    )

    // Convert LocalDateTime to epoch milliseconds
    private fun LocalDateTime.toEpochMillis(): Long {
        return this.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
    }

    val chooseValue: (Boolean, Float, Float) -> Float = { condition, trueValue, falseValue ->
        if (condition) trueValue else falseValue
    }

    private fun plotData(chart: LineChart, data: CsvData) {
        /*val voltage = data.mapIndexed { index, csvData ->
            Entry(index.toFloat(), csvData.voltage)  // Plot based on Voltage
        }*/

        val voltage = data.values.map { csvData ->
            Entry(csvData.dateTime.toEpochMillis().toFloat(), csvData.voltage) // X = epoch millis, Y = voltage
        }

        /*val relay = data.mapIndexed { index, csvData ->
            Entry(index.toFloat(), csvData.relay)  // Plot based on Relay
        }*/

        val relay = data.values.map { csvData ->
            Entry(csvData.dateTime.toEpochMillis().toFloat(), chooseValue(csvData.relay > 0.0f, data.maxV, data.minV)) // X = epoch millis, Y = relay
        }

        val dataSetVoltage = LineDataSet(voltage, "Voltage")
        dataSetVoltage.color = Color.BLUE
        dataSetVoltage.setCircleColor(Color.BLUE)

        val dataSetRelay = LineDataSet(relay, "Relay")
        dataSetRelay.color = Color.BLACK
        dataSetRelay.setCircleColor(Color.BLACK)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        //xAxis.valueFormatter = IndexAxisValueFormatter(xValues)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

            override fun getFormattedValue(value: Float): String {
                // Convert float (epoch millis) back to LocalDateTime and format it
                val millis = value.toLong()
                val dateTime = LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC)
                return dateTimeFormatter.format(dateTime)
            }
        }
        xAxis.granularity = 86400000f // 1 day in milliseconds
        xAxis.labelRotationAngle = -45f // Rotate labels for better visibility

        val lineData = LineData(dataSetVoltage, dataSetRelay)

        chart.data = lineData
        chart.description.isEnabled = false
        chart.invalidate() // Refresh the chart
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