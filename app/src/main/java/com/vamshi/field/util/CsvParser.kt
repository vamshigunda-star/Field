package com.vamshi.field.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    private const val TAG = "CsvParser"

    fun parse(inputStream: InputStream): List<Map<String, String>> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var headerLine = reader.readLine() ?: return emptyList()
        
        // Handle UTF-8 BOM if present
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1)
        }
        
        val headers = parseLine(headerLine).map { it.trim() }

        val result = mutableListOf<Map<String, String>>()
        var lineNumber = 1
        var line: String? = reader.readLine()
        while (line != null) {
            lineNumber++
            if (line.isBlank()) {
                line = reader.readLine()
                continue
            }
            val values = parseLine(line)
            if (values.size == headers.size) {
                val row = headers.zip(values).toMap()
                result.add(row)
            } else {
                Log.w(TAG, "Skipping malformed row at line $lineNumber: expected ${headers.size} columns, got ${values.size}: $line")
            }
            line = reader.readLine()
        }
        return result
    }

    private fun parseLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    tokens.add(currentToken.toString().trim())
                    currentToken = StringBuilder()
                }
                else -> {
                    currentToken.append(c)
                }
            }
            i++
        }
        tokens.add(currentToken.toString().trim())
        return tokens
    }
}
