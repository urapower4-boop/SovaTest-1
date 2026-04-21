package com.sova.test

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    data class Result(
        val question: String,
        val correctIndex: Int,
        val correctText: String,
        val bbox: IntArray?,
        val explanation: String,
        val raw: String
    )

    /** Анализ сырого битмапа. */
    suspend fun analyzeBitmap(screen: Bitmap): Result = withContext(Dispatchers.IO) {
        val b64 = bitmapToBase64Jpeg(screen, 80)
        val prompt = """
Ти — асистент на онлайн-тесті українською мовою (всеосвіта, «На Урок», Google Forms тощо).
На скріншоті — питання тесту і варіанти відповідей.

Завдання:
1. Визнач питання.
2. Обери ПРАВИЛЬНИЙ варіант відповіді.
3. Поверни координати прямокутника навколо тексту правильного варіанта у пікселях скріншота.
   Система координат: (0,0) — лівий верхній кут. Вісь Y — вниз.
4. Дай коротке пояснення (1–3 речення).

ВИДАЙ СТРОГО JSON без зайвого тексту, без ```json:
{
  "question": "текст питання",
  "correct_index": 0,
  "correct_text": "текст правильного варіанта",
  "bbox": [x, y, width, height],
  "explanation": "пояснення"
}

Якщо не можеш визначити координати — постав bbox: null.
Якщо на скріні немає тесту — постав correct_index: -1 і explanation: "no_test".
""".trimIndent()

        callGemini(prompt, b64)
    }

    /** Анализ по текстовому DOM-описанию теста (без картинки). */
    suspend fun analyzeText(question: String, options: List<String>): Result = withContext(Dispatchers.IO) {
        val opts = options.mapIndexed { i, s -> "$i: $s" }.joinToString("\n")
        val prompt = """
Питання: $question

Варіанти:
$opts

Вибери ПРАВИЛЬНИЙ варіант. Відповідай СТРОГО JSON:
{
  "question": "$question",
  "correct_index": <номер>,
  "correct_text": "<текст>",
  "bbox": null,
  "explanation": "<чому>"
}
""".trimIndent()

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                )
            ))
            put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json"))
        }
        executeRequest(body)
    }

    private suspend fun callGemini(prompt: String, base64Image: String): Result {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)
                    ))
                )
            ))
            put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json"))
        }
        return executeRequest(body)
    }

    private fun executeRequest(body: JSONObject): Result {
        val req = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result("", -1, "", null,
                    "HTTP ${resp.code}: ${raw.take(200)}", raw)
            }
            return parseGeminiResponse(raw)
        }
    }

    private fun parseGeminiResponse(raw: String): Result {
        return try {
            val root = JSONObject(raw)
            val text = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val cleaned = text
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val j = JSONObject(cleaned)

            val bbox: IntArray? = if (j.isNull("bbox")) null else {
                val arr = j.getJSONArray("bbox")
                if (arr.length() == 4)
                    intArrayOf(arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3))
                else null
            }

            Result(
                question = j.optString("question", ""),
                correctIndex = j.optInt("correct_index", -1),
                correctText = j.optString("correct_text", ""),
                bbox = bbox,
                explanation = j.optString("explanation", ""),
                raw = text
            )
        } catch (e: Exception) {
            Result("", -1, "", null, "parse_error: ${e.message}", raw)
        }
    }

    private fun bitmapToBase64Jpeg(bmp: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        val scaled = if (bmp.width > 1280) {
            val ratio = 1280f / bmp.width
            Bitmap.createScaledBitmap(bmp, 1280, (bmp.height * ratio).toInt(), true)
        } else bmp
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
