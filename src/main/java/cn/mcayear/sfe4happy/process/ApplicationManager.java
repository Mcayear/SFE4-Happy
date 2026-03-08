package cn.mcayear.sfe4happy.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;

import static cn.mcayear.sfe4happy.Main.basePath;
import static cn.mcayear.sfe4happy.Main.logger;
import static cn.mcayear.sfe4happy.process.ProcessManager.startProcess;

public class ApplicationManager {

    private static final Map<String, Process> processPool = new ConcurrentHashMap<>();

    public void runApplication(String[] args) {
        logger.info("欢迎使用 SFE4 嗨皮!");
        Path infoPath = basePath.resolve("info.json");

        // 传递拼接后的路径给 ParsePackage
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        // 遍历配置中的所有进程并启动
        if (taskConfig1.config != null) {
            for (Map.Entry<String, List<String>> entry : taskConfig1.config.entrySet()) {
                String processName = entry.getKey().toLowerCase();
                List<String> command = entry.getValue();
                if (command != null && !command.isEmpty()) {
                    Process p = startProcessAsync(command, processName.toUpperCase(), basePath.resolve(processName).toFile());
                    if (p != null) {
                        processPool.put(processName, p);
                    }
                }
            }
        }

        // 捕获 ^C 信号和 JVM 关闭事件
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("捕获到 JVM 关闭事件，正在清理...");
            shutdownProcesses();
        }));

        // 监听控制台输入
        listenForStopCommand();

        // 当 listenForStopCommand 返回后，调用 shutdownProcesses()
        shutdownProcesses();

        logger.info("应用已停止");

        // 手动关闭日志系统，确保所有日志都被输出
        LogManager.shutdown();
    }

    private void shutdownProcesses() {
        logger.info("准备关闭所有子进程...");
        try {
            // 首先给所有非 FRP 的进程发送 stop 命令，让它们开始安全关闭
            for (Map.Entry<String, Process> entry : processPool.entrySet()) {
                String name = entry.getKey().toUpperCase();
                Process p = entry.getValue();
                if (p != null && p.isAlive()) {
                    if (!name.equalsIgnoreCase("FRP")) {
                        sendStopCommandToProcess(p);
                    }
                }
            }
            
            // 然后再等待每个进程结束或进行强制销毁
            for (Map.Entry<String, Process> entry : processPool.entrySet()) {
                String name = entry.getKey().toUpperCase();
                Process p = entry.getValue();
                if (p != null && p.isAlive()) {
                    // frp 通常不支持 stop 命令，我们需要直接销毁
                    if (name.equalsIgnoreCase("FRP")) {
                        p.destroy();
                    }
                    
                    try {
                        // 我们稍微等待一下，但如果它已经被上一步的 stop 关掉了，这会立刻返回
                        if (p.waitFor(15, TimeUnit.SECONDS)) {  // 等待15秒
                            logger.info(name + " 进程已终止");
                        } else {
                            // 当超时时，执行销毁
                            logger.warn(name + " 进程终止超时，正在强制关闭...");
                            p.destroy();  // 超时后强制终止
                            
                            if (!p.waitFor(5, TimeUnit.SECONDS)) {  // 再等待5秒确保完全终止
                                logger.error(name + " 进程无法强制终止，正在调用 destroyForcibly...");
                                p.destroyForcibly();
                            } else {
                                logger.info(name + " 进程已强制终止");
                            }
                        }
                    } catch (InterruptedException e) {
                        logger.warn("等待 " + name + " 进程关闭时被中断");
                        Thread.currentThread().interrupt(); // 重新标记中断状态
                    }
                }
            }
        } catch (Exception e) {
            logger.error("关闭进程时发生错误", e);
        }
        processPool.clear();
        logger.info("所有子进程已终止");
    }

    private void restartProcesses() {
        logger.info("收到 restart 命令，正在重启所有进程...");
        
        // 1. 先关闭现有进程
        shutdownProcesses();
        
        // 2. 重新加载配置并启动
        Path infoPath = basePath.resolve("info.json");
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        if (taskConfig1.config != null) {
            for (Map.Entry<String, List<String>> entry : taskConfig1.config.entrySet()) {
                String processName = entry.getKey().toLowerCase();
                List<String> command = entry.getValue();
                if (command != null && !command.isEmpty()) {
                    Process p = startProcessAsync(command, processName.toUpperCase(), basePath.resolve(processName).toFile());
                    if (p != null) {
                        processPool.put(processName, p);
                    }
                }
            }
        }
        
        logger.info("所有进程已重启完成");
    }

    private void restartProcess(String processName) {
        processName = processName.toLowerCase();
        logger.info("收到 restart " + processName + " 命令，正在重启该进程...");
        Path infoPath = basePath.resolve("info.json");
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        if (taskConfig1.config == null) {
            logger.warn("配置解析失败或为空");
            return;
        }

        List<String> command = taskConfig1.config.get(processName);
        if (command == null || command.isEmpty()) {
            logger.warn("配置文件中未找到 " + processName + " 的配置或配置为空");
            return;
        }

        try {
            Process p = processPool.get(processName);
            if (p != null && p.isAlive()) {
                if (processName.equalsIgnoreCase("frp")) {
                    p.destroy();
                } else {
                    sendStopCommandToProcess(p);
                }
                
                try {
                    if (p.waitFor(15, TimeUnit.SECONDS)) {
                        logger.info(processName.toUpperCase() + " 进程已终止");
                    } else {
                        logger.warn(processName.toUpperCase() + " 进程终止超时，正在强制关闭...");
                        p.destroy();
                        
                        boolean exited = false;
                        try {
                            exited = p.waitFor(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        if (!exited) {
                            p.destroyForcibly();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            Process newProcess = startProcessAsync(command, processName.toUpperCase(), basePath.resolve(processName).toFile());
            if (newProcess != null) {
                processPool.put(processName, newProcess);
            } else {
                processPool.remove(processName);
            }
            logger.info(processName.toUpperCase() + " 进程已重启完成");
        } catch (Exception e) {
            logger.error("等待进程关闭时发生中断", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void listenForStopCommand() {
        try (Scanner scanner = new Scanner(System.in)) {
            String currentScreenProcessName = null;  // 当前连接的进程名
            while (true) {
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim();
                    if (input.equalsIgnoreCase("help")) {
                        showHelp();
                    } else if (input.equalsIgnoreCase("stop")) {
                        logger.info("收到 stop 命令");
                        break;  // 退出循环，停止应用
                    } else if (input.equalsIgnoreCase("restart")) {
                        restartProcesses();
                        currentScreenProcessName = null;
                        logger.info("已退出 screen 模式（所有进程已重启）");
                    } else if (input.toLowerCase().startsWith("restart ")) {
                        String[] parts = input.split("\\s+", 2);
                        if (parts.length >= 2) {
                            String processName = parts[1].trim().toLowerCase();
                            restartProcess(processName);
                            if (currentScreenProcessName != null && currentScreenProcessName.equals(processName)) {
                                currentScreenProcessName = null;
                                logger.info("已退出 screen 模式（当前连接的进程已重启）");
                            }
                        } else {
                            logger.warn("请指定要重启的进程，例如：restart server1");
                        }
                    } else if (input.toLowerCase().startsWith("screen ")) {
                        String[] parts = input.split("\\s+", 2);
                        if (parts.length >= 2) {
                            String processName = parts[1].trim().toLowerCase();
                            Process p = processPool.get(processName);
                            if (p != null && p.isAlive()) {
                                currentScreenProcessName = processName;
                                logger.info("已连接到 " + processName.toUpperCase() + " 进程");
                            } else {
                                logger.warn(processName.toUpperCase() + " 进程未启动或已结束");
                            }
                        } else {
                            logger.warn("请指定要连接的进程，例如：screen server1");
                        }
                    } else if (currentScreenProcessName != null) {
                        if (input.equalsIgnoreCase("exit")) {
                            currentScreenProcessName = null;
                            logger.info("已退出 screen 模式");
                        } else if (!input.equalsIgnoreCase("stop") && !input.toLowerCase().startsWith("screen ")) {
                            Process p = processPool.get(currentScreenProcessName);
                            sendCommandToProcess(p, input);
                        }
                    } else {
                        logger.warn("未知的命令：" + input);
                    }
                }
            }
        } catch (NoSuchElementException e) {
            logger.error("未找到输入行，程序将继续处理退出流程", e);
        }
    }

    private void showHelp() {
        logger.info("======================= 帮助菜单 =======================");
        logger.info("help                - 显示此帮助信息");
        logger.info("stop                - 关闭所有子进程并安全退出本程序");
        logger.info("restart [进程名]     - 重新加载配置文件并重启所有或指定子进程 (例如: restart server1)");
        logger.info("screen <进程名>      - 进入指定进程的控制台进行交互 (例如: screen server1)");
        logger.info("                      (配置文件中的任何键都可以作为进程名)");
        logger.info("exit                - (仅在 screen 模式下有效) 退出当前进程交互");
        logger.info("========================================================");
    }

    private void sendCommandToProcess(Process process, String command) {
        if (process != null && process.isAlive()) {
            try {
                process.getOutputStream().write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                process.getOutputStream().flush();
            } catch (IOException e) {
                logger.error("向进程发送命令时出错", e);
            }
        } else {
            logger.warn("进程不可用，无法发送命令");
        }
    }


    // 异步启动进程的帮助方法
    private Process startProcessAsync(List<String> command, String processName, File workingDirectory) {
        try {
            return startProcess(command, processName, workingDirectory);  // 返回 Process 对象
        } catch (IOException | InterruptedException e) {
            logger.error("启动进程 " + processName + " 时出错", e);
            return null;
        }
    }

    private void sendStopCommandToProcess(Process process) {
        if (process != null) {
            try {
                // frp 通常不支持 stop 命令，且部分进程关闭输入流时会报错，所以忽略报错
                process.getOutputStream().write("stop\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                process.getOutputStream().flush(); // 刷新流，确保命令发送出去
            } catch (Exception e) {
                // logger.error("向进程发送 stop 命令时出错", e); 
                // Ignore errors like Stream closed when writing stop
            }
        }
    }
}