package com.gkcontas.inventory.web;

import com.gkcontas.inventory.service.ConcurrentUpdateException;
import com.gkcontas.inventory.service.DeadlockDetectedException;
import com.gkcontas.inventory.service.InsufficientStockException;
import com.gkcontas.inventory.service.RowLockTimeoutException;
import com.gkcontas.inventory.service.StockItemNotFoundException;
import com.gkcontas.inventory.web.dto.ApiError;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The status codes are the contract, and splitting them is the point.
 *
 * <p>409 means "your request was fine, somebody beat you to the row, read
 * again and send it again". 422 means "there is not enough stock, sending it
 * again will not help". 423 means "the row is locked and the wait ran out, the
 * work may still be possible". A client that gets 409 for all three cannot
 * tell a retry worth making from a retry that will never succeed.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(StockItemNotFoundException.class)
    ResponseEntity<ApiError> notFound(StockItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "stock_item_not_found", e.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiError> insufficient(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "insufficient_stock", e.getMessage()));
    }

    /**
     * Retry-After is not decoration here: the only correct response to a
     * version conflict is to read again and write again, and the header is
     * where that instruction belongs.
     */
    @ExceptionHandler(ConcurrentUpdateException.class)
    ResponseEntity<ApiError> conflict(ConcurrentUpdateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "0")
                .body(ApiError.of(409, "concurrent_update", e.getMessage(),
                        List.of("attempts=" + e.getAttempts())));
    }

    @ExceptionHandler(DeadlockDetectedException.class)
    ResponseEntity<ApiError> deadlock(DeadlockDetectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "0")
                .body(ApiError.of(409, "deadlock_detected", e.getMessage()));
    }

    @ExceptionHandler(RowLockTimeoutException.class)
    ResponseEntity<ApiError> locked(RowLockTimeoutException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header("Retry-After", "1")
                .body(ApiError.of(423, "row_locked", e.getMessage()));
    }

    /** A duplicate order_ref on a reservation, mostly. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "integrity_violation",
                        "the write violated a database constraint"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "validation_failed", "the request body is invalid", details));
    }
}
