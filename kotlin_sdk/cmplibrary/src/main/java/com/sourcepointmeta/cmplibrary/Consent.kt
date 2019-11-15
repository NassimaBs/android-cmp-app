package com.sourcepointmeta.cmplibrary

import org.json.JSONException
import org.json.JSONObject

/**
 * Simple encapsulating class for consents.
 */
abstract class Consent constructor(var id: String, var name: String, var type: String) {

    override fun equals(otherConsent: Any?): Boolean {
        if (javaClass != otherConsent?.javaClass) {
            return false
        }
        return super.equals((otherConsent as Consent).id)
    }

    internal fun toJSON(): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", id).put("name", name).put("type", type)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return json
    }

}