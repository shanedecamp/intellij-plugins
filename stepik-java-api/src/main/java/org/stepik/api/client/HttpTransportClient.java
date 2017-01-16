package org.stepik.api.client;

import javafx.util.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author meanmail
 */
public class HttpTransportClient implements TransportClient {
    private static final Logger logger = LoggerFactory.getLogger(HttpTransportClient.class);

    private static final String ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String USER_AGENT = "Stepik Java API Client/" + StepikApiClient.getVersion();

    private static final int MAX_SIMULTANEOUS_CONNECTIONS = 300;
    private static final int FULL_CONNECTION_TIMEOUT_S = 60;
    private static final int CONNECTION_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = FULL_CONNECTION_TIMEOUT_S * 1000;
    private static final Map<Pair<String, Integer>, HttpTransportClient> instances = new HashMap<>();
    private static HttpTransportClient instance;
    private final CloseableHttpClient httpClient;

    private HttpTransportClient() {
        this(null, 0);
    }

    private HttpTransportClient(@Nullable String proxyHost, int proxyPort) {
        CookieStore cookieStore = new BasicCookieStore();
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .setConnectTimeout(CONNECTION_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

        connectionManager.setMaxTotal(MAX_SIMULTANEOUS_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_SIMULTANEOUS_CONNECTIONS);

        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore)
                .setUserAgent(USER_AGENT)
                .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);

        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);

            builder.setSSLSocketFactory(sslSocketFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            logger.warn("Failed set SSL connection socket factory", e);
        }

        if (proxyHost != null) {
            HttpHost host = new HttpHost(proxyHost, proxyPort);
            builder.setProxy(host);
        }

        httpClient = builder.build();
    }

    @NotNull
    public static HttpTransportClient getInstance() {
        if (instance == null) {
            instance = new HttpTransportClient();
        }

        return instance;
    }

    @NotNull
    public static HttpTransportClient getInstance(@Nullable String proxyHost, int proxyPort) {
        Pair<String, Integer> proxy = new Pair<>(proxyHost, proxyPort);

        if (!instances.containsKey(proxy)) {
            HttpTransportClient instance = new HttpTransportClient(proxyHost, proxyPort);
            instances.put(proxy, instance);
            return instance;
        }

        return instances.get(proxy);
    }

    @NotNull
    @Override
    public ClientResponse post(@NotNull StepikApiClient stepikApiClient, @NotNull String url, @Nullable String body)
            throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE_HEADER, CONTENT_TYPE);

        return post(stepikApiClient, url, body, headers);
    }

    @NotNull
    @Override
    public ClientResponse get(@NotNull StepikApiClient stepikApiClient, @NotNull String url) throws IOException {
        return get(stepikApiClient, url, null);
    }

    @NotNull
    @Override
    public ClientResponse post(
            @NotNull StepikApiClient stepikApiClient,
            @NotNull String url,
            @Nullable String body,
            @Nullable Map<String, String> headers)
            throws IOException {
        HttpPost request = new HttpPost(url);
        if (headers != null) {
            headers.entrySet().forEach(entry -> request.setHeader(entry.getKey(), entry.getValue()));
        }
        if (body != null) {
            request.setEntity(new StringEntity(body));
        }
        return call(stepikApiClient, request);
    }

    @NotNull
    public ClientResponse get(
            @NotNull StepikApiClient stepikApiClient,
            @NotNull String url,
            @Nullable Map<String, String> headers) throws IOException {
        HttpGet request = new HttpGet(url);
        if (headers != null) {
            headers.entrySet().forEach(entry -> request.setHeader(entry.getKey(), entry.getValue()));
        }
        return call(stepikApiClient, request);
    }

    @NotNull
    private ClientResponse call(
            @NotNull StepikApiClient stepikApiClient,
            @NotNull HttpUriRequest request) throws IOException {
        HttpResponse response = httpClient.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();

        StringBuilder result = new StringBuilder();
        if (statusCode != StatusCodes.SC_NO_CONTENT) {
            try (BufferedReader content = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), ENCODING))) {

                String line;
                while ((line = content.readLine()) != null) {
                    result.append("\n").append(line);
                }

                if (result.length() > 0) {
                    result.deleteCharAt(0); // Delete first break line
                }
            }
        }

        return new ClientResponse(stepikApiClient, statusCode, result.toString(), getHeaders(response.getAllHeaders()));
    }

    @NotNull
    private Map<String, String> getHeaders(@NotNull Header[] headers) {
        Map<String, String> result = new HashMap<>();
        for (Header header : headers) {
            result.put(header.getName(), header.getValue());
        }

        return result;
    }
}
