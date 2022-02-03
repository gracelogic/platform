package com.gracelogic.platform.payment.dto.yandex.kassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexKassaCreatePaymentDTO {
    private YandexKassaAmountDTO amount;
    private YandexKassaConfirmationDTO confirmation;
    private YandexKassaPaymentMethodDTO payment_method_data;
    private String description;
    private YandexKassaRecipientDTO recipient;
    private Boolean capture;
    private String payment_token;
    private String payment_method_id;
    private Boolean save_payment_method;
    private String client_ip;
    private Map<String, String> metadata;
    private YandexKassaReceiptDTO receipt;

    public YandexKassaAmountDTO getAmount() {
        return amount;
    }

    public void setAmount(YandexKassaAmountDTO amount) {
        this.amount = amount;
    }

    public YandexKassaConfirmationDTO getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(YandexKassaConfirmationDTO confirmation) {
        this.confirmation = confirmation;
    }

    public YandexKassaPaymentMethodDTO getPayment_method_data() {
        return payment_method_data;
    }

    public void setPayment_method_data(YandexKassaPaymentMethodDTO payment_method_data) {
        this.payment_method_data = payment_method_data;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public YandexKassaRecipientDTO getRecipient() {
        return recipient;
    }

    public void setRecipient(YandexKassaRecipientDTO recipient) {
        this.recipient = recipient;
    }

    public Boolean getCapture() {
        return capture;
    }

    public void setCapture(Boolean capture) {
        this.capture = capture;
    }

    public String getPayment_token() {
        return payment_token;
    }

    public void setPayment_token(String payment_token) {
        this.payment_token = payment_token;
    }

    public String getPayment_method_id() {
        return payment_method_id;
    }

    public void setPayment_method_id(String payment_method_id) {
        this.payment_method_id = payment_method_id;
    }

    public Boolean getSave_payment_method() {
        return save_payment_method;
    }

    public void setSave_payment_method(Boolean save_payment_method) {
        this.save_payment_method = save_payment_method;
    }

    public String getClient_ip() {
        return client_ip;
    }

    public void setClient_ip(String client_ip) {
        this.client_ip = client_ip;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public YandexKassaReceiptDTO getReceipt() {
        return receipt;
    }

    public void setReceipt(YandexKassaReceiptDTO receipt) {
        this.receipt = receipt;
    }

    @Override
    public String toString() {
        return "YandexKassaCreatePaymentDTO{" +
                "amount=" + amount +
                ", confirmation=" + confirmation +
                ", payment_method_data=" + payment_method_data +
                ", description='" + description + '\'' +
                ", recipient=" + recipient +
                ", capture=" + capture +
                ", payment_token='" + payment_token + '\'' +
                ", payment_method_id='" + payment_method_id + '\'' +
                ", save_payment_method=" + save_payment_method +
                ", client_ip='" + client_ip + '\'' +
                ", metadata=" + metadata +
                ", receipt=" + receipt +
                '}';
    }
}
