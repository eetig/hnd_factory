# AGENTS.md — 在这台/那台电脑上干活的 AI，先读这一页

> 这份文件给**任何**接手本项目的 AI（公司电脑、家里电脑、以后换的模型或工具）看；人也可以看。
> 它存在的唯一理由：**项目会在两台电脑上被同时改动**，规矩必须写在仓库里，不能只存在某个会话里。
> 最后更新：2026-10-20

---

## 一、先同步，再动手（最重要，别跳）

这个项目**同时在公司电脑和家里电脑上改**，两边都有 AI 在干活。所以：

1. **开工第一件事**：`git pull --ff-only`
2. **收工前**：`git add -A && git commit && git push` —— **不许留"只在本地没推"的改动**。
   （2026-10-20 就因为"改了没提交"，两台机器的改动差点打架）
3. **遇到冲突**：**保留双方的条目**，按日期排序，**不要 `--force`、不要丢别人的行**，
   解决完在提交信息里写清"合了哪边"。
4. ⚠️ **不要用 `git add -A` / `git add .`** —— 只 stage 自己改的文件（`git add <具体路径>`）。
   2026-10-20 就是 `git add -A` 把另一台机器**尚未提交**的液位改动（10 个文件）一并扫进了
   一个只写"文档"的提交里：东西没丢，但提交信息与内容对不上，以后查历史会被误导。
   提交前先 `git status` 看一眼有没有不属于自己的改动。

## 二、待办清单是唯一真源

- 文件：`docs/待办清单.md`（远端可直接看：
  <https://github.com/eetig/hnd_factory/blob/main/docs/待办清单.md>）
- **做完一项**：在对应行把状态改成 ✅ 并写日期；同时在末尾「已关闭」表**追加一行**。
- **新东西**：加到对应组（A 需使用方动手 / B 已记录未做 / C 待拍板 / D 技术债）的**最后一行**。
- **只追加、不重排整表** —— 两边各自 append 时 git 才容易自动合并；重排必冲突。
- 别另起一份"待办2""todo"之类的文件。

## 三、改了功能就要记账（本项目的老规矩）

- 每次改动 → `hnd_factory_UI/前后端改动统筹.md` 加一行 `变更-NNN`，
  写**为什么**（使用方原话最好）+ **怎么验证**（实测数字/命令），不要只写"改了什么"。
- 设计决策/方案 → `docs/`（例：`资料归档-开工方案.md`、`方案-分层可见与写操作留痕.md`）。
- 数据库改动 → `docs/schema.sql` 的**编号步骤**（照第 23 步的格式：为什么 / 改前核对 / 改后复核 / 回滚）。

## 四、门禁（不绿不许部署）

- 后端：`gradlew test`（当前 **190** 用例）
- 前端：`npm run verify`（lint + build + vitest；当前 **33 文件 / 499** 用例）
- 前端视觉护栏在 `tests/unit/visualTokens.spec.js`，几条硬规矩：
  - **禁用 `text-slate-400`**（对白底 2.56:1，不过 AA）；弱化文字用 `slate-500`，小字用 `slate-600`
  - 卡片串必须完整：`rounded-card border border-line bg-raised shadow-card`，**数量写死**，新增卡片要 +1
  - 表格 `table-fixed` 下**只许一列自适应**，其余列给固定宽（否则剩余宽度被摊平、左边一片空白）
  - 整行可点的 `<tr>` 必须能键盘激活（`tabindex` + 回车/空格）
- 后端有一条 `FeignClientNameUniquenessTest`：同服务名的多个 `@FeignClient` 必须各有不同 `contextId`

## 五、部署（顺序不能乱）

- 顺序：**库 → file-service → hnd_factory → 前端**
- ⚠️ `file-service`（原 img-service）改名后，Nacos 上的服务名切换有约 1 分钟窗口：
  两个服务必须**背靠背**部署，先 file-service 再 hnd_factory
- ⚠️ 本机 `sudo` 密码与 SSH 私钥**不写进仓库**（由使用方单独提供）；SSH 只接受密钥登录
- 回滚锚点：`/opt/<服务>/*.jar.bak-<时间戳>` + 旧镜像标签；每次部署前先备份 jar 与库

### 后端部署：jar 在镜像里，**光拷 /opt 不会生效**

2026-10-20 踩过：把新 jar 传到 `/opt/hnd_factory/hnd_factory.jar` 再 `docker restart`，
容器里跑的**还是旧 jar**（`docker inspect` 显示只挂了 `logs`，jar 是 `COPY` 进镜像的）。
表现极具误导性：新端点 404、注册接口 401，看起来像代码写错了。

正确做法（不改镜像，最快）：
```sh
docker cp /opt/hnd_factory/hnd_factory.jar hnd_factory:/app/hnd_factory.jar
docker restart hnd_factory
# 必验：容器内 jar 的 md5 与宿主机一致
docker exec hnd_factory md5sum /app/hnd_factory.jar
```
**每次部署后都要比一次 md5** —— 这是唯一能立刻发现"没换上去"的动作。

### 前端部署：必须**整目录原子换**，不要逐个文件覆盖

2026-10-20 踩过：先 `mv assets assets.old` 再拷新 `assets`、再拷新 `index.html` ——
中间那一两秒里 `index.html` 指向的旧 chunk 已经不在原位，**使用者正好刷新就会拿到 404，
浏览器把 404 缓存下来 → 白屏，直到 Ctrl+F5**。表现是"四个页面全白、Console 无报错"，
非常容易被误判成代码坏 / 后端 500（我们为此白查了半小时）。

正确做法：
```sh
S=/opt/1panel/apps/openresty/openresty/www/sites/hnd-ui
sudo cp -a /tmp/hnd-ui-new $S/index.new && sudo chown -R root:root $S/index.new
sudo mv $S/index $S/index.prev-$(date +%Y%m%d-%H%M%S)
sudo mv $S/index.new $S/index        # 这一步是原子的，窗口毫秒级
```
`index.html` 的缓存头已经是 `no-cache, must-revalidate`、`/assets/` 是 `immutable`（变更-096/102），
**不要**再改它们；要修的是"换文件的过程不原子"这件事。

## 六、协作偏好（使用方）

- 用**通俗中文**解释；操作步骤要**短**；自己的错**直说**，不要含糊
- 提交信息写**为什么**、带**实测数字**
- 不要引新依赖（除非说明必要性）
- 结论先行：先给"做了什么/现在怎样"，再给细节
