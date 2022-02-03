package com.gracelogic.platform.payment.dto.yandex.kassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexKassaReceiptItemDTO {
    private String description;
    private Double quantity;
    private YandexKassaAmountDTO amount;
    private String vat_code = "2";
    private String payment_mode = "full_prepayment";
    private String payment_subject = "commodity";

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getVat_code() {
        return vat_code;
    }

    public void setVat_code(String vat_code) {
        this.vat_code = vat_code;
    }

    public String getPayment_mode() {
        return payment_mode;
    }

    public void setPayment_mode(String payment_mode) {
        this.payment_mode = payment_mode;
    }

    public String getPayment_subject() {
        return payment_subject;
    }

    public void setPayment_subject(String payment_subject) {
        this.payment_subject = payment_subject;
    }

    public YandexKassaAmountDTO getAmount() {
        return amount;
    }

    public void setAmount(YandexKassaAmountDTO amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "YandexKassaReceiptItemDTO{" +
                "description='" + description + '\'' +
                ", quantity=" + quantity +
                ", amount=" + amount +
                ", vat_code='" + vat_code + '\'' +
                ", payment_mode='" + payment_mode + '\'' +
                ", payment_subject='" + payment_subject + '\'' +
                '}';
    }
}
