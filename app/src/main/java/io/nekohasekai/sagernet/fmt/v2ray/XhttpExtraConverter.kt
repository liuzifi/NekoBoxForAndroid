package io.nekohasekai.sagernet.fmt.v2ray

import org.json.JSONObject

object XhttpExtraConverter {

    // Xray SplitHTTPConfig (camelCase) <-> sing-box V2RayXHTTPBaseOptions (snake_case)
    private val FIELD_MAPPINGS = arrayOf(
        "headers" to "headers",
        "xPaddingBytes" to "x_padding_bytes",
        "noGRPCHeader" to "no_grpc_header",
        "noSSEHeader" to "no_sse_header",
        "scMaxEachPostBytes" to "sc_max_each_post_bytes",
        "scMinPostsIntervalMs" to "sc_min_posts_interval_ms",
        "scMaxBufferedPosts" to "sc_max_buffered_posts",
        "scStreamUpServerSecs" to "sc_stream_up_server_secs",
        "serverMaxHeaderBytes" to "server_max_header_bytes",
        "xPaddingObfsMode" to "x_padding_obfs_mode",
        "xPaddingKey" to "x_padding_key",
        "xPaddingHeader" to "x_padding_header",
        "xPaddingPlacement" to "x_padding_placement",
        "xPaddingMethod" to "x_padding_method",
        "uplinkHTTPMethod" to "uplink_http_method",
        "sessionIDPlacement" to "session_placement",
        "sessionIDKey" to "session_key",
        "sessionIDTable" to "session_id_table",
        "sessionIDLength" to "session_id_length",
        "seqPlacement" to "seq_placement",
        "seqKey" to "seq_key",
        "uplinkDataPlacement" to "uplink_data_placement",
        "uplinkDataKey" to "uplink_data_key",
        "uplinkChunkSize" to "uplink_chunk_size",
    )

    private val XMUX_MAPPINGS = arrayOf(
        "maxConcurrency" to "max_concurrency",
        "maxConnections" to "max_connections",
        "cMaxReuseTimes" to "c_max_reuse_times",
        "hMaxRequestTimes" to "h_max_request_times",
        "hMaxReusableSecs" to "h_max_reusable_secs",
        "hKeepAlivePeriod" to "h_keep_alive_period",
    )

    fun xrayToSingBox(xrayExtra: String): String {
        if (xrayExtra.isBlank()) return ""
        return try {
            val xray = JSONObject(xrayExtra)
            if (isSingBoxFormat(xray)) return xrayExtra
            val singBox = JSONObject()

            // Xray: if "extra" is present it replaces the whole settings
            // object (except host/path/mode), so convert from "extra" only
            val effectiveTop = xray.optJSONObject("extra") ?: xray
            convertFields(effectiveTop, singBox, FIELD_MAPPINGS)
            if (effectiveTop.has("xmux")) {
                convertXmux(effectiveTop, singBox)
            }

            if (xray.has("downloadSettings")) {
                val xrayDown = xray.getJSONObject("downloadSettings")
                val singBoxDown = JSONObject()

                // Xray also accepts the legacy key "splithttpSettings"
                (xrayDown.optJSONObject("xhttpSettings")
                    ?: xrayDown.optJSONObject("splithttpSettings"))?.let { xhttpSettings ->
                    convertField(xhttpSettings, singBoxDown, "mode", "mode")
                    convertField(xhttpSettings, singBoxDown, "host", "host")
                    convertField(xhttpSettings, singBoxDown, "path", "path")
                    // Xray: if "extra" is present it replaces the whole settings
                    // object (except host/path/mode), so convert from "extra"
                    // only; otherwise fall back to the top-level fields
                    val effective = xhttpSettings.optJSONObject("extra") ?: xhttpSettings
                    convertFields(effective, singBoxDown, FIELD_MAPPINGS)
                    if (effective.has("xmux")) {
                        convertXmux(effective, singBoxDown)
                    }
                }
                convertField(xrayDown, singBoxDown, "address", "server")
                convertField(xrayDown, singBoxDown, "port", "server_port")

                val securityName = xrayDown.optString("security")
                // only tls/reality enable TLS; "none"/empty must not create a
                // bogus tls object (previously {"enabled": true} leaked out)
                if (securityName == "tls" || securityName == "reality") {
                    val tls = JSONObject().apply { put("enabled", true) }

                    when (securityName) {
                        "tls" -> {
                            xrayDown.optJSONObject("tlsSettings")?.let { tlsSettings ->
                                convertField(tlsSettings, tls, "serverName", "server_name")
                                convertField(tlsSettings, tls, "alpn", "alpn")
                                convertField(tlsSettings, tls, "allowInsecure", "insecure")
                                tlsSettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                        "reality" -> {
                            xrayDown.optJSONObject("realitySettings")?.let { realitySettings ->
                                convertField(realitySettings, tls, "serverName", "server_name")
                                val reality = JSONObject().apply {
                                    put("enabled", true)
                                    convertField(realitySettings, this, "publicKey", "public_key")
                                    convertField(realitySettings, this, "shortId", "short_id")
                                }
                                tls.put("reality", reality)
                                realitySettings.optString("fingerprint")?.let { fp ->
                                    if (fp.isNotBlank()) {
                                        val utls = JSONObject().apply {
                                            put("enabled", true)
                                            put("fingerprint", fp)
                                        }
                                        tls.put("utls", utls)
                                    }
                                }
                            }
                        }
                    }
                    singBoxDown.put("tls", tls)
                }

                if (singBoxDown.length() > 0) singBox.put("download", singBoxDown)
            }

            singBox.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            xrayExtra
        }
    }

    fun singBoxToXray(singBoxExtra: String): String {
        if (singBoxExtra.isBlank()) return ""
        return try {
            val singBox = JSONObject(singBoxExtra)
            if (isXrayFormat(singBox)) return singBoxExtra
            val xray = JSONObject()

            convertFieldsReverse(singBox, xray, FIELD_MAPPINGS)
            if (singBox.has("xmux")) {
                convertXmuxReverse(singBox, xray)
            }

            if (singBox.has("download")) {
                val singBoxDown = singBox.getJSONObject("download")
                val xrayDown = JSONObject()

                convertField(singBoxDown, xrayDown, "server", "address")
                convertField(singBoxDown, xrayDown, "server_port", "port")
                xrayDown.put("network", "xhttp")

                if (singBoxDown.has("tls")) {
                    val tls = singBoxDown.getJSONObject("tls")

                    if (tls.has("reality") && tls.getJSONObject("reality").optBoolean("enabled", false)) {
                        xrayDown.put("security", "reality")
                        val reality = tls.getJSONObject("reality")
                        val realitySettings = JSONObject()
                        convertField(tls, realitySettings, "server_name", "serverName")
                        convertField(reality, realitySettings, "public_key", "publicKey")
                        convertField(reality, realitySettings, "short_id", "shortId")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, realitySettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("realitySettings", realitySettings)
                    } else {
                        xrayDown.put("security", "tls")
                        val tlsSettings = JSONObject()
                        convertField(tls, tlsSettings, "server_name", "serverName")
                        convertField(tls, tlsSettings, "alpn", "alpn")
                        convertField(tls, tlsSettings, "insecure", "allowInsecure")
                        if (tls.has("utls")) {
                            val utls = tls.getJSONObject("utls")
                            convertField(utls, tlsSettings, "fingerprint", "fingerprint")
                        }
                        xrayDown.put("tlsSettings", tlsSettings)
                    }
                }

                // fields go to xhttpSettings top level: in Xray, "extra" replaces the
                // whole settings object, so wrapping there would drop the rest
                val xhttpSettings = JSONObject()
                convertField(singBoxDown, xhttpSettings, "mode", "mode")
                convertField(singBoxDown, xhttpSettings, "host", "host")
                convertField(singBoxDown, xhttpSettings, "path", "path")
                convertFieldsReverse(singBoxDown, xhttpSettings, FIELD_MAPPINGS)
                if (singBoxDown.has("xmux")) {
                    convertXmuxReverse(singBoxDown, xhttpSettings)
                }
                xrayDown.put("xhttpSettings", xhttpSettings)

                if (xrayDown.length() > 0) xray.put("downloadSettings", xrayDown)
            }

            xray.toString(2).replace("\\/", "/")
        } catch (e: Exception) {
            e.printStackTrace()
            singBoxExtra
        }
    }

    // Any sing-box style (snake_case) key means the extra is already in sing-box format.
    // Checking every mapped key (not just a handful) avoids silently wiping extras such as
    // {"no_grpc_header": true} or {"x_padding_obfs_mode": true}, which would otherwise be
    // mistaken for Xray format and converted into an empty object.
    private fun isSingBoxFormat(json: JSONObject): Boolean {
        if (json.has("download")) return true
        if (FIELD_MAPPINGS.any { (xrayKey, singBoxKey) ->
                singBoxKey != xrayKey && json.has(singBoxKey)
            }) return true
        json.optJSONObject("xmux")?.let { xmux ->
            if (XMUX_MAPPINGS.any { (_, singBoxKey) -> xmux.has(singBoxKey) }) return true
        }
        return false
    }

    private fun isXrayFormat(json: JSONObject): Boolean {
        if (json.has("downloadSettings")) return true
        if (FIELD_MAPPINGS.any { (xrayKey, singBoxKey) ->
                xrayKey != singBoxKey && json.has(xrayKey)
            }) return true
        json.optJSONObject("xmux")?.let { xmux ->
            if (XMUX_MAPPINGS.any { (xrayKey, _) -> xmux.has(xrayKey) }) return true
        }
        return false
    }

    /**
     * Normalize a manually entered "extra" JSON to sing-box format.
     *
     * Users may paste Xray-style (camelCase) JSON into the xhttpExtra field;
     * without this, the snake_case whitelist merge in
     * buildSingBoxOutboundStreamSettings would silently drop every field.
     */
    fun ensureSingBoxFormat(extra: String): String {
        if (extra.isBlank()) return extra
        return try {
            val json = JSONObject(extra)
            if (isXrayFormat(json)) xrayToSingBox(extra) else extra
        } catch (e: Exception) {
            e.printStackTrace()
            extra
        }
    }

    private fun convertField(from: JSONObject, to: JSONObject, fromKey: String, toKey: String) {
        if (from.has(fromKey)) {
            to.put(toKey, from.get(fromKey))
        }
    }

    private fun convertFields(from: JSONObject, to: JSONObject, mappings: Array<Pair<String, String>>) {
        for ((fromKey, toKey) in mappings) {
            convertField(from, to, fromKey, toKey)
        }
    }

    private fun convertFieldsReverse(from: JSONObject, to: JSONObject, mappings: Array<Pair<String, String>>) {
        for ((fromKey, toKey) in mappings) {
            convertField(from, to, toKey, fromKey)
        }
    }

    private fun convertXmux(from: JSONObject, to: JSONObject) {
        val xrayXmux = from.getJSONObject("xmux")
        val singBoxXmux = JSONObject()
        convertFields(xrayXmux, singBoxXmux, XMUX_MAPPINGS)
        if (singBoxXmux.length() > 0) to.put("xmux", singBoxXmux)
    }

    private fun convertXmuxReverse(from: JSONObject, to: JSONObject) {
        val singBoxXmux = from.getJSONObject("xmux")
        val xrayXmux = JSONObject()
        convertFieldsReverse(singBoxXmux, xrayXmux, XMUX_MAPPINGS)
        if (xrayXmux.length() > 0) to.put("xmux", xrayXmux)
    }
}
