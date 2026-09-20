package io.itick.sdk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.itick.sdk.model.Depth;
import io.itick.sdk.model.Kline;
import io.itick.sdk.model.Quote;
import io.itick.sdk.model.Tick;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public static final String DEFAULT_BASE_URL = "https://api.itick.org";
    public static final String DEFAULT_WSS_URL = "wss://api.itick.org";

    private String BASE_URL = DEFAULT_BASE_URL;
    private String WSS_URL = DEFAULT_WSS_URL;
    
    // WebSocket constants
    private static final int PING_INTERVAL = 3000; // 30 seconds
    private static final int RECONNECT_INTERVAL = 5000; // 5 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final String token;
    private final OkHttpClient client;
    private final Gson gson;
    private WebSocketClient wsClient;
    private String wsPath;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private int reconnectAttempts = 0;
    private Timer pingTimer;
    private MessageHandler messageHandler;
    private ErrorHandler errorHandler;
    private Set<String> subscribedSymbols = new HashSet<>();
    private Set<String> subscribedTypes = new HashSet<>();

    // Callback interfaces
    public interface MessageHandler {
        void onMessage(String message);
    }

    public interface ErrorHandler {
        void onError(Exception error);
    }

    public Client(String token) {
        this.token = token;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    private <T> T get(String path, Map<String, String> params, Class<T> responseType) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + path);
        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("accept", "application/json")
                .addHeader("token", token)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception("API error: " + response.message());
        }

        String responseBody = response.body().string();
        // 构建 ApiResponse<T> 的泛型类型
        ParameterizedType apiResponseType = new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{responseType};
            }

            @Override
            public Type getRawType() {
                return ApiResponse.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
        ApiResponse<T> apiResponse = gson.fromJson(responseBody, apiResponseType);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }

        return apiResponse.getData();
    }

    private <T> T get(String path, Map<String, String> params, TypeToken<T> responseType) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + path);
        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("accept", "application/json")
                .addHeader("token", token)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception("API error: " + response.message());
        }

        String responseBody = response.body().string();
        // 构建 ApiResponse<T> 的泛型类型
        T apiResponse = gson.fromJson(responseBody, responseType);

        return apiResponse;
    }

    // WebSocket methods with enhanced functionality

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    public void connectWebSocket(String path) throws URISyntaxException {
        this.wsPath = path + "?token=" + this.token;
        this.isRunning.set(true);
        connectWebSocketInternal();
    }

    private void connectWebSocketInternal() {
        try {
            URI uri = new URI(WSS_URL + wsPath);
            Map<String, String> headers = new HashMap<>();
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());

            this.wsClient = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("Connected to WebSocket server" + WSS_URL);
                    isConnected.set(true);
                    reconnectAttempts = 0;
                    startPingTimer();
                    //重新开启订阅
                    if (!subscribedSymbols.isEmpty() && !subscribedTypes.isEmpty()) {
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (isConnected.get()) {
                                    subscribedSymbol(subscribedSymbols, subscribedTypes);
                                }
                            }
                        }, 1000); // 延迟 1 秒后订阅
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (messageHandler != null) {
                        messageHandler.onMessage(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected.set(false);
                    System.err.println("WebSocket closed - Code: " + code +
                            ", Reason: " + reason +
                            ", Remote: " + remote +
                            ", Time:" + System.currentTimeMillis());

                    stopPingTimer();
                    if (isRunning.get()) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    if (errorHandler != null) {
                        errorHandler.onError(ex);
                    }
                    isConnected.set(false);
                    stopPingTimer();
                    if (isRunning.get()) {
                        scheduleReconnect();
                    }
                }
            };
            this.wsClient.setConnectionLostTimeout(0);
            this.wsClient.setSocketFactory(sc.getSocketFactory());
            this.wsClient.connect();
        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.onError(e);
            }
        }
    }

    private void startPingTimer() {
        stopPingTimer();
        pingTimer = new Timer();
        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (wsClient != null && isConnected.get()) {
                    Map<String, Object> pingData = new HashMap<>();
                    pingData.put("ac", "ping");
                    pingData.put("params", System.currentTimeMillis());
                    wsClient.send(gson.toJson(pingData));
                }
            }
        }, PING_INTERVAL, PING_INTERVAL);
    }

    private void stopPingTimer() {
        if (pingTimer != null) {
            pingTimer.cancel();
            pingTimer = null;
        }
    }

    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (errorHandler != null) {
                errorHandler.onError(new Exception("Max reconnect attempts reached"));
            }
            return;
        }

        reconnectAttempts++;
        new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_INTERVAL);
                if (isRunning.get()) {
                    connectWebSocketInternal();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void subscribedSymbol(Set<String> symbols, Set<String> types) {
        subscribedTypes.addAll(types);
        subscribedSymbols.addAll(symbols);
        if (!subscribedSymbols.isEmpty()) {
            Map<String, String> params = new HashMap<>();
            params.put("params", String.join(",", subscribedSymbols));
            params.put("types", String.join(",", types));
            params.put("ac", "subscribe");
            // 开启订阅
            sendWebSocketMessage(gson.toJson(params));
        }
    }

    public void sendWebSocketMessage(String message) {
        if (wsClient == null || !isConnected.get()) {
            throw new IllegalStateException("WebSocket not connected");
        }
        wsClient.send(message);
    }

    public void closeWebSocket() {
        isRunning.set(false);
        stopPingTimer();
        if (wsClient != null) {
            wsClient.close();
        }
    }

    public boolean isWebSocketConnected() {
        return isConnected.get();
    }


    // 基础模块
    public List getSymbolList() throws Exception {
        return get("/symbol/list", null, List.class);
    }

    public List getSymbolHolidays() throws Exception {
        return get("/symbol/holidays", null, List.class);
    }

    // 股票模块
    public Object getStockInfo(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/info", params, Object.class);
    }

    public Object getStockIPO(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/ipo", params, Object.class);
    }

    public Object getStockSplit(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/split", params, Object.class);
    }

    public Tick getStockTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/tick", params, Tick.class);
    }

    public Quote getStockQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/quote", params, Quote.class);
    }

    public Depth getStockDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/depth", params, Depth.class);
    }

    public Kline[] getStockKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/stock/kline", params, Kline[].class);
    }

    public Map<String, Tick> getStockTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/stock/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getStockQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/stock/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getStockDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/stock/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getStockKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/stock/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectStockWebSocket() throws URISyntaxException {
        connectWebSocket("/stock");
    }

    public void subscribeStockWebSocket() throws URISyntaxException {
        connectWebSocket("/stock");
    }


    // 指数模块
    public Tick getIndicesTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/tick", params, Tick.class);
    }

    public Quote getIndicesQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/quote", params, Quote.class);
    }

    public Depth getIndicesDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/depth", params, Depth.class);
    }

    public Kline[] getIndicesKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }

        return get("/indices/kline", params, Kline[].class);
    }

    public Map<String, Tick> getIndicesTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/indices/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getIndicesQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/indices/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getIndicesDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/indices/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getIndicesKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }


        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/indices/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectIndicesWebSocket() throws URISyntaxException {
        connectWebSocket("/indices");
    }

    // 期货模块
    public Tick getFutureTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/tick", params, Tick.class);
    }

    public Quote getFutureQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/quote", params, Quote.class);
    }

    public Depth getFutureDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/depth", params, Depth.class);
    }

    public Kline[] getFutureKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/future/kline", params, Kline[].class);
    }

    public Map<String, Tick> getFutureTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/future/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getFutureQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/future/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getFutureDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/future/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getFutureKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }


        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/future/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectFutureWebSocket() throws URISyntaxException {
        connectWebSocket("/future");
    }

    // 基金模块
    public Tick getFundTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/tick", params, Tick.class);
    }

    public Quote getFundQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/quote", params, Quote.class);
    }

    public Depth getFundDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/depth", params, Depth.class);
    }

    public Kline[] getFundKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/fund/kline", params, Kline[].class);
    }

    public Map<String, Tick> getFundTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/fund/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getFundQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/fund/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getFundDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/fund/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getFundKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }

        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/fund/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectFundWebSocket() throws URISyntaxException {
        connectWebSocket("/fund");
    }

    // 外汇模块
    public Tick getForexTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/tick", params, Tick.class);
    }

    public Quote getForexQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/quote", params, Quote.class);
    }

    public Depth getForexDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/depth", params, Depth.class);
    }

    public Kline[] getForexKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/forex/kline", params, Kline[].class);
    }

    public Map<String, Tick> getForexTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/forex/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getForexQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/forex/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getForexDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));

        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/forex/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getForexKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }


        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/forex/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectForexWebSocket() throws URISyntaxException {
        connectWebSocket("/forex");
    }

    // 加密货币模块
    public Tick getCryptoTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/tick", params, Tick.class);
    }

    public Quote getCryptoQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/quote", params, Quote.class);
    }

    public Depth getCryptoDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/depth", params, Depth.class);
    }

    public Kline[] getCryptoKline(String region, String code, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/crypto/kline", params, Kline[].class);
    }

    public Map<String, Tick> getCryptoTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Tick>>> typeToken = new TypeToken<ApiResponse<Map<String, Tick>>>() {
        };
        ApiResponse<Map<String, Tick>> apiResponse = get("/crypto/ticks", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Quote> getCryptoQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Quote>>> typeToken = new TypeToken<ApiResponse<Map<String, Quote>>>() {
        };
        ApiResponse<Map<String, Quote>> apiResponse = get("/crypto/quotes", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Depth> getCryptoDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));


        TypeToken<ApiResponse<Map<String, Depth>>> typeToken = new TypeToken<ApiResponse<Map<String, Depth>>>() {
        };
        ApiResponse<Map<String, Depth>> apiResponse = get("/crypto/depths", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public Map<String, Kline[]> getCryptoKlines(String region, String[] codes, int kType, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("kType", String.valueOf(kType));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }


        TypeToken<ApiResponse<Map<String, Kline[]>>> typeToken = new TypeToken<ApiResponse<Map<String, Kline[]>>>() {
        };
        ApiResponse<Map<String, Kline[]>> apiResponse = get("/crypto/klines", params, typeToken);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }
        return apiResponse.getData();
    }

    public void connectCryptoWebSocket() throws URISyntaxException {
        connectWebSocket("/crypto");
    }

    /**
     * set HTTP Base URL
     *
     * @param httpBaseUrl HTTP Base URL
     */
    public void setHttpBaseUrl(String httpBaseUrl) {
        this.BASE_URL = httpBaseUrl;
    }

    /**
     * set WebSocket Base URL
     *
     * @param wssBaseUrl WebSocket Base URL
     */
    public void setWssBaseUrl(String wssBaseUrl) {
        this.WSS_URL = wssBaseUrl;
    }
}
