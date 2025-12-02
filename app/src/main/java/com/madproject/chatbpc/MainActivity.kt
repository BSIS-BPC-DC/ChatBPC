@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.madproject.chatbpc

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.pow
// Mobile Application Development 213 | Finals Project | ChatBPC:AiChat-Buddy
// Programming Instructor Mr. Migs Gatchalian
// Team Lead/Prog 1: D. Cayanes | Technical Writer: A.P. Fernandez | Programmer: J. Del Prado | System Analyst: K.H. Marquez

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val question = findViewById<EditText>(R.id.etQuestion)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val txtResponse = findViewById<TextView>(R.id.txtResponse)

        btnSubmit.setOnClickListener {
            val query = question.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show()
            } else {
                txtResponse.text = "Thinking..."
                callOpenRouter(query) { result ->
                    handler.post {
                        txtResponse.text = result
                    }
                }
            }
        }
    }

    private fun callOpenRouter(prompt: String, callback: (String) -> Unit) {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val apiKey = "Can't show my API"

        val json = JSONObject().apply {
            put("model", "x-ai/grok-4.1-fast:free")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://openrouter.ai/")
            .header("X-Title", "ChatBPC")
            .post(body)
            .build()

        executeWithRetry(request, callback)
    }

    private fun executeWithRetry(request: Request, callback: (String) -> Unit, attempt: Int = 0) {
        val maxRetries = 6

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val code = response.code
                    val bodyString = response.body?.string()

                    if (response.isSuccessful && bodyString != null) {
                        try {
                            val jsonResponse = JSONObject(bodyString)
                            val content = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            callback(content.trim())
                        } catch (e: Exception) {
                            callback("Parse Error: ${e.message}\n\nRaw:\n$bodyString")
                        }
                    } else if (code == 429 && attempt < maxRetries) {
                        val retryAfter =
                            response.header("Retry-After")?.toLongOrNull() ?: (2.0.pow(attempt)).toLong()
                        val waitMs = retryAfter * 1000
                        handler.postDelayed({
                            executeWithRetry(request, callback, attempt + 1)
                        }, waitMs)
                    } else {
                        callback("API Error: $code\n\nResponse:\n${bodyString.orEmpty()}")
                    }
                }
            }
        })
    }
}
