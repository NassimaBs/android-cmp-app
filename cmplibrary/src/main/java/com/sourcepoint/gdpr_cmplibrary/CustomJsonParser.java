package com.sourcepoint.gdpr_cmplibrary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class CustomJsonParser {

    static boolean getBoolean(String key, JSONObject j) throws ConsentLibException {
        try {
            return j.getBoolean(key);
        } catch (JSONException e) {
            throw new ConsentLibException(e, key + " missing from JSONObject");
        }
    }

    static int getInt(String key, JSONObject j) throws ConsentLibException {
        try {
            return j.getInt(key);
        } catch (JSONException e) {
            throw new ConsentLibException(e, key + " missing from JSONObject");
        }
    }

    static String getString(String key, JSONObject j) throws ConsentLibException {
        try {
            return j.getString(key);
        } catch (JSONException e) {
            throw new ConsentLibException(e, key + " missing from JSONObject");
        }
    }

    static JSONObject getJson(String key, JSONObject j) throws ConsentLibException {
        try {
            return j.getJSONObject(key);
        } catch (JSONException e) {
            throw new ConsentLibException(e, key + " missing from JSONObject");
        }
    }

    static JSONObject getJson(String strJson) throws ConsentLibException {
        try {
            return new JSONObject(strJson);
        } catch (JSONException e) {
            throw new ConsentLibException(e, "Not possible to convert String to Json");
        }
    }

    static JSONArray getJArray(String key, JSONObject j) throws ConsentLibException {
        try {
            return j.getJSONArray(key);
        } catch (JSONException e) {
            throw new ConsentLibException(e, key + " missing from JSONObject");
        }
    }

    static JSONObject getJson(int i, JSONArray jArray) throws ConsentLibException {
        try {
            return jArray.getJSONObject(i);
        } catch (JSONException e) {
            throw new ConsentLibException(e, "Error trying to get action obj from JSONObject");
        }
    }

    static String getString(int i, JSONArray jArray) throws ConsentLibException {
        try {
            return jArray.getString(i);
        } catch (JSONException e) {
            throw new ConsentLibException(e, "Error trying to get action obj from JSONObject");
        }
    }

    static HashMap<String, String> getHashMap(JSONObject jCustomFields) throws ConsentLibException {
        HashMap<String, String> hMap = new HashMap<>();
        JSONArray names = jCustomFields.names();
        if (names != null){
            for(int i = 0; i < names.length(); i++) {
                String name = getString(i, names);
                hMap.put(name, getString(name, jCustomFields));
            }
        }
        return hMap;
    }
}
