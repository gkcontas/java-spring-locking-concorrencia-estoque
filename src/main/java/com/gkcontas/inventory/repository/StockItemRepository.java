package com.gkcontas.inventory.repository;

import com.gkcontas.inventory.domain.StockItem;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    /**
     * {@code SELECT ... FOR UPDATE} in the same statement that loads the row.
     *
     * <p>Loading first and locking afterwards is a race window: between the
     * read and the lock another transaction can commit, and the entity already
     * in the persistence context is stale -- the lock then protects a value
     * that is no longer the row's. The lock has to be part of the query that
     * produced the entity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockItem i where i.id = :id")
    Optional<StockItem> findByIdForUpdate(@Param("id") Long id);

    /**
     * Same lock, but failing instead of waiting.
     *
     * <p>A timeout of 0 is the only value PostgreSQL maps to something in the
     * statement itself: {@code FOR UPDATE NOWAIT}, which raises 55P03 the
     * moment the row is held. A positive timeout has no equivalent in
     * PostgreSQL syntax, so Hibernate leaves the statement plain and the wait
     * is governed by the server's own {@code lock_timeout} instead.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select i from StockItem i where i.id = :id")
    Optional<StockItem> findByIdForUpdateNoWait(@Param("id") Long id);

    /**
     * Loads the row and promises to bump its version at flush, even if nothing
     * on it is changed: {@code update stock_item set version=? where id=? and
     * version=?}.
     *
     * <p>This is the answer to a version that guards the wrong thing. Adding a
     * child row does not make the parent dirty, so the parent's version never
     * moves and two concurrent inserts both pass a check that was never
     * really performed. Forcing the increment makes the parent the point of
     * serialization for changes to its children.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select i from StockItem i where i.id = :id")
    Optional<StockItem> findByIdForceIncrement(@Param("id") Long id);

    /**
     * Locks several rows in one statement, ordered by id.
     *
     * <p>The ORDER BY is the whole point. A deadlock between two transactions
     * that each lock two rows is a consequence of the order they ask for them,
     * not of the rows themselves: A then B against B then A ends in 40P01. Any
     * agreed order removes the cycle, and the primary key is an order both
     * sides can compute without talking to each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StockItem i where i.id in :ids order by i.id")
    List<StockItem> lockAllOrderedById(@Param("ids") List<Long> ids);

    /**
     * The whole withdrawal as one statement: no read into the application, no
     * version column, no lock held across a round trip. The condition in the
     * WHERE clause is the business rule, and the row count is the answer --
     * 1 means it was taken, 0 means somebody else got there first or the
     * balance was short.
     *
     * <p>{@code clearAutomatically} is not decoration. A bulk UPDATE goes
     * straight to the database and the persistence context never hears about
     * it, so an entity already loaded in this session would keep answering
     * with its pre-UPDATE quantity for the rest of the transaction.
     * {@code flushAutomatically} is the mirror image: pending changes have to
     * reach the database before a statement that reads what they wrote.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StockItem i
               set i.quantity = i.quantity - :requested
             where i.id = :id
               and i.quantity >= :requested
            """)
    int withdrawAtomically(@Param("id") Long id, @Param("requested") int requested);

    /**
     * Writes an absolute quantity by primary key, with no version in the WHERE
     * clause: last writer wins.
     *
     * <p>This exists because the entity in this project carries
     * {@code @Version}, and Hibernate puts that column into every UPDATE it
     * emits for the entity -- there is no way to ask it politely not to. So the
     * unprotected strategy has to go around the entity to emit the statement
     * that an entity WITHOUT a version produces on its own. The point is not
     * the bulk update; it is that this is the shape of the UPDATE in the
     * majority of codebases, and the shape a lost update needs.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StockItem i set i.quantity = :quantity where i.id = :id")
    int overwriteQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * Resets a row to a known quantity for the next scenario run.
     *
     * <p>Deliberately a bulk UPDATE: it also has to put {@code version} back,
     * and {@code version} is Hibernate's to manage on the entity path.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StockItem i set i.quantity = :quantity, i.version = 0 where i.id = :id")
    int resetQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
