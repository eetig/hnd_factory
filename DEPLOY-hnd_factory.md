# hnd_factory 容器化部署

> 本文是本项目**实际在用**的部署说明，参数都是核对过的真实值。
>
> ⚠️ 仓库里另有一份 `DEPLOY.md`，那是 img-service 的通用模板（以 `xxx-service`、8080 端口举例），
> **不要照着它配本项目** —— 端口、依赖容器名、图片路由都不一样。以本文为准。

---

## 一、核心方法：三层分离

把「不变的」和「会变的」分开，做到换服务器、换域名、换密码都不用改代码。

| 层 | 文件 | 多久变一次 | 变更代价 |
|---|---|---|---|
| **① 构建层** | `Dockerfile` | 几乎不变 | 重新构建镜像 |
| **② 配置层** | `src/main/resources/application-prod.yml` | 很少变 | 重新打 jar + 构建镜像 |
| **③ 运行层** | 1Panel compose 的环境变量 | 经常变（地址、密码、端口） | **只重建容器**，几十秒 |

**推论**：凡是「不同环境不一样」的值（数据库地址、账号密码、Nacos 地址、端口），一律走第③层环境变量，
绝不写死在代码或 yml 里。

三个配套原则：

1. **jar 在本地打，服务器只装 JRE** —— 服务器不装 Gradle、不下依赖，镜像小（~280MB vs ~1.5GB）、构建快、几乎不会因网络失败。
2. **容器之间用「容器名」互访，靠同一 docker 网络** —— 容器里的 `127.0.0.1` 指容器自己，必须用容器名。
3. **宿主机 Nginx 通过 `127.0.0.1:端口` 访问容器** —— Nginx 跑在宿主机、不在 docker 网络里。绑 `127.0.0.1` 而非 `0.0.0.0`，天然满足「微服务不暴露公网端口」。

---

## 二、关键参数

| 项 | 值 |
|---|---|
| 服务名（Nacos `spring.application.name`） | `hnd_factory` |
| jar 名 | `hnd_factory.jar`（由 `bootJar.archiveFileName` 固定） |
| 镜像名 | `hnd_factory:latest` |
| 容器名 | `hnd_factory` |
| 端口 | **8084**（容器内外一致，不做 host:container 转换） |
| 上传目录 | `/opt/hnd_factory/` |

### 依赖的外部服务

| 依赖 | 地址 | 用途 |
|---|---|---|
| Nacos | `1Panel-nacos-qquH-standalone:8848`（容器名） | 服务注册发现 |
| MySQL | `1Panel-mysql-Ipel:3306` / 库 `factory_db` | 工单、领料、入库、货物移动、权限 |
| img-service | Nacos 服务发现（宿主机 `127.0.0.1:8082`） | 图片上传/删除（Feign） |
| excel-import-service | Nacos 服务发现（宿主机 `127.0.0.1:8083`） | Excel 解析（Feign，旧版导入接口用） |
| Redis | **不使用** | Sa-Token 用默认内存存储 |
| MinIO | **不直连** | 图片全走 img-service，MinIO 的 endpoint/AK/SK 配在那边 |

> ⚠️ **依赖容器名要核对**：`1Panel-mysql-Ipel` 里是大写字母 **I**（不是小写 l）。这两个字符在等宽字体里几乎一样，
> 我们已经因为看错踩过一次。上线前务必 `docker ps --format '{{.Names}}'` 复制，不要手敲。
>
> 表现差异：**Nacos 名字写错** → 启动直接失败（响亮的）；**MySQL 名字写错** → 启动完全正常，
> 直到第一次查库才 500（静默的，更难查）。

---

## 三、四个文件

### 1. `Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8

# JVM 参数：可在 1Panel 环境变量里覆盖（改这个不用重新构建镜像）
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

WORKDIR /app

# 产物由 build.gradle 的 bootJar.archiveFileName 固定为 hnd_factory.jar
COPY hnd_factory.jar /app/hnd_factory.jar

RUN mkdir -p /app/logs

# 非 root 运行，降低容器逃逸风险
RUN groupadd -r app && useradd -r -g app -d /app app && chown -R app:app /app
USER app

EXPOSE 8084

# 用 exec 让 java 直接成为 PID 1：
#   1) docker stop 的 SIGTERM 能被 JVM 收到，走 Spring 优雅停机
#   2) 不带 exec 时 SIGTERM 只发给 sh，JVM 收不到，容器要等 10s 被 SIGKILL 强杀
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/hnd_factory.jar --spring.profiles.active=prod"]
```

### 2. `docker-compose.yml`

> 本文件**已被 `.gitignore` 排除**（含明文数据库口令，不入库）。
> 因此每个新环境都要单独拿一份，克隆仓库是拿不到的。

```yaml
services:
  hnd_factory:
    image: hnd_factory:latest
    container_name: hnd_factory
    restart: unless-stopped
    environment:
      - "TZ=Asia/Shanghai"
      # 容器内外端口保持一致，不做 host:container 转换，少一个漏设环境变量的隐患
      - "SERVER_PORT=8084"
      # ---- Nacos ----
      # 容器名互访，不可写 127.0.0.1（那是容器自己）
      - "NACOS_SERVER_ADDR=1Panel-nacos-qquH-standalone:8848"
      # 命名空间必须填 ID 而不是名称。本环境与 img-service / excel-import-service 一致：
      # 都注册在 public（public 就是公共命名空间的 ID，不是"留空"的意思）
      - "NACOS_NAMESPACE=public"
      # true = Nacos 不可达时启动失败并一直重试，保证「服务在跑 = 一定注册上了」
      - "NACOS_FAIL_FAST=true"
      # ---- MySQL ----
      - "MYSQL_HOST=1Panel-mysql-Ipel"      # 注意是大写 i 的 I，不是小写 L
      - "MYSQL_PORT=3306"
      - "MYSQL_DB=factory_db"
      - "MYSQL_USER=root"
      - "MYSQL_PASSWORD=<填入数据库口令>"     # 明文口令只存在于本文件（已 gitignore）
      # ---- 上传上限 ----
      # ⚠️ 必须与 Nginx 的 client_max_body_size 同步；不一致时大文件 413 且应用日志查不到
      - "MULTIPART_MAX_FILE_SIZE=50MB"
      - "MULTIPART_MAX_REQUEST_SIZE=50MB"
    volumes:
      # 日志持久化到宿主机（相对本 compose 文件所在目录），容器重建不丢
      # ⚠️ 首次部署前宿主机先执行：mkdir -p logs && chmod 777 logs
      - ./logs:/app/logs
    ports:
      # 只绑 127.0.0.1，宿主机 Nginx 能连、公网连不上
      - "127.0.0.1:8084:8084"
    networks:
      - nacos_default
      - 1panel-network

networks:
  # 既有网络，本文件只引用不创建
  nacos_default:
    external: true
    name: nacos_default
  1panel-network:
    external: true
    name: 1panel-network
```

**为什么接两个网络**：`nacos_default` 用来解析 Nacos 的容器名；MySQL 是 1Panel 应用商店装的，
可能只挂在 `1panel-network` 上。两个都接，对两个网络上的容器都可解析，不用纠结谁在哪儿。
若 `docker network ls` 里没有 `1panel-network`，compose 会报
`network 1panel-network declared as external, but could not be found`，把那一行删掉即可。

### 3. `.dockerignore`

```
.gradle
build
.idea
.git
*.iml
*.iws
*.ipr
out
.vscode
.DS_Store
docs
logs
```

> 排除了 `build/` —— 所以**不能用项目根目录当镜像构建目录**（`COPY` 会找不到 jar）。
> 上传目录必须是独立的、jar 和 Dockerfile 在同一层。

### 4. `src/main/resources/application-prod.yml`

已经在 jar 里，**不用单独上传**。由 ENTRYPOINT 的 `--spring.profiles.active=prod` 激活，
负责把上面那些环境变量接到实际配置项上（`server.shutdown: graceful`、Nacos discovery、数据源、日志落 `/app/logs/`）。

---

## 四、本地打包

```bash
# ⚠️ PATH 里默认的 java 是 1.8，Gradle 会直接报 "Gradle requires JVM 17 or later"
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"

# 前置：shared-contract 必须在**同级目录**（settings.gradle 里是 includeBuild('../shared-contract')）
#        它不发布到 Maven，靠复合构建解析，缺了就构建失败
cd D:/download/prj_hnd/hnd_factory
./gradlew clean bootJar --console=plain

# 跑单元测试（注意：bootJar 不会触发 test，只有 build 或显式 test 才会）
./gradlew test --console=plain
```

产物：`build/libs/hnd_factory.jar`，**该目录下应当只有这一个 jar**（`jar` 任务已被禁用）。

### 准备上传目录

**不要用 `build/libs/` 当上传目录** —— `gradle clean` 会把整个 `build/` 删掉。

```bash
mkdir -p D:/deploy/hnd_factory
cp build/libs/hnd_factory.jar Dockerfile docker-compose.yml .dockerignore D:/deploy/hnd_factory/
```

---

## 五、服务器部署

```bash
# 1. 先建日志目录并放开权限
#    不做的表现：控制台有日志、文件日志是空的，不报错、极易漏
#    原因：目录若由 Docker 自动创建，属主是 root，容器内非 root 用户写不进去
mkdir -p /opt/hnd_factory/logs && chmod 777 /opt/hnd_factory/logs

# 2. 核对依赖容器名（照抄，别手敲）
docker ps --format '{{.Names}}'
```

3. 把 `D:/deploy/hnd_factory/` 整个上传到 `/opt/hnd_factory/`（jar 和 Dockerfile 必须同层）
4. 1Panel「容器 → 镜像 → 构建镜像」：名称 `hnd_factory`、标签 `latest`、构建目录 `/opt/hnd_factory`
5. 1Panel「容器 → 编排 → 创建编排」，粘贴 `docker-compose.yml` → 创建

### ⚠️ 重建镜像 ≠ 容器更新

重新构建镜像后，**正在运行的容器仍抱着旧镜像 ID 在跑**。必须**删除容器重建**，否则改了等于没改。
用这两条确认容器跑的是新镜像：

```bash
docker inspect -f '{{.Image}}' hnd_factory       # 容器用的镜像 ID
docker inspect -f '{{.Id}}' hnd_factory:latest   # 镜像当前 ID
# 两个 ID 不一致 = 容器还在跑旧镜像
```

---

## 六、Nginx 反代

```nginx
location /api/xxx/ {
    proxy_pass http://127.0.0.1:8084;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 50m;          # 必须与 MULTIPART_MAX_* 同步
    proxy_read_timeout   60s;
    proxy_send_timeout   60s;
}

# 单据图片由 img-service 提供 —— 注意端口是 8082，且**必须带路径重写**
location /files/  { proxy_pass http://127.0.0.1:8082/api/img/file/; }
location /thumbs/ { proxy_pass http://127.0.0.1:8082/api/img/thumb/; }
```

三个端口挨得很近，是最容易写串的地方：

| location | 应指向 | 提供的服务 |
|---|---|---|
| 业务接口 | `127.0.0.1:8084` | hnd_factory |
| `/files/`、`/thumbs/` | `127.0.0.1:8082` | **img-service**（不是 8084！） |
| — | — | excel-import-service 走服务发现，**不需要 Nginx 路由** |

### 图片路由为什么必须带路径重写

hnd_factory 返回给前端的图片地址是**相对路径** `/files/X`、`/thumbs/X`，
而 img-service 的真实接口是 `/api/img/file/X`、`/api/img/thumb/X`（对应
`ImgController` 的 `@RequestMapping("/api/img")` + `@GetMapping("/file/{fileName}")`）。
`proxy_pass` 带了路径时，nginx 会用 `/api/img/file/` **替换掉**匹配到的 `/files/` 前缀。

> **漏了重写会怎样**：不会返回 404。img-service 的全局异常处理器把「路径不存在」也包成了
> **HTTP 200 + 一段 JSON 错误**，所以表现是**状态码完全正常、响应体是 JSON 而不是图片、前端一片裂图**。
> 比 404 更难查 —— 不看响应体根本发现不了。

---

## 七、验证清单

```bash
# 1. 容器起来了
docker logs hnd_factory --tail 50
#    期望三行：
#      The following 1 profile is active: "prod"
#      Tomcat started on port 8084 (http) with context path '/'
#      Started HndFactoryApplication in x.xxx seconds

# 2. 确认注册上了（比翻 Nacos 控制台更直接）
docker logs hnd_factory | grep "register finished"
#    期望：nacos registry, DEFAULT_GROUP hnd_factory <ip>:8084 register finished
#    出现 "register failed" 或 "Failfast is false" = 地址错了

# 3. 接口验证
curl -s http://127.0.0.1:8084/api/pick/test      # 期望 ok
curl -s http://127.0.0.1:8084/api/pick/list      # 期望返回 JSON，带 success:true 和 dataList
#    第 2 条才碰数据库；第 1 条不碰，验不了 MySQL

# 4. 容器内连通性（注意：JRE 精简镜像没有 nc/curl！用 bash 内置 /dev/tcp）
sudo docker exec -it hnd_factory sh
  getent hosts 1Panel-nacos-qquH-standalone
  bash -c 'echo > /dev/tcp/1Panel-nacos-qquH-standalone/8848' && echo "8848 OK"
  bash -c 'echo > /dev/tcp/1Panel-nacos-qquH-standalone/9848' && echo "9848 OK"
  exit
```

> **看到「Tomcat started on port 8084」不等于启动成功。** Nacos 注册是借 Tomcat 启动完成的事件回调去做的，
> 注册失败会反过来把启动流程中断。必须等到 `Started HndFactoryApplication` 才算数。

---

## 八、本项目实际踩过的坑

按踩到的概率排序。

### 1. Nacos 地址要填「服务端 API」端口 `8848`，不是控制台端口 `8080`

Nacos 3.x 起控制台和服务端分开了：控制台默认 `8080`（挂在 `/next/` 下），**服务端 API 仍是 `8848`**。
浏览器里打开的那个 `http://<host>:8080/` 是控制台地址，填进 `server-addr` 必然注册失败。

自检（返回 200 说明这是服务端 API）：

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://<nacos-host>:8848/nacos/v1/ns/operator/metrics
```

> 注意：Nacos 3.x **移除了部分 v1 OpenAPI**。`/nacos/v1/ns/instance/list` 返回 501 `no such api`，
> `/nacos/v2/ns/instance/list` 返回 404，只有 `operator/metrics` 这类还在。
> 所以**最可靠的判断方式是看启动日志有没有 `register finished`**，别依赖某个具体 API。

### 2. `namespace` 填了非法字符

报错：`Param check invalid:Param 'namespaceId/tenant' is illegal, illegal characters should not appear in the param.`

- 必须填**命名空间 ID**，不是名称
- ID 只允许字母、数字、`-`、`_`、`.`。**中文会被拒**（我们就是粘了带中文的占位符）
- 本环境所有服务都注册在 `public` —— `public` 就是公共命名空间的 ID，是合法值，不是"留空"

排查提示：这个报错的堆栈看着是 `Failed to start bean 'webServerStartStop'`，**根因在最底下的 `Caused by`**，
看日志要一路翻到底。

### 3. `fail-fast` 默认 true，Nacos 不可达时启动直接失败

这是**故意的**语义：注册不上就别假装在跑。配合 `restart: unless-stopped`，进程会不断重启重试。

设成 `false` 能启动，但**服务不会出现在 Nacos 服务列表里**，Feign 调它直接 503 —— 属于更糟的静默故障。
只有在本地没有 Nacos 时，才临时用命令行关掉：

```bash
java -jar build/libs/hnd_factory.jar --spring.cloud.nacos.discovery.fail-fast=false
```

### 4. `MYSQL_PASSWORD` 没有默认值，且数据源是**懒加载**的

口令只走环境变量注入（仓库里不写字面口令）。**但它不会在启动时报错** ——
数据源是懒加载的，口令错或没传，服务照样正常启动，要到**第一次查库**才 500。

所以别指望启动失败来发现口令没配好，老老实实核对 compose 里的值。

### 5. 日志目录属主

宿主机 `./logs` 若由 Docker 自动创建，属主是 `root`，容器内非 root 用户写不进去。
表现为「控制台有日志、文件日志是空的」，**不报错**。首次部署前必须先 `mkdir -p logs && chmod 777 logs`。

### 6. `client_max_body_size`

Nginx 默认 1MB。本项目导入的 Excel 含内嵌图片，可达数 MB —— 大文件会返回 **413，
且请求根本没到应用**，应用日志里查不到任何记录。

### 7. 容器内不能用 `127.0.0.1`

容器里的 `127.0.0.1` 指容器**自己**。所有依赖（Nacos/MySQL）必须用**容器名**。

### 8. 多网卡时 Nacos 注册错 IP

服务在列表里可见，但调用方 502。显式指定即可（`application-prod.yml` 里已备好注释掉的一行）：

```yaml
spring.cloud.nacos.discovery.ip: ${NACOS_REGISTER_IP:}
```

### 9. 构建相关

- **`./gradlew bootJar` 不会跑单元测试**（只有 `build` 或显式 `test` 才会）。要跑测试得单独 `./gradlew test`。
- **`JAVA_HOME` 必须是 JDK 21**，PATH 里默认的 java 是 1.8。
- **`shared-contract` 必须在同级目录**，否则 `includeBuild('../shared-contract')` 解析失败。

### 10. 运维时容易混的操作

- **Windows 上 `taskkill`（不带 `/F`）对 java 控制台进程无效**，只能强杀。强杀后注册的是临时实例
  （`ephemeral=true`），心跳停止后 Nacos 约 15~30 秒自动摘除，不会留垃圾 —— 但**会有一小段时间服务列表里还在**。
- **中文提交信息不要直接写在命令行**，用 `git commit -F <文件>` 避免控制台编码把中文搞乱。

---

## 九、日常运维速查

```bash
# 改环境变量（不重新构建镜像）
#   1Panel 面板改 → 删除容器 → 编排重新创建

# 更新代码
#   本地 bootJar → 上传 jar → 1Panel 重新构建镜像 → 删除容器重建

# 看日志
docker logs -f hnd_factory --tail 100

# 进容器排查
docker exec -it hnd_factory sh

# 确认容器用了哪个镜像
docker inspect -f '{{.Image}}' hnd_factory

# 清理悬空镜像
docker image prune -f
```

---

## 十、本地开发

本地直跑不需要配任何环境变量，靠一个**被 gitignore 的覆盖文件**：

```bash
cp config/application.yml.example config/application.yml
# 然后按所在环境填 Nacos 地址与数据库口令
```

`Spring Boot` 会自动加载「工作目录/config/application.yml」，**优先级高于 classpath 里的 yml**
（包括 jar 内的 `application-prod.yml`），且不进 git、不进镜像。

| 环境 | Nacos | 说明 |
|---|---|---|
| 家里 | `192.168.3.7:8848` | 注意 `:8080` 是控制台，不能填 |
| 公司 | `127.0.0.1:8848` | |
| 远程服务器 | — | 不用这个文件，走 compose 环境变量 |

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
java -jar build/libs/hnd_factory.jar        # 不带 profile，默认 8084
```

> 这个文件**不随 git 同步** —— 换机器要手动建一次。这正是它防误提交的代价。
