package org.gable.blendata.nextmove.client.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
@Slf4j
@Configuration
@Getter
public class AppConfig {

    @Value("${client.url-scheme}")
    private String clientUrlScheme;
    @Value("${const.path.config.hadoop-core-site}")
    private String hadoopCoreSitePath;
    @Value("${const.path.config.license}")
    private String licenseFilePath;
    @Value("${const.path.config.task}")
    private String taskConfigurePath;
    @Value("${server.ssl.key-store}")
    private String keyStore;
    @Value("${server.ssl.key-store-password}")
    private String keyStorePassword;
    @Value("${server.ssl.trust-store}")
    private String trustStore;
    @Value("${server.ssl.trust-store-password}")
    private String trustStorePassword;
    @Value("${app.id}")
    private String appId;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) throws Exception {
        // Set up SSL context
        SSLContext sslContext = createSSLContext();
        // Create an HttpClient with the custom SSL context
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();

        // Set the HttpClient in the RestTemplate
        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean(name = "directRestTemplate")
    public RestTemplate directRestTemplate(RestTemplateBuilder builder) throws Exception {
        // Set up SSL context
        SSLContext sslContext = createSSLContext();
        // Create an HttpClient with the custom SSL context
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }


    @Bean
    @LoadBalanced
    public WebClient webClient(WebClient.Builder builder) throws Exception {
        // Load the keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream keyStoreInputStream = new FileInputStream(this.keyStore)) {
            keyStore.load(keyStoreInputStream, this.keyStorePassword.toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, this.keyStorePassword.toCharArray());

        // Load the truststore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream trustStoreInputStream = new FileInputStream(this.trustStore)) {
            trustStore.load(trustStoreInputStream, this.trustStorePassword.toCharArray());
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        // Set up SSL context with stricter hostname verification
        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(keyManagerFactory.getKeyManagers()[0])  // KeyStore (client certificate)
                .trustManager(trustManagerFactory.getTrustManagers()[0])  // TrustStore (trusted certificates)
                .build();

        // Create a custom HttpClient for WebClient with improved configurations
        HttpClient client = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext))
                .tcpConfiguration(tcpClient -> tcpClient
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10 seconds connect timeout
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true) // Disable Nagle's algorithm
                        .doOnConnected(conn -> {
                            log.info("Connection established to: {}", conn.channel().remoteAddress());
                        })
                );

        // Configure WebClient with the custom HttpClient
        return builder
                .clientConnector(new ReactorClientHttpConnector(client))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }


    // Helper to load a keystore from file
    private KeyStore loadKeyStore(String path, String password) {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(inputStream, password.toCharArray());
            return ks;
        } catch (Exception e) {
            log.error("{} !!!Error loading keystore: {}", AppConst.PREFIX_LOG, path, e);
            throw new RuntimeException("Failed to load keystore: " + path, e);
        }
    }

    private SSLContext createSSLContext() {
        try {
            KeyStore keyStoreInstance = loadKeyStore(this.keyStore, this.keyStorePassword);
            KeyStore trustStoreInstance = loadKeyStore(this.trustStore, this.trustStorePassword);
            return SSLContextBuilder.create()
                    .loadKeyMaterial(keyStoreInstance, this.keyStorePassword.toCharArray())
                    .loadTrustMaterial(trustStoreInstance, null)
                    .build();
        } catch (Exception e) {
            log.error("{} !!!Error creating SSLContext", AppConst.PREFIX_LOG, e);
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }
}
