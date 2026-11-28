package com.aitronics.assistant

import android.net.Uri
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

object TopicLookup {
    fun topic(q: String) = Regex("""^(?:what (?:is|are)|who (?:is|was|were)|tell me about|define|explain)\s+(.+)""",
        RegexOption.IGNORE_CASE).matchEntire(q.trim().trimEnd('?'))?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..250 }

    fun fetch(topic: String): String {
        val uri=Uri.parse("https://en.wikipedia.org/w/api.php").buildUpon()
            .appendQueryParameter("action","query").appendQueryParameter("format","json")
            .appendQueryParameter("formatversion","2").appendQueryParameter("generator","search")
            .appendQueryParameter("gsrsearch",topic).appendQueryParameter("gsrlimit","1")
            .appendQueryParameter("prop","extracts|info").appendQueryParameter("explaintext","1")
            .appendQueryParameter("exintro","1").appendQueryParameter("exsentences","2")
            .appendQueryParameter("inprop","url").build()
        val connection=URL(uri.toString()).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout=10000; connection.readTimeout=15000
            connection.setRequestProperty("User-Agent","AITRONICS/0.8 (https://github.com/yashrajnetke3/aitronics)")
            val json=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val page=json.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            val extract=page?.optString("extract").orEmpty().trim()
            if(page==null || extract.isBlank()) "Wikipedia didn’t return an overview. Try a more specific topic."
            else "Wikipedia · ${page.getString("title")}\n\n$extract\n\n${page.getString("fullurl")}"
        } finally { connection.disconnect() }
    }
}
