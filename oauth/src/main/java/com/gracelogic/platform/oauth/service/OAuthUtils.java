package com.gracelogic.platform.oauth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class OAuthUtils {
    private static Logger logger = Logger.getLogger(OAuthUtils.class);

    public static Map<Object, Object> postJsonBodyReturnJson(String url, HashMap<String, String> params) {
        logger.info("Request to: " + url);

        CloseableHttpClient httpClient = HttpClients.custom().build();
        HttpPost postMethod = new HttpPost(url);

        postMethod.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        postMethod.getParams().setParameter("http.protocol.single-cookie-header", true);

        Map<Object, Object> result = null;

        MappingJackson2HttpMessageConverter c = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = c.getObjectMapper();

        String request = null;

        if (params != null) {
            try {
                request = objectMapper.writeValueAsString(params);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        logger.info("Request body: " + request);

        String response = null;

        try {
            if (request != null) {
                postMethod.setHeader("Accept", "application/json");
                postMethod.setHeader("Content-Type", "application/json");
//                postMethod.setHeader("Content-Encoding", "UTF-8");
                if (!StringUtils.isEmpty(request)) {
                    postMethod.setEntity(new StringEntity(request, Charset.forName("UTF-8")));
                }
            }

            CloseableHttpResponse httpResult = httpClient.execute(postMethod);
            logger.info("Request status: " + httpResult);
            HttpEntity entity = httpResult.getEntity();
            if (entity != null) {
                if (httpResult.getStatusLine().getStatusCode() == 200) {
                    response = EntityUtils.toString(entity);
                }
                EntityUtils.consume(entity);
                logger.info("Response body: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                result = objectMapper.readValue(response, new TypeReference<Map<Object, Object>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return result;
    }

    public static Map<Object, Object> postTextBodyReturnJson(String url, String body) {
        logger.info("Request to: " + url);

        CloseableHttpClient httpClient = HttpClients.custom().build();
        HttpPost postMethod = new HttpPost(url);

        postMethod.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        postMethod.getParams().setParameter("http.protocol.single-cookie-header", true);

        Map<Object, Object> result = null;

        MappingJackson2HttpMessageConverter c = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = c.getObjectMapper();

        String request = body;

        logger.info("Request body: " + request);

        String response = null;

        try {
            if (request != null) {
                postMethod.setHeader("Accept", "application/json");
                postMethod.setHeader("Content-Type", "application/x-www-form-urlencoded");
//                postMethod.setHeader("Content-Encoding", "UTF-8");
                if (!StringUtils.isEmpty(request)) {
                    postMethod.setEntity(new StringEntity(request, Charset.forName("UTF-8")));
                }
            }

            CloseableHttpResponse httpResult = httpClient.execute(postMethod);
            logger.info("Request status: " + httpResult);
            HttpEntity entity = httpResult.getEntity();
            if (entity != null) {
                //if (httpResult.getStatusLine().getStatusCode() == 200) {
                    response = EntityUtils.toString(entity);
                //}
                EntityUtils.consume(entity);
                logger.info("Response body: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                result = objectMapper.readValue(response, new TypeReference<Map<Object, Object>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return result;
    }

    public static Map<Object, Object> getQueryReturnJson(String url) {
        logger.info("Request to: " + url);

        CloseableHttpClient httpClient = HttpClients.custom().build();

        HttpGet method = new HttpGet(url);

        method.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        method.getParams().setParameter("http.protocol.single-cookie-header", true);

        Map<Object, Object> result = null;
        String response = null;

        MappingJackson2HttpMessageConverter c = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = c.getObjectMapper();

        try {
            CloseableHttpResponse httpResult = httpClient.execute(method);
            logger.info("Request status: " + httpResult);

            HttpEntity entity = httpResult.getEntity();
            if (entity != null) {
                if (httpResult.getStatusLine().getStatusCode() == 200) {
                    response = EntityUtils.toString(entity);
                }
                EntityUtils.consume(entity);
                logger.info("Response body: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                result = objectMapper.readValue(response, new TypeReference<Map<Object, Object>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return result;
    }

    public static String getQueryReturnText(String url) {
        logger.info("Request to: " + url);

        CloseableHttpClient httpClient = HttpClients.custom().build();

        HttpGet method = new HttpGet(url);

        method.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        method.getParams().setParameter("http.protocol.single-cookie-header", true);

        String response = null;

        try {
            CloseableHttpResponse httpResult = httpClient.execute(method);
            logger.info("Request status: " + httpResult);

            HttpEntity entity = httpResult.getEntity();
            if (entity != null) {
                if (httpResult.getStatusLine().getStatusCode() == 200) {
                    response = EntityUtils.toString(entity);
                }
                EntityUtils.consume(entity);
                logger.info("Response body: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
