package com.gracelogic.platform.payment.dto.yandex.kassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexKassaReceiptCustomerDTO {
    private String full_name;
    private String phone;
    private String email;
    private String inn;

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInn() {
        return inn;
    }

    public void setInn(String inn) {
        this.inn = inn;
    }

    @Override
    public String toString() {
        return "YandexKassaReceiptCustomerDTO{" +
                "full_name='" + full_name + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", inn='" + inn + '\'' +
                '}';
    }
}
