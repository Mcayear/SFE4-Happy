package cn.mcayear.sfe4happy.process;

import cn.mcayear.sfe4happy.config.InfoConfig;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static cn.mcayear.sfe4happy.Main.logger;

public class ParsePackage {

    InfoConfig config;

    public ParsePackage(File file) {
        logger.info("正在解析文件: " + file.getAbsolutePath());

        // 使用 Gson 解析 JSON 文件
        Gson gson = new Gson();

        try (FileReader reader = new FileReader(file)) {
            // 将 JSON 数据映射到 Info 类
            config = gson.fromJson(reader, InfoConfig.class);
        } catch (IOException e) {
            logger.error("IO 错误: ", e);
        } catch (JsonSyntaxException e) {
            logger.error("JSON 语法错误: ", e);
        }
    }
}
