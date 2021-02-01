package com.gracelogic.platform.oauth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gracelogic.platform.oauth.dto.OAuthDTO;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.Locale;
import java.util.Map;

public class TestCls {
    public static void main(String ... args) {
        String idToken = "eyJraWQiOiJlWGF1bm1MIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiYXVkIjoiY29tLmthcm1pdHQubW9iaWxlIiwiZXhwIjoxNjEyMjU1NTQxLCJpYXQiOjE2MTIxNjkxNDEsInN1YiI6IjAwMTQ5Mi44YzNlMjEzNzE4YmM0YzNjODkwZmI5MGQ4YjkxZTAxNy4wODU1IiwiYXRfaGFzaCI6Ikx1aEZwcFJKY1Nnd1JiY1lEYk4zRWciLCJlbWFpbCI6ImluZ3ZhcnBhcmtvQGdtYWlsLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjoidHJ1ZSIsImF1dGhfdGltZSI6MTYxMjE2OTEwNywibm9uY2Vfc3VwcG9ydGVkIjp0cnVlfQ.pSrBT1_g0QbDRYjAJzaeEXWU_eKn3EuRmOgGiQ39UvWkE0DwDXi4FyeV0kovkz8FdYkjL6gKchNQD4uA1yI0BG3LPNusm8oEl15B0hhWiE3BB31aVKYBHvYCjtW7sF6e-ShZaHdIMZuykRedPAx8bznywWYTOGr9vZ43lnQx81tdOKFOnNiGb18p_ROXsvh11RmiPy2eqXk4d--KoiAW5DWTEgd_vf37XRF-_sH-ia8IkKD5gYVfnjJ1PxLcy4t7kguz4dlrPhSi8r77HRzyVPRQAO0NuVyqvU5ca2HNMb3UyWoMR4CDeHNRXwwRUyI6cItJg5tt2P-jHX5LZrfmRA";
        String decodedIdToken = decodeJWTBody(idToken);
        MappingJackson2HttpMessageConverter c = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = c.getObjectMapper();

        try {
            Map<Object, Object> json = objectMapper.readValue(decodedIdToken, new TypeReference<Map<Object, Object>>() {
            });

            OAuthDTO OAuthDTO = new OAuthDTO();
            OAuthDTO.setAccessToken(idToken);
            OAuthDTO.setUserId(json.get("sub") != null ? (String) json.get("sub") : null);
            OAuthDTO.setEmail(json.get("email") != null ? (String) json.get("email") : null);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        Locale locale = Locale.US;
        System.out.println("text:" + decodeJWTBody(idToken));


    }

    private static String decodeJWTBody(String jwtToken) {
        String[] split_string = jwtToken.split("\\.");
        String base64EncodedHeader = split_string[0];
        String base64EncodedBody = split_string[1];
        String base64EncodedSignature = split_string[2];

        Base64 base64Url = new Base64(true);
        String header = new String(base64Url.decode(base64EncodedHeader));

        String body = new String(base64Url.decode(base64EncodedBody));
        return body;
    }
}
