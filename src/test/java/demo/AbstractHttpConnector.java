package demo;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.jboss.logging.Logger;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides HTTP communication infrastructure to interface with external systems & services.
 */
public abstract class AbstractHttpConnector {

    private static final Logger LOG = Logger.getLogger(AbstractHttpConnector.class);
    private static final int DEFAULT_REQUEST_TIMEOUT_IN_SECONDS = 45;
    private static final String HEADER_APPLICATION_NAME = "X-ApplicationName";
    private static final String APPLICATION_NAME = "Acme-Keycloak";

    private final String clientAlias;
    private final KeyStore keyStore;
    private final KeyStore trustStore;

    /**
     * Creates an {@link AbstractHttpConnector} with the given client-certificate KeyStore and TrustStore.
     *
     * @param clientAlias
     * @param clientKeystorePath
     * @param keystorePassword
     * @param trustStorePath
     * @param trustStorePassword
     */
    public AbstractHttpConnector(String clientAlias, String clientKeystorePath, char[] keystorePassword, String trustStorePath, char[] trustStorePassword) {
        this.clientAlias = clientAlias;
        this.trustStore = loadTrustStore(trustStorePath, trustStorePassword);
        this.keyStore = loadCustomKeystore(clientKeystorePath, keystorePassword);
    }

    /**
     * Creates an {@link AbstractHttpConnector} with the given client-certificate KeyStore.
     *
     * @param clientAlias
     * @param clientKeystorePath
     * @param keystorePassword
     */
    public AbstractHttpConnector(String clientAlias, String clientKeystorePath, char[] keystorePassword) {
        this(clientAlias, clientKeystorePath, keystorePassword, null, null);
    }

    /**
     * Creates an {@link AbstractHttpConnector} without a Keystore or TrustStore configured.
     *
     * @param clientAlias
     */
    public AbstractHttpConnector(String clientAlias) {
        this(clientAlias, null, null, null, null);
    }

    protected KeyStore loadTrustStore(String trustStorePath, char[] trustStorePassword) {

        if (trustStorePath == null) {
            return null;
        }

        try (InputStream keyStoreStream = new FileInputStream(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(keyStoreStream, trustStorePassword);
            return trustStore;
        } catch (Exception ex) {
            throw new RuntimeException("Could not load default keystore as TrustStore for " + clientAlias, ex);
        }
    }

    protected KeyStore loadCustomKeystore(String clientKeystorePath, char[] keystorePassword) {

        if (clientKeystorePath == null) {
            return null;
        }

        try (InputStream keyStoreStream = new FileInputStream(clientKeystorePath)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keyStoreStream, keystorePassword != null ? keystorePassword : new char[0]);
            return keyStore;
        } catch (Exception ex) {
            throw new RuntimeException("Could not load keystore for custom HttpClient for " + clientAlias, ex);
        }
    }

    /**
     * The given {@code httpFunc} is provided with a managed {@link HttpClient}.
     * This can be used if you need to return a result.
     *
     * @param httpFunc
     * @param <T>
     * @return
     */
    protected <T> T doWithHttpClient(Function<HttpClient, T> httpFunc) {
        try (SilentClosableHttpClientWrapper httpClientWrapper = new SilentClosableHttpClientWrapper(createHttpClient(keyStore))) {
            return httpFunc.apply(httpClientWrapper.getHttpClient());
        }
    }

    /**
     * The given {@code httpFunc} is provided with a managed {@link HttpClient}.
     * This can be used if you don't need to return a result.
     *
     * @param httpFunc
     */
    protected void doWithHttpClient(Consumer<HttpClient> httpFunc) {
        try (SilentClosableHttpClientWrapper httpClientWrapper = new SilentClosableHttpClientWrapper(createHttpClient(keyStore))) {
            httpFunc.accept(httpClientWrapper.getHttpClient());
        }
    }

    protected CloseableHttpClient createHttpClient(KeyStore clientCertKeystore) {

        try {
            SSLContext sslContext = createSslContext(clientCertKeystore);
            RequestConfig requestConfig = createRequestConfig();
            HttpClientBuilder httpClientBuilder = HttpClients.custom() //
                    .setSSLContext(sslContext) //
                    .setDefaultRequestConfig(requestConfig) //
                    ;
            adjustHttpClient(httpClientBuilder);
            return httpClientBuilder.build();
        } catch (Exception ex) {
            throw new RuntimeException("Could not create custom HttpClient", ex);
        }
    }

    private RequestConfig createRequestConfig() {
        int timeoutInSeconds = DEFAULT_REQUEST_TIMEOUT_IN_SECONDS;
        return RequestConfig.custom()
                .setConnectTimeout(timeoutInSeconds * 1000)
                .setConnectionRequestTimeout(timeoutInSeconds * 1000)
                .setSocketTimeout(timeoutInSeconds * 1000).build();
    }

    protected void adjustHttpClient(HttpClientBuilder httpClientBuilder) {
        httpClientBuilder.addInterceptorFirst(this::addCommonHeaders);
    }

    protected void addCommonHeaders(HttpRequest request, HttpContext context) throws
            HttpException, IOException {

        request.addHeader(HEADER_APPLICATION_NAME, APPLICATION_NAME);
    }


    protected SSLContext createSslContext(KeyStore clientCertKeystore) throws Exception {

        SSLContextBuilder sslContextBuilder = SSLContexts.custom();
        if (trustStore != null) {
            sslContextBuilder.loadTrustMaterial(trustStore, null);
        }

        if (clientCertKeystore != null) {
            sslContextBuilder.loadKeyMaterial(clientCertKeystore, "changeit".toCharArray(), new PrivateKeyStrategy() {
                @Override
                public String chooseAlias(Map<String, PrivateKeyDetails> aliases, Socket socket) {

                    if (aliases == null || aliases.isEmpty()) {
                        return null;
                    }

                    return AbstractHttpConnector.this.clientAlias;
                }
            });
        }

        return sslContextBuilder.build();
    }

    class SilentClosableHttpClientWrapper implements AutoCloseable {

        private final CloseableHttpClient httpClient;

        public SilentClosableHttpClientWrapper(CloseableHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public void close() {

            if (this.httpClient == null) {
                return;
            }

            try {
                this.httpClient.close();
            } catch (IOException e) {
                LOG.warn("Could not close custom http client for client " + clientAlias, e);
            }
        }

        public CloseableHttpClient getHttpClient() {
            return httpClient;
        }
    }
}
