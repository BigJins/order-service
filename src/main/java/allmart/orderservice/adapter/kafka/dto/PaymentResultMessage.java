package allmart.orderservice.adapter.kafka.dto;

public record PaymentResultMessage(
        String tossOrderId,
        long amount,
        String paymentKey,
        String result
) {

    public boolean isFailed() {
        return result != null &&
                (result.equalsIgnoreCase("FAILED") || result.equalsIgnoreCase("PAYMENT_FAILED"));
    }
}