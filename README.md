# 接口幂等服务 (Idempotent Service)

一个基于 Spring Boot + Redis 的通用接口幂等校验服务，为业务系统提供统一的幂等校验能力，避免重复提交、重复扣款、重复创建订单、重复消费消息等问题。

## 技术栈

- Java 1.8+
- Spring Boot 2.7.x
- Spring AOP
- Redis
- Lombok
- Maven

## 项目结构

```
idempotent-service/
├── src/main/java/com/idempotent/
│   ├── IdempotentApplication.java      # 启动类
│   ├── annotation/
│   │   └── Idempotent.java             # 幂等注解
│   ├── aspect/
│   │   └── IdempotentAspect.java       # AOP切面实现
│   ├── config/
│   │   ├── RedisConfig.java            # Redis配置
│   │   └── IdempotentProperties.java   # 幂等配置属性
│   ├── controller/
│   │   └── IdempotentController.java   # 测试接口
│   ├── dto/
│   │   └── OrderCreateDTO.java         # 订单创建DTO
│   ├── enums/
│   │   └── IdempotentTypeEnum.java     # 幂等类型枚举
│   ├── exception/
│   │   ├── IdempotentException.java    # 幂等异常
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── service/
│   │   ├── TokenService.java           # 令牌服务接口
│   │   └── impl/
│   │       └── TokenServiceImpl.java   # 令牌服务实现
│   └── util/
│       ├── RedisUtil.java              # Redis工具类
│       └── Result.java                 # 统一返回结果
└── src/main/resources/
    └── application.yml                 # 配置文件
```

## 核心功能

### 三种幂等校验模式

1. **TOKEN 模式** - 请求前先获取令牌，请求时携带令牌，用完即删
   - 适用场景：表单提交、订单创建等
   
2. **PARAM 模式** - 根据请求参数生成唯一标识
   - 适用场景：消息消费、回调通知等
   
3. **TOKEN_AND_PARAM 模式** - 令牌 + 参数双重校验
   - 适用场景：高安全性要求的支付场景

### 主要特性

- 基于 Redis + Lua 思想的原子性校验
- 支持自定义过期时间
- 支持自定义幂等 key
- 支持自定义错误提示
- AOP 切面式接入，业务代码无侵入
- 支持业务异常回滚时自动释放幂等锁

## 快速开始

### 1. 环境准备

确保已安装并启动 Redis。

### 2. 配置 Redis

修改 `application.yml` 中的 Redis 配置：

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password:
    database: 0
```

### 3. 启动项目

```bash
mvn spring-boot:run
```

服务默认端口: 8080
上下文路径: /idempotent

## 使用方式

### 方式一：TOKEN 模式

1. **获取幂等令牌**

```bash
GET /idempotent/api/token
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```

2. **请求携带令牌**

在请求头中添加 `Idempotent-Token`：

```bash
POST /idempotent/api/order/create
Header: Idempotent-Token: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Body:
{
  "orderNo": "ORD20240101001",
  "productName": "测试商品",
  "amount": 99.99,
  "quantity": 1
}
```

3. **代码中使用注解**

```java
@PostMapping("/order/create")
@Idempotent(message = "订单正在处理中，请勿重复提交")
public Result<Map<String, Object>> createOrder(@RequestBody OrderCreateDTO orderDTO) {
    // 业务逻辑
}
```

### 方式二：PARAM 模式

根据指定参数进行幂等校验：

```java
@PostMapping("/message/consume")
@Idempotent(
    type = IdempotentTypeEnum.PARAM,
    paramName = "messageId",
    expireTime = 86400,
    deleteKeyWhenFinish = false,
    message = "消息已消费，请勿重复消费"
)
public Result<String> consumeMessage(@RequestParam String messageId,
                                     @RequestParam String content) {
    // 消息消费逻辑
}
```

### 方式三：TOKEN_AND_PARAM 模式

双重保证，既需要令牌又校验参数：

```java
@PostMapping("/payment/submit")
@Idempotent(
    type = IdempotentTypeEnum.TOKEN_AND_PARAM,
    paramName = "orderNo",
    expireTime = 300,
    message = "支付请求正在处理中，请勿重复支付"
)
public Result<Map<String, Object>> submitPayment(@RequestParam String orderNo,
                                                 @RequestParam String payMethod) {
    // 支付逻辑
}
```

## 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| type | IdempotentTypeEnum | TOKEN | 幂等类型：TOKEN / PARAM / TOKEN_AND_PARAM |
| prefix | String | "" | 幂等 key 前缀 |
| key | String | "" | 幂等 key（不填默认用类名+方法名） |
| paramName | String | "" | PARAM 模式下指定参数名 |
| expireTime | long | 600 | 过期时间（秒） |
| timeUnit | TimeUnit | SECONDS | 时间单位 |
| message | String | "请勿重复提交" | 重复提交时的提示信息 |
| deleteKeyWhenFinish | boolean | true | 方法执行完成后是否删除 key |

## API 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/token | 获取幂等令牌 |
| GET | /api/token/{businessKey} | 获取业务相关的幂等令牌 |
| POST | /api/order/create | 创建订单（TOKEN模式） |
| POST | /api/order/create-by-param | 创建订单（PARAM模式） |
| POST | /api/payment/submit | 提交支付（TOKEN模式） |
| POST | /api/message/consume | 消费消息（PARAM模式） |
| GET | /api/order/{orderNo} | 查询订单 |

## 配置项说明

```yaml
idempotent:
  token:
    prefix: "idempotent:token:"    # Redis key 前缀
    expire-time: 600               # 令牌默认过期时间（秒）
    header-name: "Idempotent-Token" # 请求头名称
```

## 注意事项

1. 确保 Redis 服务正常运行
2. TOKEN 模式下，令牌使用后会立即删除
3. PARAM 模式下，如果 `deleteKeyWhenFinish = false`，幂等 key 会在过期后自动删除
4. 高并发场景下，建议使用 TOKEN 模式，配合前端防重按钮
5. 对于消息消费场景，建议使用 PARAM 模式并设置较长的过期时间

## License

MIT
