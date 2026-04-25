# iTick Java SDK

Java 语言版本的 iTick API SDK，提供基础、股票、指数、期货、基金、外汇、加密货币数据的 REST API 查询和 WebSocket 实时数据订阅功能。

# 官网地址：[https://itick.org](https://itick.org)

## 功能特性

- 支持 REST API 查询基础、股票、指数、期货、基金、外汇、加密货币数据
- 支持 WebSocket 实时数据订阅
- 自动重连机制
- 心跳保持连接
- 回调式事件处理

## 安装

### Maven

在您的 `pom.xml` 中添加以下依赖：

```xml
<dependency>
    <groupId>io.itick</groupId>
    <artifactId>sdk</artifactId>
    <version>0.1.1</version>
</dependency>
```

### Gradle

在您的 `build.gradle` 中添加以下依赖：

```gradle
implementation 'io.itick:sdk:0.1.1'
```

### 从源码构建

```bash
cd java
mvn clean install
```

## 快速开始

### 初始化客户端

```java
import io.itick.sdk.Client;

public class Example {
    public static void main(String[] args) {
        String token = "your_api_token";
        Client client = new Client(token);
    }
}
```

### REST API 使用

#### 外汇数据查询

```java
// 获取外汇实时成交
var tick = client.getForexTick("GB", "EURUSD");
System.out.println(tick);

// 获取外汇实时报价
var quote = client.getForexQuote("GB", "EURUSD");
System.out.println(quote);

// 获取外汇实时盘口
var depth = client.getForexDepth("GB", "EURUSD");
System.out.println(depth);

// 获取外汇历史K线
var kline = client.getForexKline("GB", "EURUSD", 2, 10, null);
for (var k : kline) {
    System.out.println(k);
}
```

#### 股票数据查询

```java
// 获取股票实时成交
var tick = client.getStockTick("US", "AAPL");
System.out.println(tick);

// 获取股票实时报价
var quote = client.getStockQuote("US", "AAPL");
System.out.println(quote);

// 获取股票实时盘口
var depth = client.getStockDepth("US", "AAPL");
System.out.println(depth);

// 获取股票历史K线
var kline = client.getStockKline("US", "AAPL", 2, 10, null);
for (var k : kline) {
    System.out.println(k);
}
```

#### 加密货币数据查询

```java
// 获取加密货币实时成交
var tick = client.getCryptoTick("BA", "BTCUSDT");
System.out.println(tick);

// 获取加密货币实时报价
var quote = client.getCryptoQuote("BA", "BTCUSDT");
System.out.println(quote);

// 获取加密货币实时盘口
var depth = client.getCryptoDepth("BA", "BTCUSDT");
System.out.println(depth);

// 获取加密货币历史K线
var kline = client.getCryptoKline("BA", "BTCUSDT", 2, 10, null);
for (var k : kline) {
    System.out.println(k);
}
```

### WebSocket 使用

SDK 提供了增强的 WebSocket 功能，包括自动重连和心跳保持，用户无需手动管理连接状态。

#### 设置回调函数

```java
// 设置消息处理器
client.setMessageHandler(message -> {
    System.out.println("Received WebSocket message: " + message);
});

// 设置错误处理器
client.setErrorHandler(error -> {
    System.err.println("WebSocket error: " + error.getMessage());
});

// 设置打开处理器
client.setOpenHandler(() -> {
    System.out.println("WebSocket connected");
});

// 设置关闭处理器
client.setCloseHandler(() -> {
    System.out.println("WebSocket closed");
});
```

#### 连接和订阅

```java
// 连接外汇 WebSocket
client.connectForexWebSocket();

// 发送订阅消息
client.sendWebSocketMessage("{\"action\": \"subscribe\", \"codes\": [\"EURUSD\"]}");

// 等待接收消息
Thread.sleep(5000);

// 检查连接状态
System.out.println("WebSocket connected: " + client.isWebSocketConnected());

// 关闭 WebSocket
client.closeWebSocket();
```

#### 其他 WebSocket 连接

```java
// 连接股票 WebSocket
client.connectStockWebSocket();

// 连接加密货币 WebSocket
client.connectCryptoWebSocket();
```

## API 接口列表

### 基础 (Basics)

| 方法 | 说明 |
|------|------|
| getSymbolList | 获取符号列表 |
| getSymbolHolidays | 获取节假日信息 |

### 股票 (Stock)

| 方法 | 说明 |
|------|------|
| getStockInfo | 获取股票信息 |
| getStockIPO | 获取股票IPO信息 |
| getStockSplit | 获取股票分拆信息 |
| getStockTick | 获取股票实时成交 |
| getStockQuote | 获取股票实时报价 |
| getStockDepth | 获取股票实时盘口 |
| getStockKline | 获取股票历史K线 |
| getStockTicks | 获取股票批量实时成交 |
| getStockQuotes | 获取股票批量实时报价 |
| getStockDepths | 获取股票批量实时盘口 |
| getStockKlines | 获取股票批量历史K线 |
| connectStockWebSocket | 连接股票 WebSocket |

### 指数 (Indices)

| 方法 | 说明 |
|------|------|
| getIndicesTick | 获取指数实时成交 |
| getIndicesQuote | 获取指数实时报价 |
| getIndicesDepth | 获取指数实时盘口 |
| getIndicesKline | 获取指数历史K线 |
| getIndicesTicks | 获取指数批量实时成交 |
| getIndicesQuotes | 获取指数批量实时报价 |
| getIndicesDepths | 获取指数批量实时盘口 |
| getIndicesKlines | 获取指数批量历史K线 |
| connectIndicesWebSocket | 连接指数 WebSocket |

### 期货 (Futures)

| 方法 | 说明 |
|------|------|
| getFutureTick | 获取期货实时成交 |
| getFutureQuote | 获取期货实时报价 |
| getFutureDepth | 获取期货实时盘口 |
| getFutureKline | 获取期货历史K线 |
| getFutureTicks | 获取期货批量实时成交 |
| getFutureQuotes | 获取期货批量实时报价 |
| getFutureDepths | 获取期货批量实时盘口 |
| getFutureKlines | 获取期货批量历史K线 |
| connectFutureWebSocket | 连接期货 WebSocket |

### 基金 (Funds)

| 方法 | 说明 |
|------|------|
| getFundTick | 获取基金实时成交 |
| getFundQuote | 获取基金实时报价 |
| getFundDepth | 获取基金实时盘口 |
| getFundKline | 获取基金历史K线 |
| getFundTicks | 获取基金批量实时成交 |
| getFundQuotes | 获取基金批量实时报价 |
| getFundDepths | 获取基金批量实时盘口 |
| getFundKlines | 获取基金批量历史K线 |
| connectFundWebSocket | 连接基金 WebSocket |

### 外汇 (Forex)

| 方法 | 说明 |
|------|------|
| getForexTick | 获取外汇实时成交 |
| getForexQuote | 获取外汇实时报价 |
| getForexDepth | 获取外汇实时盘口 |
| getForexKline | 获取外汇历史K线 |
| getForexTicks | 获取外汇批量实时成交 |
| getForexQuotes | 获取外汇批量实时报价 |
| getForexDepths | 获取外汇批量实时盘口 |
| getForexKlines | 获取外汇批量历史K线 |
| connectForexWebSocket | 连接外汇 WebSocket |

### 加密货币 (Crypto)

| 方法 | 说明 |
|------|------|
| getCryptoTick | 获取加密货币实时成交 |
| getCryptoQuote | 获取加密货币实时报价 |
| getCryptoDepth | 获取加密货币实时盘口 |
| getCryptoKline | 获取加密货币历史K线 |
| getCryptoTicks | 获取加密货币批量实时成交 |
| getCryptoQuotes | 获取加密货币批量实时报价 |
| getCryptoDepths | 获取加密货币批量实时盘口 |
| getCryptoKlines | 获取加密货币批量历史K线 |
| connectCryptoWebSocket | 连接加密货币 WebSocket |

## WebSocket 功能说明

### 自动重连

SDK 内置自动重连机制，当网络异常或连接断开时，会自动尝试重新连接：
- 重连间隔：5 秒
- 最大重连次数：10 次
- 重连成功后自动恢复订阅

### 心跳保持

SDK 自动维护 WebSocket 连接的心跳：
- 心跳间隔：30 秒
- 自动发送 ping 消息保持连接活跃

### 连接状态检查

```java
// 检查 WebSocket 是否连接
boolean connected = client.isWebSocketConnected();
```

## 完整示例

```java
import io.itick.sdk.Client;

public class Main {
    public static void main(String[] args) {
        try {
            // 初始化客户端
            String token = "your_api_token";
            Client client = new Client(token);

            // 设置 WebSocket 消息处理器
            client.setMessageHandler(message -> {
                System.out.println("Received WebSocket message: " + message);
            });

            // 设置 WebSocket 错误处理器
            client.setErrorHandler(error -> {
                System.err.println("WebSocket error: " + error.getMessage());
            });

            // 测试 REST API
            System.out.println("Testing forex tick...");
            var tick = client.getForexTick("GB", "EURUSD");
            System.out.println(tick);

            // 测试 WebSocket
            System.out.println("\nTesting WebSocket...");
            client.connectForexWebSocket();

            // 发送订阅消息
            client.sendWebSocketMessage("{\"action\": \"subscribe\", \"codes\": [\"EURUSD\"]}");

            // 等待接收消息
            Thread.sleep(5000);

            // 检查连接状态
            System.out.println("WebSocket connected: " + client.isWebSocketConnected());

            // 关闭 WebSocket
            client.closeWebSocket();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 系统要求

- Java 11 或更高版本
- Maven 3.6 或更高版本（用于构建）

## 依赖项

- OkHttp 4.10.0 - HTTP 客户端
- Java-WebSocket 1.5.3 - WebSocket 客户端
- Gson 2.10.1 - JSON 解析

## 文档

详细 API 文档请参考：[https://docs.itick.org](https://docs.itick.org)

## 许可证

MIT License
