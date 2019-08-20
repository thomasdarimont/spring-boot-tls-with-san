package demo;


import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class HttpClientAuthTests {

    public static final String CLIENT_KEYSTORE_PATH = "target/test-classes/clients-combined-keystore.pkcs12";
    public static final String TRUST_STORE_PATH = "target/test-classes/clients-combined-truststore.pkcs12";

    @Test
    public void httpClientAuth() {

        Assertions.assertTrue(true);

        char[] keystorePassword = "changeit".toCharArray();
        char[] trustStorePassword = "changeit".toCharArray();

        for(String clientAlias : List.of("client1","client2","client3","client4")){
            TestHttpConnector client = new TestHttpConnector(clientAlias, CLIENT_KEYSTORE_PATH, keystorePassword, TRUST_STORE_PATH, trustStorePassword);
            String responseBody = client.doGet("https://apps.tdlabs.local:8443");
            System.out.printf("#### Client %s: %s%n", clientAlias, responseBody);
        }

//
//        TestHttpConnector client2 = new TestHttpConnector("client2", CLIENT_KEYSTORE_PATH, keystorePassword, TRUST_STORE_PATH, trustStorePassword);
//        String responseBody2 = client2.doGet("https://apps.tdlabs.local:8443");
//        System.out.println(responseBody2);
//
//        TestHttpConnector client3 = new TestHttpConnector("client3", CLIENT_KEYSTORE_PATH, keystorePassword, TRUST_STORE_PATH, trustStorePassword);
//        String responseBody3 = client3.doGet("https://apps.tdlabs.local:8443");
//        System.out.println(responseBody3);
//
//        TestHttpConnector client4 = new TestHttpConnector("client4", CLIENT_KEYSTORE_PATH, keystorePassword, TRUST_STORE_PATH, trustStorePassword);
//        String responseBody4 = client4.doGet("https://apps.tdlabs.local:8443");
//        System.out.println(responseBody4);

    }

    static class TestHttpConnector extends AbstractHttpConnector {

        public TestHttpConnector(String clientAlias, String clientKeystorePath, char[] keystorePassword, String trustStorePath, char[] trustStorePassword) {
            super(clientAlias, clientKeystorePath, keystorePassword, trustStorePath, trustStorePassword);
        }

        public String doGet(String url) {
            return doWithHttpClient(httpClient -> {
                try {
                    HttpGet request = new HttpGet(url);
                    HttpResponse response = httpClient.execute(request);
                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    return body;
                } catch (Exception e) {
                    log.error("Error during request", e);
                    return null;
                }
            });
        }
    }

}
