package cn.mcayear.sfe4happy.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static cn.mcayear.sfe4happy.Main.logger;

public class ProcessManager {

    public static Process startProcess(List<String> command, String processName, File workDir) throws IOException, InterruptedException {
        logger.info("启动进程: {}", processName);

        // 保持原始路径处理逻辑
        String joinPath = workDir.toPath().resolve(command.get(0)).toString();
        command.set(0, joinPath); // 替换掉第一个元素为拼接后的路径

        // 创建 ProcessBuilder 对象
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir);
        processBuilder.redirectErrorStream(true);

        // 启动进程
        Process process = processBuilder.start();

        // 在新线程中异步读取并输出脚本的执行过程
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[{}] {}\u001B[0m", processName, line);
                }
            } catch (IOException e) {
                logger.error("读取进程输出时发生错误: {}", e.getMessage());
            }
        }).start();

        // 返回 Process 对象，供后续控制
        return process;
    }
}
