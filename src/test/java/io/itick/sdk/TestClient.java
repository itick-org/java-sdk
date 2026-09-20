package io.itick.sdk;

import com.google.gson.Gson;
import io.itick.sdk.model.Tick;


public class TestClient {
    public static void main(String[] args) {
        // 使用真实API密钥
        String token = "8850*****************ee4127087";
        Client client = new Client(token);
        //client.setWssBaseUrl("wss://api0.itick.org");
        client.setMessageHandler(message -> {
            System.out.println("Received message: " + message);
        });

        client.setErrorHandler(error -> {
            System.out.println("Received error: " + error.getMessage());
        });

        Gson gson = new Gson();

        // 测试基础模块
        System.out.println("=== 测试基础模块 ===");
        try {
            Object symbolList = client.getSymbolList();
            System.out.println("getSymbolList 成功:"+ gson.toJson(symbolList));
        } catch (Exception e) {
            System.out.println("getSymbolList 错误: " + e.getMessage());
        }

        try {
            Object symbolHolidays = client.getSymbolHolidays();
            System.out.println("getSymbolHolidays 成功:"+ gson.toJson(symbolHolidays));
        } catch (Exception e) {
            System.out.println("getSymbolHolidays 错误: " + e.getMessage());
        }

        // 测试股票模块
        System.out.println("\n=== 测试股票模块 ===");
        try {
            Object stockInfo = client.getStockInfo("us", "AAPL");
            System.out.println("getStockInfo 成功:"+ gson.toJson(stockInfo));
        } catch (Exception e) {
            System.out.println("getStockInfo 错误: " + e.getMessage());
        }

        try {
            Tick stockTick = client.getStockTick("us", "AAPL");
            System.out.println("getStockTick 成功:"+ gson.toJson(stockTick));
        } catch (Exception e) {
            System.out.println("getStockTick 错误: " + e.getMessage());
        }

        // 测试指数模块
        System.out.println("\n=== 测试指数模块 ===");
        try {
            Tick indicesTick = client.getIndicesTick("gb", "SPX");
            System.out.println("getIndicesTick 成功:"+ gson.toJson(indicesTick));
        } catch (Exception e) {
            System.out.println("getIndicesTick 错误: " + e.getMessage());
        }

        // 测试期货模块
        System.out.println("\n=== 测试期货模块 ===");
        try {
            Tick futureTick = client.getFutureTick("us", "ES");
            System.out.println("getFutureTick 成功:"+ gson.toJson(futureTick));
        } catch (Exception e) {
            System.out.println("getFutureTick 错误: " + e.getMessage());
        }

        // 测试基金模块
        System.out.println("\n=== 测试基金模块 ===");
        try {
            Tick fundTick = client.getFundTick("us", "SPY");
            System.out.println("getFundTick 成功:"+ gson.toJson(fundTick));
        } catch (Exception e) {
            System.out.println("getFundTick 错误: " + e.getMessage());
        }

        // 测试外汇模块
        System.out.println("\n=== 测试外汇模块 ===");
        try {
            Tick forexTick = client.getForexTick("gb", "EURUSD");
            System.out.println("getForexTick 成功:"+ gson.toJson(forexTick));
        } catch (Exception e) {
            System.out.println("getForexTick 错误: " + e.getMessage());
        }

        // 测试加密货币模块
        System.out.println("\n=== 测试加密货币模块 ===");
        try {
            Tick cryptoTick = client.getCryptoTick("ba", "BTCUSDT");
            System.out.println("getCryptoTick 成功:"+ gson.toJson(cryptoTick));
        } catch (Exception e) {
            System.out.println("getCryptoTick 错误: " + e.getMessage());
        }

        // 测试WebSocket
        System.out.println("\n=== 测试WebSocket ===");
        try {
            client.connectStockWebSocket();
            System.out.println("connectStockWebSocket 成功");
            client.closeWebSocket();
        } catch (Exception e) {
            System.out.println("connectStockWebSocket 错误: " + e.getMessage());
        }

        System.out.println("\n所有测试完成");
    }
}
