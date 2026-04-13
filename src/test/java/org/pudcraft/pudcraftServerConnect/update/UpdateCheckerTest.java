package org.pudcraft.pudcraftServerConnect.update;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    private static final String GITHUB_API =
        "https://api.github.com/repos/Pudcraft-Teams/pudcraft-server-connect/releases/latest";

    @TempDir
    Path tempDir;

    @Test
    void retries_after_failed_partial_download_without_leaving_final_file_behind() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Path dataDir = pluginsDir.resolve("pudcraft-server-connect");
        Files.createDirectories(dataDir);
        Files.writeString(pluginsDir.resolve("pudcraft-server-connect-1.0.0.jar"), "existing plugin jar");

        PudcraftServerConnect plugin = allocatePlugin();
        PluginDescriptionFile description = new PluginDescriptionFile(
            "pudcraft-server-connect",
            "1.0.0",
            "org.pudcraft.pudcraftServerConnect.PudcraftServerConnect"
        );
        setJavaPluginField(plugin, "description", description);
        setJavaPluginField(plugin, "dataFolder", dataDir.toFile());
        setJavaPluginField(plugin, "file", pluginsDir.resolve("pudcraft-server-connect-1.0.0.jar").toFile());
        setJavaPluginField(plugin, "server", serverWithLogger(Logger.getLogger("UpdateCheckerTest")));
        setJavaPluginField(plugin, "logger", new PluginLogger(plugin));

        ConfigManager configManager = new ConfigManager(plugin);

        String downloadUrl = "https://example.com/pudcraft-server-connect-1.0.1.jar";
        byte[] completeJar = "complete update jar bytes".getBytes(StandardCharsets.UTF_8);
        byte[] partialJar = "partial".getBytes(StandardCharsets.UTF_8);

        UpdateChecker checker = new UpdateChecker(plugin, configManager);
        injectHttpClient(checker, new StubHttpClient(downloadUrl, partialJar, completeJar));

        checker.checkAndDownload((CommandSender) null);

        Path finalTarget = pluginsDir.resolve("update").resolve("pudcraft-server-connect-1.0.0.jar");
        assertFalse(Files.exists(finalTarget), "interrupted downloads should not leave a final jar behind");

        checker.checkAndDownload((CommandSender) null);

        assertTrue(Files.exists(finalTarget), "retry should download the final jar");
        assertArrayEquals(completeJar, Files.readAllBytes(finalTarget), "retry should replace the partial download");
    }

    private static PudcraftServerConnect allocatePlugin() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (PudcraftServerConnect) allocateInstance.invoke(unsafe, PudcraftServerConnect.class);
    }

    private static void setJavaPluginField(Object plugin, String fieldName, Object value) throws Exception {
        Field field = plugin.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private static Server serverWithLogger(Logger logger) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getLogger".equals(method.getName())) {
                return logger;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(boolean.class)) return false;
            if (returnType.equals(int.class)) return 0;
            if (returnType.equals(long.class)) return 0L;
            if (returnType.equals(double.class)) return 0.0d;
            if (returnType.equals(float.class)) return 0.0f;
            if (returnType.equals(short.class)) return (short) 0;
            if (returnType.equals(byte.class)) return (byte) 0;
            if (returnType.equals(char.class)) return (char) 0;
            return null;
        };
        return (Server) Proxy.newProxyInstance(
            Server.class.getClassLoader(),
            new Class<?>[] { Server.class },
            handler
        );
    }

    private static void injectHttpClient(UpdateChecker checker, HttpClient httpClient) throws Exception {
        Field field = UpdateChecker.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(checker, httpClient);
    }

    private static final class StubHttpClient extends HttpClient {
        private final String downloadUrl;
        private final byte[] firstDownloadPayload;
        private final byte[] secondDownloadPayload;
        private final AtomicInteger downloadAttempts = new AtomicInteger();

        private StubHttpClient(String downloadUrl, byte[] firstDownloadPayload, byte[] secondDownloadPayload) {
            this.downloadUrl = downloadUrl;
            this.firstDownloadPayload = firstDownloadPayload;
            this.secondDownloadPayload = secondDownloadPayload;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
            String uri = request.uri().toString();
            if (GITHUB_API.equals(uri)) {
                String releaseJson = "{"
                    + "\"tag_name\":\"v1.0.1\","
                    + "\"assets\":[{"
                    + "\"name\":\"pudcraft-server-connect-1.0.1.jar\","
                    + "\"browser_download_url\":\"" + downloadUrl + "\""
                    + "}]"
                    + "}";
                return cast(new SimpleResponse<>(request, releaseJson));
            }
            if (downloadUrl.equals(uri)) {
                int attempt = downloadAttempts.incrementAndGet();
                if (attempt == 1) {
                    return cast(new SimpleResponse<>(request, failingStream(firstDownloadPayload, 3)));
                }
                if (attempt == 2) {
                    return cast(new SimpleResponse<>(request, new java.io.ByteArrayInputStream(secondDownloadPayload)));
                }
                throw new AssertionError("Unexpected download attempt " + attempt);
            }
            throw new AssertionError("Unexpected request URI: " + uri);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @SuppressWarnings("unchecked")
        private static <T> HttpResponse<T> cast(HttpResponse<?> response) {
            return (HttpResponse<T>) response;
        }
    }

    private static final class SimpleResponse<T> implements HttpResponse<T> {
        private final HttpRequest request;
        private final T body;

        private SimpleResponse(HttpRequest request, T body) {
            this.request = request;
            this.body = body;
        }

        @Override public int statusCode() { return 200; }
        @Override public HttpRequest request() { return request; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public T body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }

    private static InputStream failingStream(byte[] data, int failAfterBytes) {
        return new InputStream() {
            private int index;

            @Override
            public int read() throws IOException {
                if (index == failAfterBytes) {
                    throw new IOException("simulated interrupted download");
                }
                if (index >= data.length) {
                    return -1;
                }
                return data[index++] & 0xFF;
            }
        };
    }
}
