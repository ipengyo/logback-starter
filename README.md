# 关于打日志那点事

参考文档：
* https://github.com/bingoohuang/blog/issues/151
* https://github.com/YLongo/logback-chinese-manual
* http://blog.zollty.com/b/archive/logback-guide-and-best-practices.html

## 什么是日志

日志 = timestamp(时间戳) + data(日志内容)

我们程序或者业务发生问题了，最可靠的排查手段就是查日志，所以打日志这件事很重要。

## 日志记录什么

简而言之就是：谁，在哪个模块，干了什么，结果是啥。

举例： 用户=1001，在商城模块，购买了商品；商品id=宝贝100，商品金额=6，购买成功。

1. 记录有帮助的信息
    * 日志产生时的上下文信息，比如与用户相关的记录用户id、发生请求ip、发生渠道
    * 敏感数据（手机号、身份证、密码）脱敏处理，或记录信息摘要。如 176*****90
    * 如果需要解析，可考虑结构化数据输出参数信息，可以用json字符串来代替，这样方便解析
2. 什么时候记录日志
    * 操作日志
        * 用户关键操作时
        * 用户错误操作时
        * 系统启动、停止、重载时
        * 缓存更新时
    * 安全相关
        * 登录、退出
        * 修改密码
        * 访问受限资源的操作
    * 业务相关
        * 用户付款
        * 用户付款失败
    * 告警相关（ERROR）
        * 第三方服务请求失败时
        * 插入数据库失败
        * 未知异常导致处理逻辑失败
    * debug日志
        * 业务执行的细节信息，比如请求详细的参数、返回详细结果
        * 耗费性能的信息
        * debug级别可通过配置开关

# 最佳实践

## 日志形态

日志的落地形态一般情况有如下

1. 写入到日志文件中
2. 直接写入到数据库(mysql、es)
3. 通过socket发送到其他进程
4. 容器部署下，一般应用把日志打到stdout或stderr上，可以通过docker logs 进行查看。

我们这里选择按最常用的 1.写到日志文件 这样可靠性强

## 日志文件

1. 日志默认存储在${LOG_HOME}/logs/目录下面，例如: /server/app_server/logs/
2. 当前正在写入的日志名为${LOG_APP_NAME}.log，不带日期后缀，例如: app.log
3. 归档日志，以日期（天） + 文件大小 为单位进行轮转。单个日志最大size：200MB，超过即进行轮转。
   * 归档日志格式为${LOG_APP_NAME}.log.%d{yyyy-MM-dd}.%i，日期作为文件名后缀，后面的i为自增数字。例如: app.log.2020-04-24.1 
   * 归档日志保留期限：最大保留60个。 
        * 可按应用规模与机器配置调整，一般应用，这个数字够用，最大存储空间 10GB ~=  60 * 200

```shell
${LOG_HOME} = /server/app_server
${LOG_APP_NAME} = app
```

备注：
* app.log 是应用所有输出日志
* 如果需要单独存储`error`级别的日志 
    * 一般日志库都具有LogLevelFilter功能，可以将ERROR级别日志，复制输出到另外一个目录存储。
    * 写入日志名 比如：`/server/app_server/logs/app-error.log`  归档规则同上。

## 日志内容格式

1. 每行日志遵循相同的标准格式
2. 多行日志，必须要有行首格式，以供区分
3. 日志内容UTF8编码

示例：

``
[2022-05-19 15:41:23.036] [INFO] [main] [Application] : 用户登录 userId=1, ip=127.0.01
``

|  p1   | p2  | p3  | p4  | p5  | p6  | 
|  ----  | ----  | ----  | ----  | ----  | ----  |
| [2022-05-19 15:41:23.036]  | [INFO] | [main] | [Application] | : | 用户登录 userId=1, ip=127.0.01 |
| 时间  | 日志级别 | 线程Name | loggerName | : | 详情文本 |

1. 时间，固定格式 yyyy-MM-dd HH:mm:ss.SSS
2. 日志级别，右对齐，大写。取值 ERROR WARN INFO DEBUG
3. 线程Name
4. 日志logger名称
5. 分隔符 ： 无意义
6. 日志详情内容

上述例子以java语言为例，如果其他语言的日志库没有上述某个字段，可以其他类似值代替或者默认值。

## 日志级别（level）

日志等级是用来区分日志对应事件严重程度的说明，这是所有日志中必须具备的一个选项。

* `ERROR` （错误） 非预期中的错误
* `WARN`  （警告） 潜在的危险或值得关注的信息
    * `ERROR` 和 `WARN` 的区别很多程序员难以选择，可以从告警角度考虑：ERROR一般需要告警，WARN不需要。
* `INFO`  （信息） 应用执行过程中的详细信息，一般通过该信息可以看到每个请求的主要执行过程。
* `DEBUG` （调试）用于线下调试的日志信息，用于分析应用执行逻辑，线上应用一般切勿开启。
    * DEBUG日志可以多打，方便分析问题，特别是“新鲜出炉”的代码，可能出现问题的地方特别多，DEBUG日志多打时，比较方便分析问题。

日志级别最好要有动态改变的能力

比如线上正式环境默认是INFO级别的日志，排查问题时需要改变到DEBUG级别。如果没有动态改变的能力，需要重启服务器那就成本高了。

动态改变日志级别的能力实现手段：
* 修改配置文件
* 通过http接口 实现 admin/setLogLevel  注意此接口不能对外，需鉴权。

## 日志采集-实现监控告警

通过我们如上的约定，我们的程序日志目录、格式都是固定的。可通过日志服务logtail采集集中存储。

实现监控项告警
* 执行成功 日志条数 < x
    * 比如我在跑一个数据，按正常10分钟内1条 成功的日志输出。倘若10分钟内1条成功的都没有，则可认为业务挂了，需要人工确认排查。
* ERRO级别/执行失败 日志条数 >= x

双向指标监控，确保监控指标不留死角。可以想想如果只监控某一项指标，你的监控是不是会有问题。

比如只监控ERROR级别日志条数10分钟内>=1，有一种极端情况你的机器直接挂了（网线被挖掘机挖断了），没有任何日志输出成功，那么这条监控规则永远不会触发。

## JAVA中的最佳实践

1. 根据开发环境、测试环境、生产环境等环境的不同，来配置不同的日志策略。
    * 正式环境中程序输出的日志不需要打在Console，Console日志只拿来兜底，记录一些未知的代码库往控制台输出的日志
2. 做到在自己的代码中，合理选择日志输出的级别，不要输出大量无意义的调试日志
3. 将第三方代码中的不必要的日志过滤掉
4. 默认所有程序全部打到app.log，使用日志采集工具采集app.log一个文件即可，运维简单
5. 控制日志文件的保存时间和文件大小
6. 最好是能热加载配置，让日志配置的更改能及时生效，便于问题排查
7. 注意日志程序优雅的关闭 （shutdown）

### 日志库选择（logback)

日志库的原则：可靠、简单、不需要过多折腾
日志库：logback 
logback 中文手册/文档： https://github.com/YLongo/logback-chinese-manual

依赖版本：
```groovy
dependencies {
    implementation group: 'ch.qos.logback', name: 'logback-classic', version: '1.2.3'
    // 配置文件中的条件处理功能需要 Janino 库 docs: https://logback.qos.ch/setup.html
    implementation group: 'org.codehaus.janino', name: 'janino', version: '3.1.2'
}
```
### 性能优化

1、异步日志
* 日志密集大批量输出可使用 AsyncAppender 异步日志。

2、请使用 {} 占位符拼接
```
// 好
logger.info("name:{}"", "小红")

// 坏
logger.info("name:" + "小红")
```

这样性能会好一点，因为只有在打印的时候才会拼接字符串

3、debug日志注意不要使用字符串拼接
```
// 好
logger.debug("name:{}"", "小红")
或者
if (logger.isDebugEnabled()) {
    logger.debug("name:{}" + "小红");
}

// 坏
logger.debug("name:" + "小红")
```

debug日志因为会涉及到level动态改变，大多数情况下是不需要输出内容的。

如果是level是INFO，但是在debug日志时进行了字符串拼接，但是日志又不需要输出。

这将白白浪费性能。如果字符串比较大，还将造成卡顿。所以不要使用debug时进行字符串 拼接。

### 配置文件模版 (logback.xml)

查看： 项目/src/main/resources/logback.xml 

#### 配置文件说明:


scan="true" scanPeriod="15 seconds"

> 代表可以动态改变日志配置文件，实现热更配置，需15秒后生效。

property name:

* LOG_ENV `重要` 定义日志环境标识 默认dev、正式环境可填prod
    * 可通过启动参数 -Dlog.log_env=prod 启动时改变
* LOG_HOME `重要` 定义日志文件的存储地址路径 ，这个使用的是相对路径，默认即在日志文件存放在项目根路径./logs文件夹下 (可通过环境变量LOG_HOME覆盖)
    * 可通过启动参数 -Dlog.log_home=./logs 启动时改变
* LOG_ROOT_LEVEL `重要`  定义rootLevel  默认INFO，调试程序时可以修改为 DEBUG
    * 可通过启动参数 -Dlog_root_level=DEBUG 启动时改变
* LOG_APP_NAME 程序日志文件名 默认:app
* LOG_PATTERN 日志格式：[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%p] [%t] [%-5.40logger{39}] : %m%n
* LOG_MAX_FILE_SIZE 定义日志文件大小,超过这个大小将被轮转 默认：200MB
* LOG_MAX_TOTAL_SIZE 定义日志文件保留最多数据空间  默认：20GB
* LOG_MAX_HISTORY 定义日志文件保留最近条数  默认保留最近60条


shutdownHook
> 在独立的 java 应用程序中，在配置文件中添加 <shutdownHook/> 可以确保任何日志打包任务完成之后，JVM 才会退出。在 web 应用程序中，webShutdownHook 会自动安装，<shutdownHook/> 将会变的多余且没有必要。

appender： 
* `appender STDOUT` 控制台输出
* `appender FILE` app.log 轮转日志输出
* `appender FILE_ERROR` app-error.log 轮转日志输出 过滤级别ERROR的日志单独存储，日志归档规则如上
* `appender ASYNC_FILE`  包装appender FILE 的异步队列输出，一般情况不太需要
*  其他的特殊需求，可自己扩展

root Level:

> 使用了 if condition 来根据变量 LOG_ENV 参数值来动态改变 appender-ref的个数.dev环境打开Console日志输出

> 此功能依赖于 janino jar包 docs: https://logback.qos.ch/setup.html