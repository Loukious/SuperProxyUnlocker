package tn.loukious.superproxyunlocker

/** The RevenueCat Flutter schema survives R8 renaming of SDK classes and members. */
object CustomerInfoPatcher {
    const val EXPIRATION_MILLIS = 4102444800000L
    const val EXPIRATION = "2100-01-01T00:00:00Z"
    private const val PURCHASE = "2020-01-01T00:00:00Z"
    private const val PURCHASE_MILLIS = 1577836800000L
    private val fallbackIds = listOf("pro", "premium", "vip", "lifetime")

    fun isCustomerInfo(value: Any?): Boolean = value is Map<*, *> &&
        value["entitlements"] is Map<*, *> &&
        value["activeSubscriptions"] is List<*> &&
        value["allPurchasedProductIdentifiers"] is List<*> &&
        value["originalAppUserId"] is String &&
        value["requestDate"] is String

    /** Copies maps before editing, including immutable SDK maps; never mutates SDK caches. */
    fun patch(value: Map<*, *>): Map<String, Any?> {
        require(isCustomerInfo(value)) { "Unsupported customer-info schema" }
        val result = copy(value)
        val container = copy(value["entitlements"])
        val all = copy(container["all"])
        val active = copy(container["active"])
        val ids = linkedSetOf<String>().apply {
            addAll(all.keys)
            addAll(active.keys)
            addAll(fallbackIds)
        }
        for (id in ids) {
            val entitlement = copy(all[id] ?: active[id])
            entitlement.putAll(mapOf(
                "identifier" to id, "isActive" to true, "willRenew" to true,
                "periodType" to "NORMAL", "expirationDate" to EXPIRATION,
                "expirationDateMillis" to EXPIRATION_MILLIS, "isSandbox" to false,
                "unsubscribeDetectedAt" to null, "unsubscribeDetectedAtMillis" to null,
                "billingIssueDetectedAt" to null, "billingIssueDetectedAtMillis" to null,
                "ownershipType" to "PURCHASED", "verification" to "VERIFIED_ON_DEVICE",
            ))
            entitlement.putIfAbsent("productIdentifier", id)
            entitlement.putIfAbsent("productPlanIdentifier", null)
            entitlement.putIfAbsent("store", "PLAY_STORE")
            entitlement.putIfAbsent("latestPurchaseDate", PURCHASE)
            entitlement.putIfAbsent("latestPurchaseDateMillis", PURCHASE_MILLIS)
            entitlement.putIfAbsent("originalPurchaseDate", PURCHASE)
            entitlement.putIfAbsent("originalPurchaseDateMillis", PURCHASE_MILLIS)
            all[id] = entitlement
        }
        container["all"] = all
        container["active"] = LinkedHashMap(all)
        container["verification"] = "VERIFIED_ON_DEVICE"
        result["entitlements"] = container
        result["latestExpirationDate"] = EXPIRATION
        result["latestExpirationDateMillis"] = EXPIRATION_MILLIS

        val products = linkedSetOf<String>().apply {
            (value["activeSubscriptions"] as List<*>).filterIsInstance<String>().forEach(::add)
            (value["allPurchasedProductIdentifiers"] as List<*>).filterIsInstance<String>().forEach(::add)
            all.values.mapNotNull { (it as? Map<*, *>)?.get("productIdentifier") as? String }.forEach(::add)
        }
        result["activeSubscriptions"] = products.toList()
        result["allPurchasedProductIdentifiers"] = products.toList()
        result["allExpirationDates"] = copy(value["allExpirationDates"]).apply {
            keys.toList().forEach { put(it, EXPIRATION) }
            products.forEach { put(it, EXPIRATION) }
        }
        result["allExpirationDatesMillis"] = copy(value["allExpirationDatesMillis"]).apply {
            keys.toList().forEach { put(it, EXPIRATION_MILLIS) }
            products.forEach { put(it, EXPIRATION_MILLIS) }
        }
        result["allPurchaseDates"] = copy(value["allPurchaseDates"]).apply {
            products.forEach { putIfAbsent(it, PURCHASE) }
        }
        result["allPurchaseDatesMillis"] = copy(value["allPurchaseDatesMillis"]).apply {
            products.forEach { putIfAbsent(it, PURCHASE_MILLIS) }
        }
        result["subscriptionsByProductIdentifier"] = copy(value["subscriptionsByProductIdentifier"]).apply {
            (keys + products).toSet().forEach { id ->
                put(id, copy(get(id)).apply {
                    putAll(mapOf(
                        "productIdentifier" to id, "expiresDate" to EXPIRATION,
                        "gracePeriodExpiresDate" to EXPIRATION, "isActive" to true,
                        "willRenew" to true, "isSandbox" to false,
                        "unsubscribeDetectedAt" to null, "billingIssuesDetectedAt" to null,
                        "refundedAt" to null,
                    ))
                    putIfAbsent("purchaseDate", PURCHASE)
                    putIfAbsent("originalPurchaseDate", PURCHASE)
                    putIfAbsent("store", "PLAY_STORE")
                    putIfAbsent("ownershipType", "PURCHASED")
                    putIfAbsent("periodType", "NORMAL")
                    putIfAbsent("storeTransactionId", "superproxy-unlocker")
                })
            }
        }
        return result
    }

    private fun copy(value: Any?): LinkedHashMap<String, Any?> = LinkedHashMap<String, Any?>().apply {
        if (value is Map<*, *>) value.forEach { (key, item) -> if (key is String) put(key, item) }
    }
}
