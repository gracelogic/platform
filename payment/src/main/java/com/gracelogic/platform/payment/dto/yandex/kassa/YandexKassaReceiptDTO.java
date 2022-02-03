package com.gracelogic.platform.payment.dto.yandex.kassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexKassaReceiptDTO {
    private YandexKassaReceiptCustomerDTO customer;
    private List<YandexKassaReceiptItemDTO> items = new LinkedList<>();

    public YandexKassaReceiptCustomerDTO getCustomer() {
        return customer;
    }

    public void setCustomer(YandexKassaReceiptCustomerDTO customer) {
        this.customer = customer;
    }

    public List<YandexKassaReceiptItemDTO> getItems() {
        return items;
    }

    public void setItems(List<YandexKassaReceiptItemDTO> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "YandexKassaReceiptDTO{" +
                "customer=" + customer +
                ", items=" + items +
                '}';
    }
}
