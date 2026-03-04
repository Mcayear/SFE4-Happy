package cn.mcayear.sfe4happy.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static cn.mcayear.sfe4happy.Main.logger;

public class ProcessManager {

    public static Process startProcess(List<String> command, String processName, File workDir) throws IOException, InterruptedException {
        logger.info("启动进程: {}", processName);

        if (command == null || command.isEmpty()) {
            logger.error("命令列表为空，无法启动进程: {}", processName);
            throw new IllegalArgumentException("command is empty");
        }

        String originalFirst = command.get(0);
        Path workPath = workDir.toPath().toAbsolutePath().normalize();
        Path resolvedPath = workPath.resolve(originalFirst).normalize();
        command.set(0, resolvedPath.toString());

        ensureExecutableIfPossible(resolvedPath);
        String firstLine = readFirstLine(resolvedPath);
        boolean hasCrlf = containsCarriageReturn(resolvedPath);
        logScriptHeader(firstLine);

        List<String> processCommand = prepareCommand(command, resolvedPath, firstLine, hasCrlf);

        // 创建 ProcessBuilder 对象
        ProcessBuilder processBuilder = new ProcessBuilder(processCommand);
        processBuilder.directory(workDir);
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            logger.error("启动进程失败，命令: {}", processBuilder.command());
            List<String> fallbackCommand = buildFallbackCommand(processCommand, resolvedPath, firstLine, hasCrlf);
            if (fallbackCommand != null) {
                logger.warn("尝试使用备用命令启动: {}", fallbackCommand);
                processBuilder = new ProcessBuilder(fallbackCommand);
                processBuilder.directory(workDir);
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
            } else {
                throw e;
            }
        }

        // 在新线程中异步读取并输出脚本的执行过程
        Process finalProcess = process;
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
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

    private static void ensureExecutableIfPossible(Path path) {
        if (path == null) {
            return;
        }
        if (Files.exists(path) && Files.isRegularFile(path) && !Files.isExecutable(path)) {
            boolean changed = path.toFile().setExecutable(true);
            logger.warn("目标文件不可执行，尝试设置可执行权限: result={}", changed);
        }
    }

    private static String readFirstLine(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.readLine();
        } catch (Exception e) {
            logger.warn("读取脚本首行失败: {}", e.toString());
            return null;
        }
    }

    private static void logScriptHeader(String firstLine) {
        if (firstLine == null) {
            return;
        }
        if (firstLine.contains("\r")) {
            logger.warn("脚本首行包含CR字符，可能是CRLF导致解释器路径错误");
        }
    }

    private static List<String> prepareCommand(List<String> command, Path resolvedPath, String firstLine, boolean hasCrlf) {
        List<String> processCommand = new ArrayList<>(command);
        if (firstLine == null || !firstLine.startsWith("#!")) {
            return processCommand;
        }
        boolean hasCr = firstLine.contains("\r");
        String interpreter = parseInterpreter(firstLine);
        Path interpreterPath = resolveInterpreterPath(interpreter);
        if (interpreterPath == null) {
            logger.warn("无法解析脚本解释器路径");
        }
        if (hasCrlf) {
            List<String> stripCommand = buildCrlfStrippedCommand(resolvedPath, interpreterPath, processCommand);
            if (stripCommand != null) {
                logger.warn("使用去CR方式执行脚本: {}", stripCommand);
                return stripCommand;
            }
        }
        if (interpreterPath == null || !Files.exists(interpreterPath) || hasCr) {
            List<String> fallback = buildInterpreterCommand(resolvedPath, processCommand);
            if (fallback != null) {
                logger.warn("使用解析器方式执行脚本: {}", fallback);
                return fallback;
            }
        }
        return processCommand;
    }

    private static List<String> buildFallbackCommand(List<String> processCommand, Path resolvedPath, String firstLine, boolean hasCrlf) {
        if (resolvedPath == null || !Files.isRegularFile(resolvedPath)) {
            return null;
        }
        
        String fileName = resolvedPath.getFileName() != null ? resolvedPath.getFileName().toString().toLowerCase() : "";
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        if (isWindows && (fileName.endsWith(".bat") || fileName.endsWith(".cmd"))) {
            List<String> cmdCommand = new ArrayList<>();
            cmdCommand.add("cmd.exe");
            cmdCommand.add("/c");
            cmdCommand.addAll(processCommand);
            return cmdCommand;
        }

        if (hasCrlf) {
            Path interpreterPath = firstLine != null && firstLine.startsWith("#!") ? resolveInterpreterPath(parseInterpreter(firstLine)) : null;
            List<String> stripCommand = buildCrlfStrippedCommand(resolvedPath, interpreterPath, processCommand);
            if (stripCommand != null) {
                return stripCommand;
            }
        }
        if (firstLine != null && firstLine.startsWith("#!")) {
            return buildInterpreterCommand(resolvedPath, processCommand);
        }
        if (fileName.endsWith(".sh")) {
            return buildInterpreterCommand(resolvedPath, processCommand);
        }
        return null;
    }

    private static List<String> buildInterpreterCommand(Path scriptPath, List<String> currentCommand) {
        String interpreter = findExecutableInPath("bash");
        if (interpreter == null) {
            interpreter = findExecutableInPath("sh");
        }
        if (interpreter == null) {
            Path bashPath = Paths.get("/bin/bash");
            if (Files.exists(bashPath)) {
                interpreter = bashPath.toString();
            }
        }
        if (interpreter == null) {
            Path shPath = Paths.get("/bin/sh");
            if (Files.exists(shPath)) {
                interpreter = shPath.toString();
            }
        }
        if (interpreter == null) {
            return null;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add(interpreter);
        fallback.add(scriptPath.toString());
        if (currentCommand.size() > 1) {
            fallback.addAll(currentCommand.subList(1, currentCommand.size()));
        }
        return fallback;
    }

    private static String parseInterpreter(String firstLine) {
        String line = firstLine.substring(2).trim();
        if (line.isEmpty()) {
            return null;
        }
        if (line.startsWith("/usr/bin/env")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return parts[1].trim();
            }
            return null;
        }
        String[] parts = line.split("\\s+");
        return parts.length > 0 ? parts[0].trim() : null;
    }

    private static Path resolveInterpreterPath(String interpreter) {
        if (interpreter == null || interpreter.isEmpty()) {
            return null;
        }
        if (interpreter.startsWith("/")) {
            return Paths.get(interpreter).normalize();
        }
        String inPath = findExecutableInPath(interpreter);
        if (inPath != null) {
            return Paths.get(inPath).normalize();
        }
        return null;
    }

    private static String findExecutableInPath(String name) {
        String pathVar = System.getenv("PATH");
        if (pathVar == null || pathVar.isEmpty()) {
            return null;
        }
        String[] parts = pathVar.split(":");
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            Path candidate = Paths.get(part).resolve(name);
            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static boolean containsCarriageReturn(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\r') {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检测CRLF失败: {}", e.toString());
        }
        return false;
    }

    private static List<String> buildCrlfStrippedCommand(Path scriptPath, Path interpreterPath, List<String> currentCommand) {
        String bash = findExecutableInPath("bash");
        if (bash == null) {
            Path bashPath = Paths.get("/bin/bash");
            if (Files.exists(bashPath)) {
                bash = bashPath.toString();
            }
        }
        if (bash == null) {
            return null;
        }

        String interpreter = bash;
        if (interpreterPath != null && Files.exists(interpreterPath)) {
            interpreter = interpreterPath.toString();
        } else {
            String sh = findExecutableInPath("sh");
            if (sh != null) {
                interpreter = sh;
            } else {
                Path shPath = Paths.get("/bin/sh");
                if (Files.exists(shPath)) {
                    interpreter = shPath.toString();
                }
            }
        }

        List<String> command = new ArrayList<>();
        command.add(bash);
        command.add("-c");
        command.add("exec \"$0\" <(tr -d '\\r' < \"$1\") \"${@:2}\"");
        command.add(interpreter);
        command.add(scriptPath.toString());
        if (currentCommand != null && currentCommand.size() > 1) {
            command.addAll(currentCommand.subList(1, currentCommand.size()));
        }
        return command;
    }

}
