package com.kaipai;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 *
 * 使用方法：
 * 1. 修改下方 TABLE_NAMES 为需要生成的表名
 * 2. 修改 MODULE_NAME 为对应的模块名（影响生成的包路径）
 * 3. 直接运行 main 方法即可
 * 4. 生成的文件在 src/main/java/com/kaipai/module/{moduleName}/ 下
 */
public class CodeGenerator {

    // ========== 按需修改这里 ==========

    /** 数据库连接（改为你的实际连接） */
    private static final String DB_URL = "jdbc:mysql://101.43.57.62:3306/kaipai_dev?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "root123456";

    /** 要生成的表名（可多个，逗号分隔） */
    private static final String[] TABLE_NAMES = {
            "user"
            // 例如新增表后：
            // "new_table_name"
    };

    /** 模块名（生成到 module 下的哪个子包） */
    private static final String MODULE_NAME = "user";

    // ========== 以下一般不需要修改 ==========

    private static final String PROJECT_PATH = System.getProperty("user.dir");
    private static final String JAVA_PATH = PROJECT_PATH + "/src/main/java";
    private static final String XML_PATH = PROJECT_PATH + "/src/main/resources/mapper/" + MODULE_NAME;
    private static final String PARENT_PACKAGE = "com.kaipai";

    public static void main(String[] args) {
        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                .globalConfig(builder -> builder
                        .author("kaipai")
                        .outputDir(JAVA_PATH)
                        .commentDate("yyyy-MM-dd")
                        .disableOpenDir()
                )
                .packageConfig(builder -> builder
                        .parent(PARENT_PACKAGE)
                        .moduleName("module." + MODULE_NAME)
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .controller("controller")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, XML_PATH))
                )
                .strategyConfig(builder -> builder
                        .addInclude(TABLE_NAMES)
                        .addTablePrefix()
                        // Entity 配置
                        .entityBuilder()
                        .superClass("com.kaipai.common.entity.BaseEntity")
                        .addSuperEntityColumns(
                                "version", "deleted", "rid",
                                "create_user_id", "create_user_name", "create_time",
                                "update_user_id", "update_user_name", "last_update"
                        )
                        .enableLombok()
                        .enableTableFieldAnnotation()
                        .naming(NamingStrategy.underline_to_camel)
                        .columnNaming(NamingStrategy.underline_to_camel)
                        .logicDeleteColumnName("deleted")
                        .versionColumnName("version")
                        // Mapper 配置
                        .mapperBuilder()
                        .enableMapperAnnotation()
                        // Service 配置
                        .serviceBuilder()
                        // Controller 配置
                        .controllerBuilder()
                        .enableRestStyle()
                )
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("代码生成完毕！路径: " + JAVA_PATH + "/com/kaipai/module/" + MODULE_NAME);
    }
}
