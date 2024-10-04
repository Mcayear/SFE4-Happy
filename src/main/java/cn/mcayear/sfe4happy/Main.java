package cn.mcayear.sfe4happy;

import cn.mcayear.sfe4happy.process.ApplicationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    // 创建 Logger 实例
    public static final Logger logger = LogManager.getLogger(Main.class);

    public static final Path basePath = Paths.get(System.getProperty("user.dir")).resolve("package");
    public static void main(String[] args) {
        ApplicationManager appManager = new ApplicationManager();
        appManager.runApplication(args);
    }
}
