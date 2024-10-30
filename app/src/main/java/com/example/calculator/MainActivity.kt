package com.example.calculator

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

interface CurrencyCallback {
    fun onSuccess(currencies: Array<String>)
    fun onFailure(error: IOException)
}

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var sourceAmountEditText: EditText
    private lateinit var targetAmountEditText: EditText
    private lateinit var sourceCurrencySpinner: Spinner
    private lateinit var targetCurrencySpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourceAmountEditText = findViewById(R.id.sourceAmountEditText)
        targetAmountEditText = findViewById(R.id.targetAmountEditText)
        sourceCurrencySpinner = findViewById(R.id.sourceCurrencySpinner)
        targetCurrencySpinner = findViewById(R.id.targetCurrencySpinner)

        fetchSupportedCurrency(object : CurrencyCallback {
            override fun onSuccess(currencies: Array<String>) {
                runOnUiThread {
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currencies)
                    sourceCurrencySpinner.adapter = adapter
                    targetCurrencySpinner.adapter = adapter

                    val defaultPosition = currencies.indexOf("USD")
                    if (defaultPosition != -1) {
                        sourceCurrencySpinner.setSelection(defaultPosition)
                        targetCurrencySpinner.setSelection(defaultPosition)
                    }

                }
            }

            override fun onFailure(error: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to fetch currencies: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })

        sourceAmountEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateConversion()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        sourceCurrencySpinner.onItemSelectedListener = SpinnerListener()
        targetCurrencySpinner.onItemSelectedListener = SpinnerListener()
    }

    inner class SpinnerListener : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: android.view.View?,
            position: Int,
            id: Long
        ) {
            updateConversion()
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }

    private fun updateConversion() {
        val sourceAmount = sourceAmountEditText.text.toString().toDoubleOrNull()

        if(sourceAmount == null) {
            targetAmountEditText.setText("Converted amount")
            return
        }

        val sourceCurrency = sourceCurrencySpinner.selectedItem.toString()
        val targetCurrency = targetCurrencySpinner.selectedItem.toString()

        fetchExchangeRate(sourceCurrency, targetCurrency) { rate, error ->
            if (rate != null) {
                val convertedAmount = sourceAmount * rate
                targetAmountEditText.setText(String.format("%.2f", convertedAmount))
            } else {
                Toast.makeText(this, error ?: "Unknown error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchExchangeRate(baseCurrency: String, targetCurrency: String, callback: (Double?, String?) -> Unit) {
        val url = "https://api.currencyfreaks.com/v2.0/rates/latest?apikey=58d3f618b18a4cec8de3ec554b6bb6e7&symbols=$targetCurrency&base=$baseCurrency"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    callback(null, "Failed to fetch exchange rate: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        return
                    }

                    val responseBody = response.body()?.string()
                    if (responseBody != null) {
                        try {
                            val jsonObject = JSONObject(responseBody)
                            val ratesObject = jsonObject.getJSONObject("rates")
                            val rate = ratesObject.getDouble(targetCurrency)
                            runOnUiThread {
                                callback(rate, null)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                callback(null, "Error parsing JSON: ${e.message}")
                            }
                        }
                    } else {
                        runOnUiThread {
                            callback(null, "Empty response body")
                        }
                    }
                }
            }
        })
    }

    private fun fetchSupportedCurrency(callback: CurrencyCallback) {
        val request = Request.Builder()
            .url("https://api.currencyfreaks.com/v2.0/supported-currencies")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onFailure(IOException("Unexpected code $response"))
                    return
                }

                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val supportedCurrenciesMap = jsonObject.getJSONObject("supportedCurrenciesMap")
                    val currencyCodes = supportedCurrenciesMap.keys().asSequence().toList().toTypedArray()
                    callback.onSuccess(currencyCodes)
                } else {
                    callback.onFailure(IOException("Response body is null"))
                }
            }
        })
    }
}
