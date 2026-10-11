package tn.loukious.superproxyunlocker

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class CustomerInfoPatcherTest {
    private fun customer(entitlements: Map<String, Any?> = emptyMap()) = mapOf<String, Any?>(
        "entitlements" to entitlements,
        "activeSubscriptions" to emptyList<String>(),
        "allPurchasedProductIdentifiers" to emptyList<String>(),
        "originalAppUserId" to "test-user", "requestDate" to "2026-10-11T00:00:00Z",
        "managementURL" to null, "nonSubscriptionTransactions" to emptyList<Any>(),
    )

    @Test fun unrelatedAndIncompletePayloadsAreIgnored() {
        listOf(null, true, "text", mapOf("isActive" to false),
            mapOf("entitlements" to emptyMap<String, Any>())).forEach {
            assertFalse(CustomerInfoPatcher.isCustomerInfo(it))
        }
        assertTrue(CustomerInfoPatcher.isCustomerInfo(customer()))
    }

    @Test fun immutableEmptyCustomerGetsCompleteEntitlementsWithoutMutatingOriginal() {
        val original = Collections.unmodifiableMap(customer())
        val result = CustomerInfoPatcher.patch(original)
        val entitlements = result["entitlements"] as Map<*, *>
        val active = entitlements["active"] as Map<*, *>
        val pro = active["pro"] as Map<*, *>
        assertEquals(true, pro["isActive"])
        assertEquals(CustomerInfoPatcher.EXPIRATION, pro["expirationDate"])
        assertEquals(CustomerInfoPatcher.EXPIRATION_MILLIS, pro["expirationDateMillis"])
        assertEquals(entitlements["all"], active)
        assertEquals("test-user", result["originalAppUserId"])
        assertEquals(original["requestDate"], result["requestDate"])
        assertTrue((original["entitlements"] as Map<*, *>).isEmpty())
        assertTrue((original["activeSubscriptions"] as List<*>).isEmpty())
        assertTrue((result["activeSubscriptions"] as List<*>).contains("pro"))
    }

    @Test fun existingRenamedEntitlementAndProductArePreservedAndActivated() {
        val originalEntitlement = mapOf<String, Any?>(
            "identifier" to "new-pro-tier", "isActive" to false,
            "productIdentifier" to "superproxy.yearly", "latestPurchaseDate" to "2025-02-01T00:00:00Z",
        )
        val input = customer(mapOf("all" to mapOf("new-pro-tier" to originalEntitlement), "active" to emptyMap<String, Any>()))
        val result = CustomerInfoPatcher.patch(input)
        val active = (result["entitlements"] as Map<*, *>)["active"] as Map<*, *>
        val updated = active["new-pro-tier"] as Map<*, *>
        assertEquals(true, updated["isActive"])
        assertEquals("superproxy.yearly", updated["productIdentifier"])
        assertEquals(originalEntitlement["latestPurchaseDate"], updated["latestPurchaseDate"])
        assertEquals(false, originalEntitlement["isActive"])
        assertTrue((result["activeSubscriptions"] as List<*>).contains("superproxy.yearly"))
    }

    @Test fun applyingPatchTwiceIsStable() {
        val once = CustomerInfoPatcher.patch(customer())
        assertEquals(once, CustomerInfoPatcher.patch(once))
    }
}
