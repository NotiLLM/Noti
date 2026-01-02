package org.muilab.notigpt.util.app

import android.content.Context
import android.util.Log
import org.muilab.notigpt.R
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_UNKNOWN

// App category mapping cache
private var appCategoryMap: Map<String, String>? = null

fun loadAppCategoryMapping(context: Context): Map<String, String> {
    if (appCategoryMap == null) {
        appCategoryMap = try {
            val resources = context.resources
            val xmlResourceParser = resources.getXml(R.xml.app_category_map)
            val mapping = mutableMapOf<String, String>()

            var eventType = xmlResourceParser.eventType
            while (eventType != android.content.res.XmlResourceParser.END_DOCUMENT) {
                if (eventType == android.content.res.XmlResourceParser.START_TAG &&
                    xmlResourceParser.name == "string"
                ) {
                    val appName = xmlResourceParser.getAttributeValue(null, "name")
                    xmlResourceParser.next()
                    if (xmlResourceParser.eventType == android.content.res.XmlResourceParser.TEXT) {
                        val category = xmlResourceParser.text
                        if (appName != null && category != null) {
                            mapping[appName] = category
                        }
                    }
                }
                eventType = xmlResourceParser.next()
            }
            xmlResourceParser.close()
            mapping.toMap()
        } catch (e: Exception) {
            Log.e("AppCategoryMapping", "Failed to load app category mapping", e)
            emptyMap()
        }
    }
    return appCategoryMap!!
}

fun getAppCategoryByAppName(context: Context, appName: String): String {
    val mapping = loadAppCategoryMapping(context)
    return mapping[appName] ?: APP_CATEGORY_UNKNOWN
}

