# tikectsystem_pro

`tikectsystem_pro` 是一个基于 Spring Cloud Alibaba 的分布式票务系统示例项目，配套 Vue 3 前端。项目按微服务拆分，覆盖票务平台常见业务：用户注册登录、节目展示、分类检索、实名购票人管理、下单、订单管理、简化支付、订单超时取消、网关转发、缓存、分库分表和消息队列。

本项目适合用于学习微服务拆分、高并发下单链路、ShardingSphere 分库分表、Redis 缓存、Kafka 异步解耦、Spring Cloud Gateway 路由和 Vue 3 前后端联调。

## 技术栈

后端核心：

- Java 17
- Spring Boot 3.3.0
- Spring Cloud 2023.0.2
- Spring Cloud Alibaba 2023.0.1.0
- Nacos 注册发现与配置管理
- Spring Cloud Gateway 网关
- OpenFeign 服务间调用
- MyBatis-Plus 数据访问
- ShardingSphere JDBC 分库分表
- Redis / Redisson 缓存与分布式锁
- Kafka 异步消息与订单延迟处理
- Seata 分布式事务
- Elasticsearch 节目搜索
- Sentinel 流控
- Spring Boot Admin 服务监控
- Knife4j / OpenAPI 接口文档

前端核心：

- Vue 3
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios
- Sass

## 项目结构

```text
.
|-- tikectsystem-common
|   `-- 通用响应、异常、常量、工具类、加解密、签名、JWT 等基础能力
|-- tikectsystem-server
|   |-- tikectsystem-gateway-service
|   |-- tikectsystem-user-service
|   |-- tikectsystem-base-data-service
|   |-- tikectsystem-program-service
|   |-- tikectsystem-order-service
|   |-- tikectsystem-pay-service
|   |-- tikectsystem-customize-service
|   |-- tikectsystem-admin-service
|   `-- tikectsystem-mybatis-plus-service
|-- tikectsystem-server-client
|   `-- 各服务对外 Feign Client
|-- tikectsystem-spring-cloud-framework
|   `-- Spring Cloud 通用自动配置、灰度、初始化和组件封装
|-- tikectsystem-redis-tool-framework
|   `-- Redis 通用封装、缓存工具、Stream 相关基础能力
|-- tikectsystem-redisson-framework
|   `-- Redisson 分布式锁封装
|-- tikectsystem-thread-pool-framework
|   `-- 线程池配置和任务线程池能力
|-- tikectsystem-id-generator-framework
|   `-- 分布式 ID 生成能力
|-- tikectsystem-elasticsearch-framework
|   `-- Elasticsearch 客户端与查询封装
|-- tikectsystem-captcha-manage-framework
|   `-- 验证码管理
|-- sql
|   `-- MySQL 初始化脚本
|-- spotless
|   `-- Java 格式化与 license header 配置
`-- vue3
    `-- Vue 3 前端工程
```

## 服务说明

| 服务 | 模块 | 默认端口 | 职责 |
| --- | --- | ---: | --- |
| 网关服务 | `tikectsystem-gateway-service` | 6085 | 统一入口、路由转发、跨域、鉴权前置能力 |
| 用户服务 | `tikectsystem-user-service` | 6082 | 用户注册、登录、用户信息、账号安全 |
| 基础数据服务 | `tikectsystem-base-data-service` | 6083 | 地区、字典、基础配置等通用数据 |
| 定制化服务 | `tikectsystem-customize-service` | 6084 | 定制业务能力 |
| 节目服务 | `tikectsystem-program-service` | 6086 | 节目、票档、座位、库存、下单前置校验 |
| 订单服务 | `tikectsystem-order-service` | 8081 | 订单创建、订单查询、订单状态流转、订单过期取消 |
| 支付服务 | `tikectsystem-pay-service` | 6087 | 简化支付确认、支付状态更新 |
| 监控服务 | `tikectsystem-admin-service` | 10082 | Spring Boot Admin 监控 |

## 网关路由

外部请求统一经过 gateway-service。网关配置位于：

```text
tikectsystem-server/tikectsystem-gateway-service/src/main/resources/application-pro.yml
```

默认路由：

| 外部路径 | 目标服务 |
| --- | --- |
| `/tikectsystem/user/**` | user-service |
| `/tikectsystem/basedata/**` | base-data-service |
| `/tikectsystem/program/**` | program-service |
| `/tikectsystem/order/**` | order-service |
| `/tikectsystem/pay/**` | pay-service |
| `/tikectsystem/customize/**` | customize-service |
| `/tikectsystem/admin/**` | admin-service |

路由使用 `StripPrefix=2`，例如：

```text
/tikectsystem/program/detail
```

会转发到 program-service 的：

```text
/detail
```

## 环境依赖

本项目默认在 Windows 环境开发，源码文件使用 UTF-8 编码。

本地开发建议准备：

| 组件 | 默认端口 | 用途 |
| --- | ---: | --- |
| JDK | 17 | 后端编译与运行 |
| Node.js | 建议 16+ | 前端依赖安装、构建和开发服务 |
| MySQL | 3306 | 业务数据存储 |
| Redis | 6379 | 缓存、分布式锁、热点数据 |
| Nacos | 8848 | 注册中心、配置中心 |
| Kafka | 9092 | 下单异步消息、订单过期处理 |
| Seata | 8091 | 分布式事务 |
| Elasticsearch | 9200 | 节目搜索 |
| Sentinel Dashboard | 8082 | 流控观察，可选 |

生产或预发环境不要把真实地址、账号、密码提交到仓库，建议使用环境变量、启动参数、配置中心或密钥管理系统注入。

## 数据库初始化

SQL 文件位于：

```text
sql/cloud/
```

初始化顺序：

1. 执行 `sql/cloud/1_tikectsystem_cloud_create_database.sql` 创建数据库。
2. 导入用户库脚本：
   - `tikectsystem_user_0.sql`
   - `tikectsystem_user_1.sql`
3. 导入节目库脚本：
   - `tikectsystem_program_0.sql`
   - `tikectsystem_program_1.sql`
4. 导入订单库脚本：
   - `tikectsystem_order_0.sql`
   - `tikectsystem_order_1.sql`
5. 导入支付库脚本：
   - `tikectsystem_pay_0.sql`
   - `tikectsystem_pay_1.sql`
6. 导入公共业务库脚本：
   - `tikectsystem_base_data.sql`
   - `tikectsystem_customize.sql`

ShardingSphere 配置文件位于各服务的 `src/main/resources/` 下，例如：

```text
tikectsystem-server/tikectsystem-user-service/src/main/resources/shardingsphere-user-local.yaml
tikectsystem-server/tikectsystem-program-service/src/main/resources/shardingsphere-program-local.yaml
tikectsystem-server/tikectsystem-order-service/src/main/resources/shardingsphere-order-local.yaml
tikectsystem-server/tikectsystem-pay-service/src/main/resources/shardingsphere-pay-local.yaml
```

新增表、调整分片键、修改分片算法时，需要同步检查业务代码、Mapper XML 和对应 ShardingSphere YAML。

## 后端构建与启动

根目录是 Maven 多模块项目，父 POM 为：

```text
pom.xml
```

常用构建命令：

```bash
mvn -DskipTests clean install
```

构建单个服务及其依赖：

```bash
mvn -DskipTests -pl tikectsystem-server/tikectsystem-program-service -am clean install
```

启动服务示例：

```bash
mvn -pl tikectsystem-server/tikectsystem-gateway-service spring-boot:run
mvn -pl tikectsystem-server/tikectsystem-user-service spring-boot:run
mvn -pl tikectsystem-server/tikectsystem-base-data-service spring-boot:run
mvn -pl tikectsystem-server/tikectsystem-program-service spring-boot:run
mvn -pl tikectsystem-server/tikectsystem-order-service spring-boot:run
mvn -pl tikectsystem-server/tikectsystem-pay-service spring-boot:run
```

本地完整联调建议启动顺序：

1. MySQL、Redis、Nacos、Kafka。
2. gateway-service。
3. user-service、base-data-service。
4. program-service、order-service。
5. pay-service。
6. customize-service、admin-service 按需启动。

## JVM 与外部依赖启动参数

部署到预发、测试或生产环境时，建议把 JVM 内存、Redis、Nacos、Kafka、Elasticsearch 和服务命名前缀通过启动参数注入。

注意：下面只展示参数名和占位符，不要在 README 中写真实地址、密码、账号或具体容量值。

```bash
-XX:MaxMetaspaceSize=<METASPACE_MAX_SIZE>
-Xmx<HEAP_MAX_SIZE>
-Dspring.data.redis.host=<REDIS_HOST>
-Dspring.data.redis.password=<REDIS_PASSWORD>
-Dspring.cloud.nacos.discovery.server-addr=<NACOS_SERVER_ADDR>
-Dspring.kafka.bootstrap-servers=<KAFKA_BOOTSTRAP_SERVERS>
-Delasticsearch.ip=<ELASTICSEARCH_ADDRESS>
-Delasticsearch.userName=<ELASTICSEARCH_USERNAME>
-Delasticsearch.passWord=<ELASTICSEARCH_PASSWORD>
-Dprefix.distinction.name=<SERVICE_NAME_PREFIX>
```

参数说明：

| 参数 | 说明 |
| --- | --- |
| `-XX:MaxMetaspaceSize` | 限制 JVM Metaspace 最大值，避免类元数据无限增长 |
| `-Xmx` | 限制 JVM 最大堆内存 |
| `-Dspring.data.redis.host` | Redis 服务地址 |
| `-Dspring.data.redis.password` | Redis 密码 |
| `-Dspring.cloud.nacos.discovery.server-addr` | Nacos 注册中心地址 |
| `-Dspring.kafka.bootstrap-servers` | Kafka Broker 地址 |
| `-Delasticsearch.ip` | Elasticsearch 地址 |
| `-Delasticsearch.userName` | Elasticsearch 用户名 |
| `-Delasticsearch.passWord` | Elasticsearch 密码 |
| `-Dprefix.distinction.name` | 服务名前缀，用于区分不同环境的服务注册命名空间 |

Windows PowerShell 示例：

```powershell
$env:JAVA_TOOL_OPTIONS="-XX:MaxMetaspaceSize=<METASPACE_MAX_SIZE> -Xmx<HEAP_MAX_SIZE> -Dspring.data.redis.host=<REDIS_HOST> -Dspring.data.redis.password=<REDIS_PASSWORD> -Dspring.cloud.nacos.discovery.server-addr=<NACOS_SERVER_ADDR> -Dspring.kafka.bootstrap-servers=<KAFKA_BOOTSTRAP_SERVERS> -Delasticsearch.ip=<ELASTICSEARCH_ADDRESS> -Delasticsearch.userName=<ELASTICSEARCH_USERNAME> -Delasticsearch.passWord=<ELASTICSEARCH_PASSWORD> -Dprefix.distinction.name=<SERVICE_NAME_PREFIX>"
```

Linux / macOS 示例：

```bash
export JAVA_TOOL_OPTIONS="-XX:MaxMetaspaceSize=<METASPACE_MAX_SIZE> -Xmx<HEAP_MAX_SIZE> -Dspring.data.redis.host=<REDIS_HOST> -Dspring.data.redis.password=<REDIS_PASSWORD> -Dspring.cloud.nacos.discovery.server-addr=<NACOS_SERVER_ADDR> -Dspring.kafka.bootstrap-servers=<KAFKA_BOOTSTRAP_SERVERS> -Delasticsearch.ip=<ELASTICSEARCH_ADDRESS> -Delasticsearch.userName=<ELASTICSEARCH_USERNAME> -Delasticsearch.passWord=<ELASTICSEARCH_PASSWORD> -Dprefix.distinction.name=<SERVICE_NAME_PREFIX>"
```

如果使用 IDE 启动服务，可将上述参数填入 Run Configuration 的 VM options。

## 前端运行

前端目录：

```text
vue3/
```

安装依赖：

```bash
cd vue3
npm install
```

启动开发服务：

```bash
npm run dev
```

构建生产包：

```bash
npm run build
```

预览生产包：

```bash
npm run preview
```

开发环境配置文件：

```text
vue3/.env.development
```

常用配置：

```text
VITE_APP_TITLE=<APP_TITLE>
VITE_APP_BASE_API=<FRONTEND_PROXY_PREFIX>
VITE_APP_URL=<GATEWAY_URL>
VITE_SIGN_FLAG=<SIGN_SWITCH>
VITE_CODE=<PLATFORM_CODE>
VITE_CREATE_ORDER_VERSION=<CREATE_ORDER_VERSION>
VITE_EXPERIENCE_ACCOUNT_FLAG=<EXPERIENCE_ACCOUNT_SWITCH>
```

前端通过 Vite proxy 访问后端网关。开发环境下，请确认 `VITE_APP_URL` 指向 gateway-service。

## 核心业务流程

### 用户与登录

用户服务负责注册、登录、用户资料、账号设置和实名信息维护。前端登录成功后会保存登录态，后续请求通过封装好的 Axios 工具发送。

相关模块：

```text
tikectsystem-server/tikectsystem-user-service
tikectsystem-server-client/tikectsystem-user-client
vue3/src/views/login.vue
vue3/src/views/register.vue
```

### 节目浏览

节目服务负责节目列表、节目详情、票档、座位和库存等数据。首页、分类页和详情页通过网关访问 program-service。

相关模块：

```text
tikectsystem-server/tikectsystem-program-service
tikectsystem-server-client/tikectsystem-program-client
vue3/src/views/index.vue
vue3/src/views/allType/index.vue
vue3/src/views/contentDetail/index.vue
```

### 下单

下单链路涉及节目服务、订单服务、Redis、Kafka 和数据库分片。

典型流程：

1. 用户在详情页选择票档、数量和购票人。
2. 前端进入确认订单页。
3. 用户点击提交订单。
4. program-service 校验库存、座位、票档等信息。
5. order-service 创建订单。
6. Redis / Kafka 参与缓存、异步解耦和订单超时处理。
7. 下单成功后跳转到支付确认页。

当前前端通过 `VITE_CREATE_ORDER_VERSION` 控制下单版本。异步下单链路建议使用支持排队和轮询结果的版本。

下单页已做防重复提交：

- 创建订单期间按钮进入 loading。
- loading 期间按钮 disabled。
- 重复点击会被前端拦截。
- 页面销毁时会清理轮询定时器。

### 支付

支付流程已简化为站内确认支付，不再跳转第三方支付平台。

流程：

1. 下单成功进入支付确认页。
2. 前端携带 `orderNumber` 查询订单详情。
3. 用户点击确认支付。
4. pay-service / order-service 完成支付状态更新。
5. 前端校验状态后跳转支付成功页。

相关页面：

```text
vue3/src/views/order/payMethod.vue
vue3/src/views/order/paySuccess.vue
vue3/src/views/orderManagement/index.vue
```

### 订单过期

订单超时取消依赖 Kafka 延迟队列方案，不再使用 Redis Stream 作为过期订单延迟队列。

重点检查：

```text
tikectsystem-server/tikectsystem-order-service
```

部署时必须保证 Kafka 地址、Topic、Consumer Group 与环境匹配。

## 配置说明

### 服务名隔离

服务注册名支持通过参数控制前缀：

```text
-Dprefix.distinction.name=<SERVICE_NAME_PREFIX>
```

该参数用于区分本地、测试、预发、生产等不同环境，避免不同环境的服务注册到同一组名称下导致调用混乱。

### Redis

Redis 主要用于：

- 登录态、验证码、临时缓存
- 热点节目与票档数据
- 分布式锁
- 下单链路的部分缓存数据

Redis 地址和密码建议通过启动参数或配置中心注入，不建议写死到仓库。

### Kafka

Kafka 主要用于：

- 下单异步消息
- 订单状态流转消息
- 订单过期取消延迟处理

本地联调时，program-service 和 order-service 都需要能访问同一 Kafka 集群。

### Elasticsearch

Elasticsearch 主要用于节目搜索。若本地只验证下单和支付流程，可以暂时不启动搜索相关链路，但涉及搜索页面或索引同步时需要启动。

### Seata

Seata 用于分布式事务。若本地不启用 Seata，应通过配置关闭，不建议直接删除事务注解或依赖。

## 代码规范

Java 代码格式化使用 Spotless：

```bash
mvn spotless:apply
mvn spotless:check
```

配置文件：

```text
spotless/spotless.xml
spotless/license-header
```

约定：

- Java 源码使用 UTF-8。
- 服务间调用通过 `tikectsystem-server-client` 中的 Feign Client。
- 不要跨服务直接引用其他服务 Controller。
- 新增分片表时，同步更新 SQL、实体、Mapper、Mapper XML 和 ShardingSphere YAML。
- 修改下单、库存、座位逻辑时，同时检查 Redis Lua 脚本和 Kafka 消息消费逻辑。

## 常见问题

### 前端请求 404 或跨域失败

检查：

- gateway-service 是否启动。
- `vue3/.env.development` 中网关地址是否正确。
- Vite proxy 前缀是否和 Axios baseURL 一致。
- 请求路径是否符合 `/tikectsystem/{service}/**` 的网关规则。

### 服务启动后注册不到 Nacos

检查：

- Nacos 地址是否正确。
- Nacos 用户名密码是否正确。
- `prefix.distinction.name` 是否符合当前环境约定。
- 本机网络是否能访问 Nacos 端口。

### 数据库连接失败

检查：

- MySQL 是否启动。
- 分片库是否已创建。
- 当前 profile 是否匹配对应的 `shardingsphere-*-local.yaml` 或 `shardingsphere-*-pro.yaml`。
- JDBC URL、用户名、密码是否由正确环境注入。

### 下单后一直排队或订单不生成

检查：

- Redis 是否可用。
- Kafka 是否可用。
- program-service 和 order-service 是否都已启动。
- 下单版本配置是否和后端实现一致。
- order-service 消费组是否正常消费消息。

### 支付后仍显示未支付

检查：

- pay-service 是否启动。
- order-service 是否能查询到该订单。
- 支付接口是否返回成功。
- 前端是否携带正确的 `orderNumber`。
- 支付成功后订单状态是否已更新为已支付。

### Elasticsearch 连接失败

检查：

- Elasticsearch 地址是否正确。
- 用户名和密码是否正确。
- 当前环境是否确实需要启动搜索链路。

## 安全建议

- 不要在 README、Git 提交、前端 `.env` 或 YAML 中提交真实密码、私钥、Token、数据库账号。
- 示例参数统一使用占位符。
- 生产环境建议使用配置中心、环境变量、密钥管理系统或容器 Secret 注入敏感信息。
- 前端签名私钥、测试账号等配置只适合本地开发，不应直接用于生产环境。

## License

本项目使用 Apache License 2.0，详见 `LICENSE`。
