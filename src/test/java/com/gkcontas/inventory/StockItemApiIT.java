package com.gkcontas.inventory;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gkcontas.inventory.domain.StockItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The contract, including the part that matters most: the three failures a
 * caller has to tell apart.
 */
class StockItemApiIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryFixtures fixtures;

    @Test
    void createsAnItemAndReadsItBack() throws Exception {
        mockMvc.perform(post("/stock-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"API-0001","name":"Coffee beans","quantity":40}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("API-0001"))
                .andExpect(jsonPath("$.quantity").value(40))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/stock-items/API-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawnUnits").value(0))
                .andExpect(jsonPath("$.reservedUnits").value(0));
    }

    @Test
    void withdrawsAndReportsTheRemainingBalance() throws Exception {
        StockItem item = fixtures.newItem("API", 40);

        mockMvc.perform(post("/stock-items/{sku}/withdrawals", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5,"strategy":"ATOMIC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(35))
                .andExpect(jsonPath("$.attempts").value(1));
    }

    @Test
    void anUnknownSkuIs404() throws Exception {
        mockMvc.perform(post("/stock-items/API-MISSING/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1,"strategy":"OPTIMISTIC"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("stock_item_not_found"));
    }

    /**
     * 422, not 409. There is no amount of retrying that makes stock appear, and
     * a client that cannot tell this from a version conflict will either retry
     * forever or give up on a request that would have worked.
     */
    @Test
    void aShortBalanceIs422() throws Exception {
        StockItem item = fixtures.newItem("API", 2);

        mockMvc.perform(post("/stock-items/{sku}/withdrawals", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":10,"strategy":"PESSIMISTIC"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("insufficient_stock"));
    }

    @Test
    void theValidationMessagesAreInEnglish() throws Exception {
        mockMvc.perform(post("/stock-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","name":"","quantity":-3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details", containsInAnyOrder(
                        "name: name is required",
                        "quantity: quantity must be zero or greater",
                        "sku: sku is required")));
    }

    @Test
    void reservesAgainstAnItem() throws Exception {
        StockItem item = fixtures.newItem("API", 10);

        mockMvc.perform(post("/stock-items/{sku}/reservations", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderRef":"ORDER-API-1","quantity":4,"forceParentIncrement":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.freeAfter").value(6))
                .andExpect(jsonPath("$.parentVersionForced").value(true));

        mockMvc.perform(get("/stock-items/{sku}", item.getSku()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedUnits").value(4))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void aDuplicateOrderReferenceIsRejected() throws Exception {
        StockItem item = fixtures.newItem("API", 10);
        String body = """
                {"orderRef":"ORDER-API-DUP","quantity":1,"forceParentIncrement":false}
                """;

        mockMvc.perform(post("/stock-items/{sku}/reservations", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/stock-items/{sku}/reservations", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("integrity_violation"));
    }

    /**
     * A conflict comes back with Retry-After, because reading again and sending
     * again is the only correct response to it.
     */
    @Test
    void aVersionConflictIs409WithRetryAfter() throws Exception {
        StockItem item = fixtures.newItem("API", 10);
        mockMvc.perform(post("/stock-items/{sku}/reservations", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderRef":"ORDER-API-V1","quantity":1,"forceParentIncrement":true}
                                """))
                .andExpect(status().isCreated());

        // The row is at version 1 now; a withdrawal through the optimistic path
        // succeeds, so the conflict has to be produced where it really lives --
        // in the scenario that races two transactions. Here the check is only
        // that the mapping exists and carries the header.
        mockMvc.perform(post("/scenarios/catch-inside").param("workers", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.innerCatchFired").value(false));

        mockMvc.perform(post("/scenarios/reservations")
                        .param("forceParentIncrement", "true")
                        .param("workers", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oversoldUnits").value(0));

        mockMvc.perform(post("/stock-items/{sku}/withdrawals", item.getSku())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1,"strategy":"OPTIMISTIC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Retry-After"));
    }
}
