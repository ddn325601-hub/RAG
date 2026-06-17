# 日志与指标查询说明

## 常用日志字段

`traceId`：一次请求的链路追踪 ID，用于串联前端、网关、应用服务和数据库操作。

`userId`：客户账号 ID。排查客户个例问题时必须记录。

`orderId`：订单编号。订单状态异常、支付异常、售后异常必须携带。

`paymentNo`：支付流水号。支付成功未出单、重复扣费、退款失败必须携带。

`errorCode`：业务错误码，例如 `PAYMENT_CALLBACK_FAILED`、`ORDER_CREATE_FAILED`、`STOCK_LOCK_TIMEOUT`。

## 推荐查询语句

支付回调失败：
`paymentNo:<流水号> AND (payment_callback OR signature_verify_failed OR order_status_update)`

订单创建失败：
`userId:<客户ID> AND (create_order_failed OR transaction_rollback OR stock_lock_timeout)`

接口响应慢：
`traceId:<traceId> OR (path:<接口路径> AND duration:>2000)`

登录失败：
`userId:<客户ID> AND (login_failed OR verify_code_error OR account_locked)`

## 指标解释

`http_5xx_rate`：服务端错误率，超过 1% 需要关注，超过 5% 升级。

`p95_latency_ms`：接口 95 分位耗时，超过 2000ms 说明客户体验明显下降。

`mq_lag`：消息队列积压，超过 1000 表示消费明显滞后。

`db_slow_query_count`：数据库慢查询数量，订单、支付、登录链路异常时重点查看。

## 客服如何使用

客服不需要直接执行复杂查询，但要收集关键字段：客户账号、订单号、支付流水号、发生时间、错误截图。智能体可以根据这些字段生成日志查询建议和工单说明。
