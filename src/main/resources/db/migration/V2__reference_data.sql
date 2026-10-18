-- Two items so the deadlock scenario has two rows to lock in opposite order.
insert into stock_item (sku, name, quantity, version) values
    ('SKU-COFFEE', 'Coffee beans, 1 kg', 1000, 0),
    ('SKU-FILTER', 'Paper filters, 100 pack', 1000, 0);
