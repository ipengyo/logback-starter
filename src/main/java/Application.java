import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public class Application {
    static Logger logger = LoggerFactory.getLogger(Application.class);
    public static void main(String[] args) throws InterruptedException {
        logger.info("用户登录 userId={}, ip:{}", 1, "127.0.01");
        logger.error("logger error");
        logger.debug("logger debug");

        // 测试配置文件，动态更新
//        new Thread(() -> {
//            while (true) {
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                logger.debug("test ...");
//                logger.info("info test ...");
//            }
//        }).start();
//        Thread.currentThread().join();
    }
}
