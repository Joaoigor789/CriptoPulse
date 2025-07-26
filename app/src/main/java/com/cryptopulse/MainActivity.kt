package com.cryptopulse

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.cryptopulse.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

class CryptoScraper {
    suspend fun getBitcoinPriceBRL(): Double? {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=brl"
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = connection.inputStream
                    val response = stream.bufferedReader().use { it.readText() }
                    stream.close()

                    val jsonObj = JSONObject(response)
                    val bitcoinObj = jsonObj.getJSONObject("bitcoin")
                    bitcoinObj.getDouble("brl")
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding  // ViewBinding
    private val scraper = CryptoScraper()
    private val handler = Handler(Looper.getMainLooper())

    private var series = LineGraphSeries<DataPoint>()
    private var pointIndex = 0
    private var selectedCoin = "bitcoin" // Atualmente só bitcoin suportado no scraper
    private var countdownSeconds = 5

    private val updateTask = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            lifecycleScope.launch {
                val price = scraper.getBitcoinPriceBRL()
                if (price != null) {
                    binding.priceText.text = "Preço BTC (BRL): R$ %.2f".format(price)
                    addDataPoint(price)
                } else {
                    binding.priceText.text = "Atualizando preço"
                }
            }
            countdownSeconds = 10
            handler.postDelayed(this, 10000)
        }
    }





    private val countdownTask = object : Runnable {
        override fun run() {
            if (countdownSeconds > 0) {
                binding.timerText.text = "Próxima atualização em: $countdownSeconds s"
                countdownSeconds--
                handler.postDelayed(this, 1000)
            } else {
                binding.timerText.text = "Atualizando..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupGraph()

        updateTask.run()
        countdownTask.run()

        binding.priceText.text = "Carregando preço..."
    }

    private fun setupSpinner() {
        val coins = listOf("bitcoin") // Só bitcoin suportado pelo scraper no momento
        binding.coinSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, coins)
        binding.coinSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCoin = coins[position]
                resetGraph()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupGraph() {
        series = LineGraphSeries()

        // Personalização da linha do gráfico
        series.color = android.graphics.Color.parseColor("#FF5722") // laranja vibrante
        series.thickness = 8
        series.isDrawDataPoints = true
        series.dataPointsRadius = 10f
        series.isDrawBackground = true
        series.backgroundColor = android.graphics.Color.argb(50, 255, 87, 34) // fundo laranja suave

        binding.graph.removeAllSeries()
        binding.graph.addSeries(series)

        // Configurações do gráfico
        binding.graph.viewport.isScrollable = true
        binding.graph.viewport.isScalable = true
        binding.graph.viewport.isXAxisBoundsManual = true
        binding.graph.viewport.isYAxisBoundsManual = true

        binding.graph.viewport.setMinX(0.0)
        binding.graph.viewport.setMaxX(10.0)
        binding.graph.viewport.setMinY(0.0)
        binding.graph.viewport.setMaxY(100000.0)

        binding.graph.gridLabelRenderer.horizontalAxisTitle = "Tempo"
        binding.graph.gridLabelRenderer.verticalAxisTitle = "Preço (BRL)"
    }

    private fun updateUI(price: Double) {
        runOnUiThread {
            val formattedPrice = "R$ ${String.format("%.2f", price)}"
            binding.priceText.text = formattedPrice

            // Animação 3D ao atualizar
            binding.priceText.animate()
                .rotationXBy(360f)
                .setDuration(600)
                .start()
        }
    }



    private fun resetGraph() {
        runOnUiThread {
            pointIndex = 0
            series.resetData(arrayOf()) // limpa dados sem remover série

            binding.graph.viewport.setMinX(0.0)
            binding.graph.viewport.setMaxX(10.0)
            binding.graph.viewport.setMinY(0.0)
            binding.graph.viewport.setMaxY(100000.0)
        }
    }




    private fun addDataPoint(price: Double) {
        runOnUiThread {
            series.appendData(DataPoint(pointIndex.toDouble(), price), true, 50)
            pointIndex++

            if (pointIndex > 10) {
                binding.graph.viewport.setMinX(pointIndex - 10.0)
                binding.graph.viewport.setMaxX(pointIndex.toDouble())
            } else {
                binding.graph.viewport.setMinX(0.0)
                binding.graph.viewport.setMaxX(10.0)
            }

            val minY = series.lowestValueY
            val maxY = series.highestValueY
            val margin = (maxY - minY) * 0.1
            binding.graph.viewport.setMinY(minY - margin)
            binding.graph.viewport.setMaxY(maxY + margin)

            binding.graph.viewport.isYAxisBoundsManual = true
            binding.graph.viewport.isXAxisBoundsManual = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTask)
        handler.removeCallbacks(countdownTask)
    }
}
