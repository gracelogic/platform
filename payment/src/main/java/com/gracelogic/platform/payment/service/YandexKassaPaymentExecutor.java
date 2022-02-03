package com.gracelogic.platform.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gracelogic.platform.finance.FinanceUtils;
import com.gracelogic.platform.payment.Utils;
import com.gracelogic.platform.payment.dto.PaymentExecutionRequestDTO;
import com.gracelogic.platform.payment.dto.PaymentExecutionResultDTO;
import com.gracelogic.platform.payment.dto.ProcessPaymentRequest;
import com.gracelogic.platform.payment.dto.yandex.kassa.*;
import com.gracelogic.platform.payment.exception.PaymentExecutionException;
import com.gracelogic.platform.property.service.PropertyService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YandexKassaPaymentExecutor implements PaymentExecutor {
    private static final String API_URL = "https://payment.yandex.net";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static Logger logger = Logger.getLogger(YandexKassaPaymentExecutor.class);

    @Override
    public PaymentExecutionResultDTO execute(PaymentExecutionRequestDTO request, ApplicationContext context) throws PaymentExecutionException {
        PropertyService propertyService = null;
        try {
            propertyService = context.getBean(PropertyService.class);
        } catch (Exception e) {
            throw new PaymentExecutionException(e.getMessage());
        }

        YandexKassaCreatePaymentDTO paymentDTO = new YandexKassaCreatePaymentDTO();
        paymentDTO.setAmount(new YandexKassaAmountDTO(FinanceUtils.toFractional2Rounded(request.getAmount()), request.getCurrencyCode()));
        paymentDTO.setCapture(true);
        String paymentToken = request.getParams().get("payment_token");
        if (!StringUtils.isEmpty(paymentToken)) {
            //Платёж был создан на клиенте (выбраны способ оплата и проч)
            paymentDTO.setPayment_token(paymentToken);
        }
        else {
            //Создаём платёж на сервере
            paymentDTO.setConfirmation(new YandexKassaConfirmationDTO("redirect", propertyService.getPropertyValue("payment:yandex_kassa_redirect_url")));
        }

        if (!StringUtils.isEmpty(request.getDescription())) {
            YandexKassaReceiptDTO receiptDTO = new YandexKassaReceiptDTO();
            paymentDTO.setReceipt(receiptDTO);

            YandexKassaReceiptItemDTO itemDTO = new YandexKassaReceiptItemDTO();
            itemDTO.setAmount(paymentDTO.getAmount());
            itemDTO.setQuantity(1D);
            itemDTO.setDescription(request.getDescription());
            receiptDTO.getItems().add(itemDTO);

            YandexKassaReceiptCustomerDTO customerDTO = new YandexKassaReceiptCustomerDTO();
            customerDTO.setEmail(request.getParams().get("customer_full_name"));
            customerDTO.setEmail(request.getParams().get("customer_email"));
            receiptDTO.setCustomer(customerDTO);
        }

        try {
            YandexKassaPaymentDTO result = createPayment(paymentDTO, propertyService.getPropertyValue("payment:yandex_kassa_shop_id"), propertyService.getPropertyValue("payment:yandex_kassa_secret"));

            Map<String, String> responseParams = new HashMap<>();
            request.getParams().put("confirmation_url", result.getConfirmation().getConfirmation_url());
            return new PaymentExecutionResultDTO(false, result.getId(), request.getParams());
        } catch (Exception e) {
            logger.error("Failed to execute payment with Yandex.Kassa", e);
            throw new PaymentExecutionException(e.getMessage());
        }

    }

    @Override
    public void processCallback(UUID paymentSystemId, ApplicationContext context, HttpServletRequest request, HttpServletResponse response) throws PaymentExecutionException {
        logger.info("Yandex.Kassa callback accepted");
        PropertyService propertyService = null;
        PaymentService paymentService = null;
        try {
            propertyService = context.getBean(PropertyService.class);
            paymentService = context.getBean(PaymentService.class);
        } catch (Exception e) {
            throw new PaymentExecutionException(e.getMessage());
        }

        try {
            String requestBody = IOUtils.toString(request.getInputStream());
            logger.info("raw request: " + requestBody);
            YandexKassaNotificationDTO notification = mapper.readValue(requestBody, YandexKassaNotificationDTO.class);
            logger.info(notification.toString());
            if (StringUtils.equalsIgnoreCase(notification.getEvent(), "payment.succeeded") && !StringUtils.isEmpty(notification.getObject().getId())) {
                YandexKassaPaymentDTO payment = getPayment(notification.getObject().getId(), propertyService.getPropertyValue("payment:yandex_kassa_shop_id"), propertyService.getPropertyValue("payment:yandex_kassa_secret"));
                if (StringUtils.equalsIgnoreCase(payment.getStatus(), "succeeded")) {
                    ProcessPaymentRequest req = new ProcessPaymentRequest();
                    req.setExternalIdentifier(payment.getId());
                    req.setRegisteredAmount(Double.parseDouble(payment.getAmount().getValue()));
                    req.setPaymentUID(payment.getId());
                    req.setCurrency(payment.getAmount().getCurrency());
                    paymentService.processPayment(paymentSystemId, req, null);
                }
                else {
                    logger.warn("Payment not succeed, but event accepted: " + notification.getObject().getId());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process Yandex.Kassa callback", e);
            throw new PaymentExecutionException(e.getMessage());
        }
    }

    @Override
    public boolean isRecurringPaymentsAllowed() {
        return false;
    }

    private static YandexKassaPaymentDTO createPayment(YandexKassaCreatePaymentDTO request, String shopId, String secret) throws Exception {
        CloseableHttpClient httpClient = HttpClientUtils.getMultithreadedUnsecuredClient();
        String uri = API_URL + "/api/v3/payments";
        logger.info("request url: " + uri);
        HttpPost sendMethod = new HttpPost(uri);
        sendMethod.addHeader("Authorization", "Basic " + Utils.getBase64Authorization(shopId, secret));
        sendMethod.addHeader("Content-Type", "application/json");
        sendMethod.addHeader("Accept", "application/json");
        sendMethod.addHeader("Idempotence-Key", UUID.randomUUID().toString());
        String requestBody = mapper.writeValueAsString(request);
        logger.info("request body: " + requestBody);
        sendMethod.setEntity(new StringEntity(requestBody));
        CloseableHttpResponse result = httpClient.execute(sendMethod);
        logger.info("response status: " + result.getStatusLine().getStatusCode());
        HttpEntity entity = result.getEntity();
        String response = EntityUtils.toString(entity);
        logger.info("response body: " + response);
        return mapper.readValue(response, YandexKassaPaymentDTO.class);
    }

    private static YandexKassaPaymentDTO getPayment(String id, String shopId, String secret) throws Exception {
        CloseableHttpClient httpClient = HttpClientUtils.getMultithreadedUnsecuredClient();
        String uri = API_URL + "/api/v3/payments/" + id;
        logger.debug("request url: " + uri);
        HttpGet sendMethod = new HttpGet(uri);
        sendMethod.addHeader("Authorization", "Basic " + Utils.getBase64Authorization(shopId, secret));
        CloseableHttpResponse result = httpClient.execute(sendMethod);
        logger.debug("response status: " + result.getStatusLine().getStatusCode());
        HttpEntity entity = result.getEntity();
        String response = EntityUtils.toString(entity);
        logger.debug("response body: " + response);
        return mapper.readValue(response, YandexKassaPaymentDTO.class);
    }
}