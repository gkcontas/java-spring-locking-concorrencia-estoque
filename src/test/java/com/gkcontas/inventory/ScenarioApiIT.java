package com.gkcontas.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scenarios behind the README are reachable over HTTP, so a reader can
 * reproduce a number instead of taking it on trust.
 */
class ScenarioApiIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theUnguardedScenarioReportsTheUnitsItLost() throws Exception {
        mockMvc.perform(post("/scenarios/strategies/NONE")
                        .param("workers", "16")
                        .param("initialQuantity", "500")
                        .param("thinkTimeMillis", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("NONE"))
                .andExpect(jsonPath("$.successes").value(16))
                .andExpect(jsonPath("$.unitsPromised").value(16))
                .andExpect(jsonPath("$.unitsRemoved").value(1))
                .andExpect(jsonPath("$.unitsLost").value(15));
    }

    @Test
    void theAtomicScenarioLosesNothing() throws Exception {
        mockMvc.perform(post("/scenarios/strategies/ATOMIC")
                        .param("workers", "16")
                        .param("initialQuantity", "500")
                        .param("thinkTimeMillis", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsLost").value(0))
                .andExpect(jsonPath("$.successes").value(16))
                .andExpect(jsonPath("$.transactionsUsed").value(16));
    }

    @Test
    void allFiveStrategiesRunInOneCall() throws Exception {
        mockMvc.perform(post("/scenarios/strategies")
                        .param("workers", "8")
                        .param("initialQuantity", "500")
                        .param("thinkTimeMillis", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].strategy").value("NONE"))
                .andExpect(jsonPath("$[4].strategy").value("ATOMIC"))
                .andExpect(jsonPath("$[4].unitsLost").value(0));
    }

    @Test
    void noWaitTurnsTheQueueIntoFailures() throws Exception {
        mockMvc.perform(post("/scenarios/no-wait")
                        .param("workers", "16")
                        .param("initialQuantity", "500")
                        .param("thinkTimeMillis", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsLost").value(0))
                .andExpect(jsonPath("$.failures.locked").exists());
    }

    @Test
    void theDeadlockScenariosDisagreeOnlyAboutLockOrder() throws Exception {
        mockMvc.perform(post("/scenarios/deadlock/crossed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockOrder").value("crossed"))
                .andExpect(jsonPath("$.deadlocks").value(1));

        mockMvc.perform(post("/scenarios/deadlock/agreed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockOrder").value("agreed"))
                .andExpect(jsonPath("$.deadlocks").value(0))
                .andExpect(jsonPath("$.successes").value(2));
    }

    @Test
    void theLockWaitScenarioSeparatesWaitingFromFailingFast() throws Exception {
        mockMvc.perform(post("/scenarios/lock-wait").param("holdMillis", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noWaitOutcome").value("locked"))
                .andExpect(jsonPath("$.waitingOutcome").value("ok"));
    }

    @Test
    void theContentionScenarioReportsThroughput() throws Exception {
        mockMvc.perform(post("/scenarios/contention/PESSIMISTIC")
                        .param("workers", "16")
                        .param("rows", "4")
                        .param("initialQuantity", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(4))
                .andExpect(jsonPath("$.unitsLost").value(0))
                .andExpect(jsonPath("$.throughputPerSecond").exists());
    }

    @Test
    void theRepeatableReadScenarioAbortsInsteadOfLosing() throws Exception {
        mockMvc.perform(post("/scenarios/repeatable-read")
                        .param("workers", "8")
                        .param("initialQuantity", "500")
                        .param("thinkTimeMillis", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsLost").value(0))
                .andExpect(jsonPath("$.failures.conflict").exists());
    }
}
