package io.github.itick.sdk;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private static final String BASE_URL = "https://api.itick.org";
    private static final String WSS_URL = "wss://api.itick.org";
    
    // WebSocket constants
    private static final int PING_INTERVAL = 30000; // 30 seconds
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
        ApiResponse<?> apiResponse = gson.fromJson(responseBody, ApiResponse.class);
        if (apiResponse.getCode() != 0) {
            throw new Exception("API error: " + apiResponse.getMsg());
        }

        String dataJson = gson.toJson(apiResponse.getData());
        return gson.fromJson(dataJson, responseType);
    }

    // WebSocket methods with enhanced functionality
    
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }
    
    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }
    
    public void connectWebSocket(String path) throws URISyntaxException {
        this.wsPath = path+"?token="+this.token;
        this.isRunning.set(true);
        connectWebSocketInternal();
    }
    
    private void connectWebSocketInternal() {
        try {
            URI uri = new URI(WSS_URL + wsPath);
            this.wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    isConnected.set(true);
                    reconnectAttempts = 0;
                    startPingTimer();
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
            this.wsClient.connect();
        } catch (URISyntaxException e) {
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
                    wsClient.send("ping");
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
    public Object getSymbolList() throws Exception {
        return get("/symbol/list", null, Object.class);
    }

    public Object getSymbolHolidays() throws Exception {
        return get("/symbol/holidays", null, Object.class);
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

    public Object getStockTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/tick", params, Object.class);
    }

    public Object getStockQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/quote", params, Object.class);
    }

    public Object getStockDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/stock/depth", params, Object.class);
    }

    public Object[] getStockKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/stock/kline", params, Object[].class);
    }

    public Map<String, Object> getStockTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/stock/ticks", params, HashMap.class);
    }

    public Map<String, Object> getStockQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/stock/quotes", params, HashMap.class);
    }

    public Map<String, Object> getStockDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/stock/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getStockKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/stock/klines", params, HashMap.class);
    }

    public void connectStockWebSocket() throws URISyntaxException {
        connectWebSocket("/stock");
    }

    // 指数模块
    public Object getIndicesTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/tick", params, Object.class);
    }

    public Object getIndicesQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/quote", params, Object.class);
    }

    public Object getIndicesDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/indices/depth", params, Object.class);
    }

    public Object[] getIndicesKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/indices/kline", params, Object[].class);
    }

    public Map<String, Object> getIndicesTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/indices/ticks", params, HashMap.class);
    }

    public Map<String, Object> getIndicesQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/indices/quotes", params, HashMap.class);
    }

    public Map<String, Object> getIndicesDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/indices/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getIndicesKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/indices/klines", params, HashMap.class);
    }

    public void connectIndicesWebSocket() throws URISyntaxException {
        connectWebSocket("/indices");
    }

    // 期货模块
    public Object getFutureTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/tick", params, Object.class);
    }

    public Object getFutureQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/quote", params, Object.class);
    }

    public Object getFutureDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/future/depth", params, Object.class);
    }

    public Object[] getFutureKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/future/kline", params, Object[].class);
    }

    public Map<String, Object> getFutureTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/future/ticks", params, HashMap.class);
    }

    public Map<String, Object> getFutureQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/future/quotes", params, HashMap.class);
    }

    public Map<String, Object> getFutureDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/future/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getFutureKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/future/klines", params, HashMap.class);
    }

    public void connectFutureWebSocket() throws URISyntaxException {
        connectWebSocket("/future");
    }

    // 基金模块
    public Object getFundTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/tick", params, Object.class);
    }

    public Object getFundQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/quote", params, Object.class);
    }

    public Object getFundDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/fund/depth", params, Object.class);
    }

    public Object[] getFundKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/fund/kline", params, Object[].class);
    }

    public Map<String, Object> getFundTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/fund/ticks", params, HashMap.class);
    }

    public Map<String, Object> getFundQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/fund/quotes", params, HashMap.class);
    }

    public Map<String, Object> getFundDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/fund/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getFundKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/fund/klines", params, HashMap.class);
    }

    public void connectFundWebSocket() throws URISyntaxException {
        connectWebSocket("/fund");
    }

    // 外汇模块
    public Object getForexTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/tick", params, Object.class);
    }

    public Object getForexQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/quote", params, Object.class);
    }

    public Object getForexDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/forex/depth", params, Object.class);
    }

    public Object[] getForexKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/forex/kline", params, Object[].class);
    }

    public Map<String, Object> getForexTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/forex/ticks", params, HashMap.class);
    }

    public Map<String, Object> getForexQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/forex/quotes", params, HashMap.class);
    }

    public Map<String, Object> getForexDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/forex/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getForexKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/forex/klines", params, HashMap.class);
    }

    public void connectForexWebSocket() throws URISyntaxException {
        connectWebSocket("/forex");
    }

    // 加密货币模块
    public Object getCryptoTick(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/tick", params, Object.class);
    }

    public Object getCryptoQuote(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/quote", params, Object.class);
    }

    public Object getCryptoDepth(String region, String code) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        return get("/crypto/depth", params, Object.class);
    }

    public Object[] getCryptoKline(String region, String code, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("code", code);
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/crypto/kline", params, Object[].class);
    }

    public Map<String, Object> getCryptoTicks(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/crypto/ticks", params, HashMap.class);
    }

    public Map<String, Object> getCryptoQuotes(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/crypto/quotes", params, HashMap.class);
    }

    public Map<String, Object> getCryptoDepths(String region, String[] codes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        return get("/crypto/depths", params, HashMap.class);
    }

    public Map<String, Object[]> getCryptoKlines(String region, String[] codes, int period, int limit, Long end) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("region", region);
        params.put("codes", String.join(",", codes));
        params.put("period", String.valueOf(period));
        params.put("limit", String.valueOf(limit));
        if (end != null) {
            params.put("end", String.valueOf(end));
        }
        return get("/crypto/klines", params, HashMap.class);
    }

    public void connectCryptoWebSocket() throws URISyntaxException {
        connectWebSocket("/crypto");
    }
}
