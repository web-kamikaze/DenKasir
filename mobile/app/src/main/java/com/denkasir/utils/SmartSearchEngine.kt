package com.denkasir.utils

import com.denkasir.data.local.entity.ProductEntity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SmartSearchEngine {

    class ProductIndex(
        val product: ProductEntity,
        val id: String,
        val barcode: String,
        val barcodeClean: String,
        val rawSearchText: String,
        val tokens: Array<String>
    )

    private val indexCache = ConcurrentHashMap<String, ProductIndex>()

    // Indonesian Colloquial / Common Typo / Phonetic Canonical Mapping
    private val CANONICAL_MAP = mapOf(
        "mei" to "mie",
        "mi" to "mie",
        "mye" to "mie",
        "bakmi" to "mie",
        "indomi" to "indomie",
        "indomy" to "indomie",
        "sedap" to "sedaap",
        "sdp" to "sedaap",
        "cabe" to "cabai",
        "cb" to "cabai",
        "lombok" to "cabai",
        "telor" to "telur",
        "tlur" to "telur",
        "minak" to "minyak",
        "minya" to "minyak",
        "myk" to "minyak",
        "bimoli" to "minyak",
        "filma" to "minyak",
        "sania" to "minyak",
        "sunco" to "minyak",
        "coklat" to "cokelat",
        "coklate" to "cokelat",
        "choc" to "cokelat",
        "roko" to "rokok",
        "rkk" to "rokok",
        "surya" to "rokok",
        "sampoerna" to "rokok",
        "akua" to "aqua",
        "aqwa" to "aqua",
        "aer" to "air",
        "aerputih" to "air",
        "mineral" to "air",
        "leminerale" to "air",
        "brambang" to "bawang",
        "bwg" to "bawang",
        "bamer" to "bawang",
        "baput" to "bawang",
        "tehbotol" to "teh",
        "sosro" to "teh",
        "bengbeng" to "beng",
        "saos" to "saus",
        "kecap" to "kecap",
        "kcp" to "kecap",
        "bango" to "kecap",
        "sampo" to "shampo",
        "shampoo" to "shampo",
        "odol" to "pepsodent",
        "deterjen" to "detergen",
        "rinso" to "detergen",
        "daia" to "detergen",
        "snack" to "snak",
        "chiki" to "snak",
        "taro" to "snak",
        "indomilk" to "susu",
        "dancow" to "susu",
        "frisian" to "susu",
        "bendera" to "susu",
        "bearbrand" to "susu",
        "beras" to "beras",
        "roti" to "roti",
        "sariroti" to "roti"
    )

    fun normalizeToken(token: String): String {
        val clean = token.lowercase(Locale.ROOT).trim()
        return CANONICAL_MAP[clean] ?: clean
    }

    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val len = text.length
        val result = ArrayList<String>()
        val sb = java.lang.StringBuilder()
        for (i in 0 until len) {
            val ch = text[i]
            if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
            } else {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    fun getOrCreateIndex(product: ProductEntity): ProductIndex {
        return indexCache.getOrPut(product.id) {
            val barcodeStr = product.barcode?.trim() ?: ""
            val barcodeClean = barcodeStr.lowercase(Locale.ROOT)
            val nameTokens = tokenize(product.name).map { normalizeToken(it) }
            val catTokens = tokenize(product.category).map { normalizeToken(it) }
            val allTokens = (nameTokens + catTokens).distinct().toTypedArray()
            val raw = "${product.name} ${product.category} $barcodeStr".lowercase(Locale.ROOT)
            ProductIndex(product, product.id, barcodeStr, barcodeClean, raw, allTokens)
        }
    }

    fun preIndex(products: List<ProductEntity>) {
        for (product in products) {
            getOrCreateIndex(product)
        }
    }

    fun clearIndex() {
        indexCache.clear()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        val lenDiff = kotlin.math.abs(len1 - len2)
        if (lenDiff > 2) return lenDiff

        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            val c1 = s1[i - 1]
            for (j in 1..len2) {
                val cost = if (c1 == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    fun tokenMatches(queryToken: String, candidateTokens: List<String>, rawCandidateText: String): Boolean {
        val normQuery = normalizeToken(queryToken)

        // 1. Direct containment in raw candidate text (fastest check!)
        if (rawCandidateText.contains(queryToken) || rawCandidateText.contains(normQuery)) {
            return true
        }

        for (cToken in candidateTokens) {
            val normCand = normalizeToken(cToken)

            // 2. Exact or canonical match
            if (cToken == queryToken || normCand == normQuery || normCand == queryToken || cToken == normQuery) {
                return true
            }

            // 3. Prefix match
            if (cToken.startsWith(queryToken) || normCand.startsWith(normQuery)) {
                return true
            }

            // 4. Substring match
            if (cToken.contains(queryToken) || normCand.contains(normQuery)) {
                return true
            }

            // 5. Reverse contains
            if (queryToken.contains(cToken) && cToken.length >= 3) {
                return true
            }

            // 6. Typo tolerance via Levenshtein distance
            val minLen = minOf(normQuery.length, normCand.length)
            if (minLen >= 3) {
                val maxAllowedDist = if (minLen >= 6) 2 else 1
                if (kotlin.math.abs(normQuery.length - normCand.length) <= maxAllowedDist) {
                    val dist = levenshteinDistance(normQuery, normCand)
                    if (dist <= maxAllowedDist) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun matches(query: String, vararg candidateFields: String?): Boolean {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return true

        val combinedRaw = candidateFields.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        val candidateTokens = tokenize(combinedRaw)

        return qTokens.all { qToken ->
            tokenMatches(qToken, candidateTokens, combinedRaw)
        }
    }

    fun calculateScore(query: String, product: ProductEntity): Int {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return 0

        val rawQuery = query.lowercase(Locale.ROOT).trim()
        val item = getOrCreateIndex(product)
        var score = 0

        if (item.rawSearchText == rawQuery) {
            score += 1000
        } else if (item.rawSearchText.startsWith(rawQuery)) {
            score += 600
        } else if (item.rawSearchText.contains(rawQuery)) {
            score += 400
        }

        if (item.barcode.isNotEmpty()) {
            if (item.barcode.equals(rawQuery, ignoreCase = true)) {
                score += 1200
            } else if (item.barcode.contains(rawQuery, ignoreCase = true)) {
                score += 500
            }
        }

        val normQTokens = qTokens.map { normalizeToken(it) }
        for (normQ in normQTokens) {
            for (c in item.tokens) {
                if (c == normQ) {
                    score += 100
                    break
                } else if (c.startsWith(normQ)) {
                    score += 70
                    break
                } else if (c.contains(normQ)) {
                    score += 50
                    break
                } else if (minOf(normQ.length, c.length) >= 3) {
                    val maxDist = if (normQ.length >= 6) 2 else 1
                    if (kotlin.math.abs(normQ.length - c.length) <= maxDist) {
                        if (levenshteinDistance(normQ, c) <= maxDist) {
                            score += 30
                            break
                        }
                    }
                }
            }
        }

        return score
    }

    fun filterProducts(
        query: String,
        products: List<ProductEntity>,
        maxResults: Int = 100
    ): List<ProductEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return products

        val rawQuery = trimmed.lowercase(Locale.ROOT)
        val qTokens = tokenize(trimmed)
        if (qTokens.isEmpty()) return products
        val normQTokens = qTokens.map { normalizeToken(it) }

        val matches = ArrayList<Pair<ProductEntity, Int>>()

        for (product in products) {
            val item = getOrCreateIndex(product)
            var isMatch = true
            var score = 0

            // 1. Barcode exact/prefix match (highest priority for kasir)
            if (item.barcodeClean.isNotEmpty()) {
                if (item.barcodeClean == rawQuery) {
                    matches.add(Pair(product, 3000))
                    continue
                } else if (item.barcodeClean.startsWith(rawQuery)) {
                    score += 1500
                }
            }

            // 2. Direct exact or prefix on full raw text
            if (item.rawSearchText.startsWith(rawQuery)) {
                score += 1200
            } else if (item.rawSearchText.contains(rawQuery)) {
                score += 800
            }

            // 3. Multi-token evaluation using pre-indexed tokens
            for (i in qTokens.indices) {
                val q = qTokens[i]
                val normQ = normQTokens[i]

                // Fast check: is token directly in rawSearchText?
                if (item.rawSearchText.contains(q) || item.rawSearchText.contains(normQ)) {
                    score += 200
                    continue
                }

                // Check pre-indexed tokens
                var tokenFound = false
                for (cToken in item.tokens) {
                    if (cToken == q || cToken == normQ || cToken.startsWith(q) || cToken.startsWith(normQ)) {
                        score += 150
                        tokenFound = true
                        break
                    }
                    if (cToken.contains(q) || cToken.contains(normQ)) {
                        score += 100
                        tokenFound = true
                        break
                    }
                    if (q.contains(cToken) && cToken.length >= 3) {
                        score += 80
                        tokenFound = true
                        break
                    }
                    if (normQ.length >= 3 && cToken.length >= 3) {
                        val maxDist = if (normQ.length >= 6) 2 else 1
                        if (kotlin.math.abs(normQ.length - cToken.length) <= maxDist) {
                            if (levenshteinDistance(normQ, cToken) <= maxDist) {
                                score += 50
                                tokenFound = true
                                break
                            }
                        }
                    }
                }

                if (!tokenFound) {
                    isMatch = false
                    break
                }
            }

            if (isMatch) {
                // In-order bonus
                if (qTokens.size > 1) {
                    var lastIdx = -1
                    var inOrder = true
                    for (q in qTokens) {
                        val idx = item.rawSearchText.indexOf(q, lastIdx + 1)
                        if (idx == -1) {
                            inOrder = false
                            break
                        }
                        lastIdx = idx
                    }
                    if (inOrder) score += 300
                }
                matches.add(Pair(product, score))
            }
        }

        matches.sortByDescending { it.second }
        return if (maxResults > 0 && matches.size > maxResults) {
            matches.take(maxResults).map { it.first }
        } else {
            matches.map { it.first }
        }
    }
}