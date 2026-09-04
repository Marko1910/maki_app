package com.example.maki.data

import android.content.Context
import java.io.File

/**
 * Local training-data collector for the waste detector. Each labelled capture is
 * filed under `filesDir/dataset/<label>/` and appended to `dataset/manifest.jsonl`
 * — raw material to export and train YOLO offline (Roboflow/Colab) later, then
 * ship the model back into the app.
 *
 * ponytail: local-only for now; a later step uploads the dataset to Supabase
 * Storage / Roboflow once a bucket exists.
 */
object TrainingDataStore {
    fun save(context: Context, source: File, label: String): File {
        val dir = File(context.filesDir, "dataset/$label").apply { mkdirs() }
        val dest = File(dir, source.name)
        source.copyTo(dest, overwrite = true)
        source.delete()
        File(context.filesDir, "dataset/manifest.jsonl").appendText(
            """{"file":"dataset/$label/${dest.name}","label":"$label","ts":${System.currentTimeMillis()}}""" + "\n"
        )
        return dest
    }

    /** How many captures collected so far (for a quick progress read). */
    fun count(context: Context): Int =
        File(context.filesDir, "dataset/manifest.jsonl").let { if (it.exists()) it.readLines().count { l -> l.isNotBlank() } else 0 }
}
