package cn.mcayear.sfe4happy.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;

import static cn.mcayear.sfe4happy.Main.basePath;
import static cn.mcayear.sfe4happy.Main.logger;
import static cn.mcayear.sfe4happy.process.ProcessManager.startProcess;

public class ApplicationManager {

    private static Process frpProcess;
    private static Process server1Process;
    private static Process server2Process;

    public void runApplication(String[] args) {
        logger.info("欢迎使用 SFE4 嗨皮!");
        Path infoPath = basePath.resolve("info.json");

        // 传递拼接后的路径给 ParsePackage
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        // 启动 frp
        frpProcess = startProcessAsync(taskConfig1.config.getFrp(), "FRP", basePath.resolve("frp").toFile());

        // 启动 server
        server1Process = !taskConfig1.config.getServer1().isEmpty()
                ? startProcessAsync(taskConfig1.config.getServer1(), "SERVER1", basePath.resolve("server1").toFile())
                : null;

        server2Process = !taskConfig1.config.getServer2().isEmpty()
                ? startProcessAsync(taskConfig1.config.getServer2(), "SERVER2", basePath.resolve("server2").toFile())
                : null;

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
            if (server1Process != null && server1Process.isAlive()) {
                sendStopCommandToProcess(server1Process);
                if (server1Process.waitFor(30, TimeUnit.SECONDS)) {  // 等待30秒
                    logger.info("SERVER1 进程已终止");
                } else {
                    logger.warn("SERVER1 进程终止超时");
                    server1Process.destroy();  // 超时后强制终止
                }
            }

            if (server2Process != null && server2Process.isAlive()) {
                sendStopCommandToProcess(server2Process);
                if (server2Process.waitFor(30, TimeUnit.SECONDS)) {  // 设置30秒超时时间
                    logger.info("SERVER2 进程已终止");
                } else {
                    logger.warn("SERVER2 进程终止超时，正在强制关闭...");
                    server2Process.destroy();  // 超时后强制终止
                    if (server2Process.waitFor(5, TimeUnit.SECONDS)) {  // 再等待5秒确保完全终止
                        logger.info("SERVER2 进程已强制终止");
                    } else {
                        logger.error("SERVER2 进程无法强制终止");
                    }
                }
            }

            if (frpProcess != null && frpProcess.isAlive()) {
                frpProcess.destroy(); // 向子进程发送终止信号
                if (frpProcess.waitFor(15, TimeUnit.SECONDS)) {  // 设置15秒超时时间
                    logger.info("FRP 进程已终止");
                } else {
                    logger.warn("FRP 进程终止超时，正在强制关闭...");
                    frpProcess.destroy();  // 超时后强制终止
                    if (frpProcess.waitFor(5, TimeUnit.SECONDS)) {  // 再等待5秒确保完全终止
                        logger.info("FRP 进程已强制终止");
                    } else {
                        logger.error("FRP 进程无法强制终止");
                    }
                }
            }

        } catch (InterruptedException e) {
            logger.error("等待进程关闭时发生中断", e);
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
        logger.info("所有子进程已终止");
    }

    private void restartProcesses() {
        logger.info("收到 restart 命令，正在重启所有进程...");
        
        // 1. 先关闭现有进程
        shutdownProcesses();
        
        // 2. 重新加载配置并启动
        Path infoPath = basePath.resolve("info.json");
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        frpProcess = startProcessAsync(taskConfig1.config.getFrp(), "FRP", basePath.resolve("frp").toFile());

        server1Process = !taskConfig1.config.getServer1().isEmpty()
                ? startProcessAsync(taskConfig1.config.getServer1(), "SERVER1", basePath.resolve("server1").toFile())
                : null;

        server2Process = !taskConfig1.config.getServer2().isEmpty()
                ? startProcessAsync(taskConfig1.config.getServer2(), "SERVER2", basePath.resolve("server2").toFile())
                : null;
        
        logger.info("所有进程已重启完成");
    }

    private void restartProcess(String processName) {
        logger.info("收到 restart " + processName + " 命令，正在重启该进程...");
        Path infoPath = basePath.resolve("info.json");
        ParsePackage taskConfig1 = new ParsePackage(infoPath.toFile());

        try {
            switch (processName) {
                case "server1":
                    if (server1Process != null && server1Process.isAlive()) {
                        sendStopCommandToProcess(server1Process);
                        if (server1Process.waitFor(30, TimeUnit.SECONDS)) {
                            logger.info("SERVER1 进程已终止");
                        } else {
                            logger.warn("SERVER1 进程终止超时，正在强制关闭...");
                            server1Process.destroy();
                            server1Process.waitFor(5, TimeUnit.SECONDS);
                        }
                    }
                    server1Process = !taskConfig1.config.getServer1().isEmpty()
                            ? startProcessAsync(taskConfig1.config.getServer1(), "SERVER1", basePath.resolve("server1").toFile())
                            : null;
                    logger.info("SERVER1 进程已重启完成");
                    break;
                case "server2":
                    if (server2Process != null && server2Process.isAlive()) {
                        sendStopCommandToProcess(server2Process);
                        if (server2Process.waitFor(30, TimeUnit.SECONDS)) {
                            logger.info("SERVER2 进程已终止");
                        } else {
                            logger.warn("SERVER2 进程终止超时，正在强制关闭...");
                            server2Process.destroy();
                            server2Process.waitFor(5, TimeUnit.SECONDS);
                        }
                    }
                    server2Process = !taskConfig1.config.getServer2().isEmpty()
                            ? startProcessAsync(taskConfig1.config.getServer2(), "SERVER2", basePath.resolve("server2").toFile())
                            : null;
                    logger.info("SERVER2 进程已重启完成");
                    break;
                case "frp":
                    if (frpProcess != null && frpProcess.isAlive()) {
                        frpProcess.destroy();
                        if (frpProcess.waitFor(15, TimeUnit.SECONDS)) {
                            logger.info("FRP 进程已终止");
                        } else {
                            logger.warn("FRP 进程终止超时，正在强制关闭...");
                            frpProcess.destroy();
                            frpProcess.waitFor(5, TimeUnit.SECONDS);
                        }
                    }
                    frpProcess = startProcessAsync(taskConfig1.config.getFrp(), "FRP", basePath.resolve("frp").toFile());
                    logger.info("FRP 进程已重启完成");
                    break;
                default:
                    logger.warn("未知的进程名称：" + processName);
                    break;
            }
        } catch (InterruptedException e) {
            logger.error("等待进程关闭时发生中断", e);
            Thread.currentThread().interrupt();
        }
    }

    private void listenForStopCommand() {
        try (Scanner scanner = new Scanner(System.in)) {
            Process currentScreenProcess = null;  // 当前连接的进程
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
                        currentScreenProcess = null;
                        logger.info("已退出 screen 模式（所有进程已重启）");
                    } else if (input.toLowerCase().startsWith("restart ")) {
                        String[] parts = input.split("\\s+", 2);
                        if (parts.length >= 2) {
                            String processName = parts[1].trim().toLowerCase();
                            restartProcess(processName);
                            if (currentScreenProcess != null) {
                                if (processName.equals("server1") && currentScreenProcess == server1Process) {
                                    currentScreenProcess = null;
                                    logger.info("已退出 screen 模式（当前连接的进程已重启）");
                                } else if (processName.equals("server2") && currentScreenProcess == server2Process) {
                                    currentScreenProcess = null;
                                    logger.info("已退出 screen 模式（当前连接的进程已重启）");
                                } else if (processName.equals("frp") && currentScreenProcess == frpProcess) {
                                    currentScreenProcess = null;
                                    logger.info("已退出 screen 模式（当前连接的进程已重启）");
                                }
                            }
                        } else {
                            logger.warn("请指定要重启的进程，例如：restart server1");
                        }
                    } else if (input.toLowerCase().startsWith("screen ")) {
                        String[] parts = input.split("\\s+", 2);
                        if (parts.length >= 2) {
                            String processName = parts[1].trim().toLowerCase();
                            switch (processName) {
                                case "server1":
                                    if (server1Process != null && server1Process.isAlive()) {
                                        currentScreenProcess = server1Process;
                                        logger.info("已连接到 SERVER1 进程");
                                    } else {
                                        logger.warn("SERVER1 进程未启动或已结束");
                                    }
                                    break;
                                case "server2":
                                    if (server2Process != null && server2Process.isAlive()) {
                                        currentScreenProcess = server2Process;
                                        logger.info("已连接到 SERVER2 进程");
                                    } else {
                                        logger.warn("SERVER2 进程未启动或已结束");
                                    }
                                    break;
                                case "frp":
                                    if (frpProcess != null && frpProcess.isAlive()) {
                                        currentScreenProcess = frpProcess;
                                        logger.info("已连接到 FRP 进程");
                                    } else {
                                        logger.warn("FRP 进程未启动或已结束");
                                    }
                                    break;
                                default:
                                    logger.warn("未知的进程名称：" + processName);
                                    break;
                            }
                        } else {
                            logger.warn("请指定要连接的进程，例如：screen server1");
                        }
                    } else if (currentScreenProcess != null) {
                        if (input.equalsIgnoreCase("exit")) {
                            currentScreenProcess = null;
                            logger.info("已退出 screen 模式");
                        } else if (!input.equalsIgnoreCase("stop") && !input.toLowerCase().startsWith("screen ")) {
                            sendCommandToProcess(currentScreenProcess, input);
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
        logger.info("                      可用进程名: frp, server1, server2");
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
                process.getOutputStream().write("stop\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                process.getOutputStream().flush(); // 刷新流，确保命令发送出去
            } catch (IOException e) {
                logger.error("向进程发送 stop 命令时出错", e);
            }
        }
    }
}
