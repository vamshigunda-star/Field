package com.vamshi.field.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.testing.TestResult
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private fun escapeCsv(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    fun exportAthleteResults(context: Context, athlete: Individual, results: List<TestResult>, tests: Map<String, FitnessTest>) {
        val safeLast = athlete.lastName.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val safeFirst = athlete.firstName.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val fileName = "Athlete_${safeLast}_${safeFirst}_Export.csv"
        val header = "Test Name,Date,Score,Unit,Percentile,Classification\n"

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val content = results.sortedByDescending { it.createdAt }.joinToString("\n") { result ->
            val test = tests[result.testId]
            val date = df.format(Date(result.createdAt))
            val score = result.rawScore
            val unit = test?.unit ?: ""
            val percentile = result.percentile ?: ""
            val classification = result.classification ?: ""

            "${escapeCsv(test?.name ?: "Unknown")},$date,$score,${escapeCsv(unit)},$percentile,${escapeCsv(classification)}"
        }

        val csvData = header + content
        downloadThenShare(context, fileName, csvData)
    }

    fun exportEventResults(context: Context, eventName: String, results: List<Pair<Individual, TestResult>>, tests: Map<String, FitnessTest>) {
        val safeEvent = eventName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Event_${safeEvent}_Export.csv"
        val header = "Athlete Name,Test Name,Date,Score,Unit,Percentile,Classification\n"

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val content = results.sortedBy { it.first.lastName }.joinToString("\n") { (athlete, result) ->
            val test = tests[result.testId]
            val date = df.format(Date(result.createdAt))
            val score = result.rawScore
            val unit = test?.unit ?: ""
            val percentile = result.percentile ?: ""
            val classification = result.classification ?: ""

            "${escapeCsv(athlete.fullName)},${escapeCsv(test?.name ?: "Unknown")},$date,$score,${escapeCsv(unit)},$percentile,${escapeCsv(classification)}"
        }

        val csvData = header + content
        downloadThenShare(context, fileName, csvData)
    }

    /** Saves the CSV to the device's public Downloads folder, then offers it via the share sheet. */
    private fun downloadThenShare(context: Context, fileName: String, content: String) {
        val downloadUri = saveToDownloads(context, fileName, content)
        if (downloadUri != null) {
            Toast.makeText(context, "Saved \"$fileName\" to Downloads", Toast.LENGTH_LONG).show()
            shareUri(context, downloadUri, fileName)
        } else {
            // Pre-API 29 without storage permission: still let the user share a copy rather than blocking them.
            Toast.makeText(context, "Couldn't save to Downloads, sharing a temporary copy instead", Toast.LENGTH_LONG).show()
            val file = File(context.cacheDir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            shareUri(context, uri, fileName)
        }
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsViaMediaStore(context, fileName, content)
        } else {
            saveToDownloadsLegacy(context, fileName, content)
        }
    }

    private fun saveToDownloadsViaMediaStore(context: Context, fileName: String, content: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IOException("Unable to open output stream for $uri")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun saveToDownloadsLegacy(context: Context, fileName: String, content: String): Uri? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.writeText(content)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: IOException) {
            null
        }
    }

    private fun shareUri(context: Context, uri: Uri, fileName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    }
}
