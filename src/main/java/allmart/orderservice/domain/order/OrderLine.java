package allmart.orderservice.domain.order;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import static java.util.Objects.requireNonNull;

@Embeddable
public record OrderLine(
        @Column(name = "product_id", nullable = false)
        Long productId,
        @Column(name = "product_name_snapshot", nullable = false, length = 200)
        String productNameSnapshot,
        @Embedded
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
        Money unitPrice,
        @Column(name = "quantity", nullable = false)
        int quantity
) {

    public OrderLine {
        requireNonNull(productId);
        requireNonNull(unitPrice);

        productNameSnapshot = normalizeRequired(productNameSnapshot);

        if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");

    }

    public Money lineAmount() {
        return unitPrice.multiply(quantity);
    }

    private static String normalizeRequired(String productNameSnapshot) {
        requireNonNull(productNameSnapshot);
        String trimmed= productNameSnapshot.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException("productNameSnapshot is blank");
        if (trimmed.length() > 200) throw new IllegalArgumentException("productNameSnapshot must be <= " + 200);
        return trimmed;
    }
}
