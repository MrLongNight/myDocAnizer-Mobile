package com.example.service

import android.content.Context
import com.example.model.DocRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository zur Speicherung und Auswertung von deterministischen Schlagwort-Regeln.
 * Auswertung erfolgt in Millisekunden noch VOR dem Aufruf eines LLMs.
 */
class DocRuleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("doc_rules_storage", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<DocRule>>(loadRules())
    val rules: StateFlow<List<DocRule>> = _rules.asStateFlow()

    private fun loadRules(): List<DocRule> {
        val json = prefs.getString("saved_doc_rules_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<DocRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val matchArr = obj.optJSONArray("matchKeywords") ?: JSONArray()
                val matchWords = (0 until matchArr.length()).map { matchArr.getString(it) }

                val exclArr = obj.optJSONArray("excludeKeywords") ?: JSONArray()
                val exclWords = (0 until exclArr.length()).map { exclArr.getString(it) }

                val tagsArr = obj.optJSONArray("targetTags") ?: JSONArray()
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }

                val cfObj = obj.optJSONObject("targetCustomFields") ?: JSONObject()
                val customFieldsMap = mutableMapOf<String, String>()
                val cfKeys = cfObj.keys()
                while (cfKeys.hasNext()) {
                    val key = cfKeys.next()
                    customFieldsMap[key] = cfObj.getString(key)
                }

                list.add(
                    DocRule(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Regel"),
                        matchKeywords = matchWords,
                        excludeKeywords = exclWords,
                        targetMainCategoryId = obj.optString("targetMainCategoryId", "A01"),
                        targetSubCategoryId = obj.optString("targetSubCategoryId", "B1.01"),
                        targetDocType = obj.optString("targetDocType", "Dokument"),
                        detectedSender = obj.optString("detectedSender", ""),
                        targetTags = tags,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        confidenceScore = obj.optDouble("confidenceScore", 1.0).toFloat(),
                        isAiGenerated = obj.optBoolean("isAiGenerated", false),
                        targetCustomFields = customFieldsMap
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRules(list: List<DocRule>) {
        try {
            val arr = JSONArray()
            list.forEach { r ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("matchKeywords", JSONArray(r.matchKeywords))
                    put("excludeKeywords", JSONArray(r.excludeKeywords))
                    put("targetMainCategoryId", r.targetMainCategoryId)
                    put("targetSubCategoryId", r.targetSubCategoryId)
                    put("targetDocType", r.targetDocType)
                    put("detectedSender", r.detectedSender)
                    put("targetTags", JSONArray(r.targetTags))
                    put("isEnabled", r.isEnabled)
                    put("confidenceScore", r.confidenceScore.toDouble())
                    put("isAiGenerated", r.isAiGenerated)
                    val cfObj = JSONObject()
                    r.targetCustomFields.forEach { (k, v) -> cfObj.put(k, v) }
                    put("targetCustomFields", cfObj)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_doc_rules_json", arr.toString()).apply()
            _rules.value = list
        } catch (e: Exception) {
            // Logging
        }
    }

    fun addRule(rule: DocRule) {
        val current = _rules.value.toMutableList()
        current.removeAll { it.id == rule.id }
        current.add(0, rule)
        saveRules(current)
    }

    fun addRules(newRules: List<DocRule>) {
        val current = _rules.value.toMutableList()
        newRules.forEach { nr ->
            current.removeAll { it.id == nr.id }
            current.add(0, nr)
        }
        saveRules(current)
    }

    fun updateRule(rule: DocRule) {
        val current = _rules.value.map { if (it.id == rule.id) rule else it }
        saveRules(current)
    }

    fun deleteRule(ruleId: String) {
        val current = _rules.value.filter { it.id != ruleId }
        saveRules(current)
    }

    fun toggleRule(ruleId: String, enabled: Boolean) {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(isEnabled = enabled) else it
        }
        saveRules(current)
    }

    /**
     * Schneller Regel-Check des OCR-Textes (Schritt 1 VOR dem LLM)
     * Gibt die erste passende Regel zurück oder null
     */
    fun matchRule(ocrText: String): DocRule? {
        if (ocrText.isBlank()) return null
        val lowerText = ocrText.lowercase()

        for (rule in _rules.value) {
            if (!rule.isEnabled || rule.matchKeywords.isEmpty()) continue

            // 1. Ausschlusskriterien prüfen
            val hasExcludedWord = rule.excludeKeywords.any {
                it.isNotBlank() && lowerText.contains(it.lowercase())
            }
            if (hasExcludedWord) continue

            // 2. Pflicht-Keywords prüfen (alle Keywords der Regel müssen vorkommen)
            val allMatch = rule.matchKeywords.all {
                it.isNotBlank() && lowerText.contains(it.lowercase())
            }

            if (allMatch) {
                return rule
            }
        }
        return null
    }
}
