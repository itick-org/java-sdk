package io.itick.sdk;

public class WebsocketTestClient {
    public static void main(String[] args) {
        try {
            // 初始化客户端
            String token = "8850**************************6ee4127087";
            Client client = new Client(token);

            // 设置 WebSocket 消息处理器
            client.setMessageHandler(message -> {
                System.out.println("Received WebSocket message: " + message);
            });

            // 设置 WebSocket 错误处理器
            client.setErrorHandler(error -> {
                System.err.println("WebSocket error: " + error.getMessage());
            });

            // 测试外汇实时成交接口
            System.out.println("Testing forex tick...");
            var tick = client.getForexTick("GB", "EURUSD");
            System.out.println(tick);

            // 测试外汇实时报价接口
            System.out.println("\nTesting forex quote...");
            var quote = client.getForexQuote("GB", "EURUSD");
            System.out.println(quote);

            // 测试外汇实时盘口接口
            System.out.println("\nTesting forex depth...");
            var depth = client.getForexDepth("GB", "EURUSD");
            System.out.println(depth);

            // 测试外汇历史K线接口
            System.out.println("\nTesting forex kline...");
            var kline = client.getForexKline("GB", "EURUSD", 2, 10, null);
            for (var k : kline) {
                System.out.println(k);
            }

            // 测试 WebSocket 连接
            System.out.println("\nTesting WebSocket...");
            try {
                client.connectForexWebSocket();
                Thread.sleep(3000);

                // 发送订阅消息
                /**
                 * {
                 *   "ac": "subscribe",
                 *   "params": "AAPL$US,TSLA$US",
                 *   "types": "depth,quote"
                 * }
                 */
                client.sendWebSocketMessage("{\"ac\": \"subscribe\", \"params\": \"EURUSD$GB\",\"types\": \"quote\"}");
                
                // 等待接收消息
                System.out.println("Waiting for WebSocket messages...");
                Thread.sleep(10000);
                
                // 检查连接状态
                System.out.println("WebSocket connected: " + client.isWebSocketConnected());
                
            } catch (Exception e) {
                System.err.println("WebSocket error: " + e.getMessage());
            } finally {
                // 关闭 WebSocket
                client.closeWebSocket();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
