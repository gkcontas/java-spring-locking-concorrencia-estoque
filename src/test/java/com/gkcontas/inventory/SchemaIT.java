package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The schema comes from Flyway and {@code ddl-auto: validate} only checks that
 * the entities fit it. What the entities cannot express is checked here.
 */
class SchemaIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void seedItemsExistForTheDeadlockScenario() {
        List<String> skus = jdbc.queryForList(
                "select sku from stock_item where sku like 'SKU-%' order by sku", String.class);
        assertThat(skus).contains("SKU-COFFEE", "SKU-FILTER");
    }

    @Test
    void quantityCannotGoNegative() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into stock_item (sku, name, quantity) values ('SCHEMA-NEG', 'x', -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * The constraint is the last line of defence against selling past zero, and
     * it is blind to the problem this project is about: a balance that is wrong
     * but still positive passes it without complaint.
     */
    @Test
    void theConstraintDoesNotNoticeAWrongButPositiveBalance() {
        jdbc.update("insert into stock_item (sku, name, quantity) values ('SCHEMA-WRONG', 'x', 100)");
        int updated = jdbc.update("update stock_item set quantity = 99 where sku = 'SCHEMA-WRONG'");
        assertThat(updated).isOne();
        assertThat(jdbc.queryForObject(
                "select quantity from stock_item where sku = 'SCHEMA-WRONG'", Integer.class))
                .isEqualTo(99);
    }
}
