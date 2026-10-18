-- The stock row every scenario fights over.
--
-- "version" exists in the table, not only in the entity: optimistic locking is
-- a column the application reads and writes inside the WHERE clause of its own
-- UPDATE. Nothing in the database enforces it.
--
-- The check constraint is deliberate, and so is what it does NOT catch. It
-- stops the quantity from going negative, which is the last line of defence
-- against overselling past zero. It is blind to a lost update: when two
-- transactions both read 100 and both write 99, the row lands on a perfectly
-- legal value that is simply one unit short of the truth.
create table stock_item (
    id         bigserial    primary key,
    sku        varchar(32)  not null,
    name       varchar(120) not null,
    quantity   integer      not null,
    version    bigint       not null default 0,
    constraint stock_item_sku_unique      unique (sku),
    constraint stock_item_quantity_signed check (quantity >= 0)
);

-- One row per withdrawal the application accepted.
--
-- This is what makes a lost update measurable instead of a story: the units
-- promised to callers are sum(quantity) here, the units actually taken off the
-- shelf are initial minus stock_item.quantity. The gap between the two numbers
-- is exactly the stock that was handed out twice.
create table stock_movement (
    id            bigserial   primary key,
    stock_item_id bigint      not null,
    quantity      integer     not null,
    strategy      varchar(24) not null,
    attempts      integer     not null default 1,
    created_at    timestamptz not null default now(),
    constraint stock_movement_item_fk  foreign key (stock_item_id) references stock_item (id),
    constraint stock_movement_positive check (quantity > 0)
);

create index stock_movement_item_idx on stock_movement (stock_item_id);

-- A child collection of the stock item, for the trap that an optimistic
-- version only guards the row it lives on. Inserting a reservation does not
-- touch stock_item, so it does not bump stock_item.version, so two concurrent
-- transactions can each add a reservation that together exceed the quantity --
-- with the version check passing on both.
create table reservation (
    id            bigserial   primary key,
    stock_item_id bigint      not null,
    order_ref     varchar(64) not null,
    quantity      integer     not null,
    created_at    timestamptz not null default now(),
    constraint reservation_item_fk       foreign key (stock_item_id) references stock_item (id),
    constraint reservation_order_unique  unique (order_ref),
    constraint reservation_positive      check (quantity > 0)
);

create index reservation_item_idx on reservation (stock_item_id);
