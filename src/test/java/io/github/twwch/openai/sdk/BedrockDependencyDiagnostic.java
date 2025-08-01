package io.github.twwch.openai.sdk;

import software.amazon.awssdk.core.SdkSystemSetting;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.util.Properties;

/**
 * 诊断Bedrock依赖问题
 */
public class BedrockDependencyDiagnostic {
    
    public static void main(String[] args) {
        System.out.println("=== Bedrock依赖诊断 ===\n");
        
        // 1. 检查关键类的来源
        checkClassSource();
        
        // 2. 检查AWS SDK版本
        checkAwsSdkVersion();
        
        // 3. 检查系统属性
        checkSystemProperties();
        
        // 4. 检查Jackson版本
        checkJacksonVersion();
        
        // 5. 检查类加载器
        checkClassLoaders();
        
        // 6. 打印建议
        printRecommendations();
    }
    
    private static void checkClassSource() {
        System.out.println("1. 检查关键类的来源:");
        
        Class<?>[] criticalClasses = {
            software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.class,
            software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest.class,
            software.amazon.awssdk.core.SdkBytes.class,
            software.amazon.awssdk.awscore.eventstream.EventStreamResponseHandlerFromBuilder.class
        };
        
        for (Class<?> clazz : criticalClasses) {
            try {
                CodeSource source = clazz.getProtectionDomain().getCodeSource();
                if (source != null) {
                    URL location = source.getLocation();
                    System.out.println("  " + clazz.getSimpleName() + ": " + location);
                }
            } catch (Exception e) {
                System.out.println("  " + clazz.getSimpleName() + ": 无法获取来源 - " + e.getMessage());
            }
        }
        System.out.println();
    }
    
    private static void checkAwsSdkVersion() {
        System.out.println("2. 检查AWS SDK版本:");
        
        try {
            // 尝试获取版本信息
            Properties props = new Properties();
            props.load(BedrockDependencyDiagnostic.class.getResourceAsStream(
                "/META-INF/maven/software.amazon.awssdk/bedrockruntime/pom.properties"));
            System.out.println("  Bedrock Runtime版本: " + props.getProperty("version"));
        } catch (Exception e) {
            System.out.println("  无法获取Bedrock Runtime版本信息");
        }
        
        try {
            // 检查SDK核心版本
            Class<?> sdkGlobalConfig = Class.forName("software.amazon.awssdk.core.internal.SdkGlobalConfiguration");
            Method getUserAgent = sdkGlobalConfig.getDeclaredMethod("getUserAgent");
            getUserAgent.setAccessible(true);
            String userAgent = (String) getUserAgent.invoke(null);
            System.out.println("  SDK User Agent: " + userAgent);
        } catch (Exception e) {
            System.out.println("  无法获取SDK版本信息");
        }
        System.out.println();
    }
    
    private static void checkSystemProperties() {
        System.out.println("3. 检查系统属性:");
        
        String[] relevantProps = {
            "aws.region",
            "aws.accessKeyId",
            "aws.secretAccessKey",
            "software.amazon.awssdk.http.service.impl",
            "java.version",
            "java.vendor"
        };
        
        for (String prop : relevantProps) {
            String value = System.getProperty(prop);
            if (value != null) {
                System.out.println("  " + prop + " = " + value);
            }
        }
        
        // 检查环境变量
        System.out.println("\n  相关环境变量:");
        for (SdkSystemSetting setting : SdkSystemSetting.values()) {
            String envVar = setting.environmentVariable();
            String value = System.getenv(envVar);
            if (value != null) {
                System.out.println("    " + envVar + " = " + (envVar.contains("SECRET") ? "***" : value));
            }
        }
        System.out.println();
    }
    
    private static void checkJacksonVersion() {
        System.out.println("4. 检查Jackson版本:");
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            String version = mapper.version().toString();
            System.out.println("  Jackson版本: " + version);
            
            // 检查Jackson模块
            mapper.getRegisteredModuleIds().forEach(moduleId -> {
                System.out.println("  已注册模块: " + moduleId);
            });
        } catch (Exception e) {
            System.out.println("  无法获取Jackson版本信息: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void checkClassLoaders() {
        System.out.println("5. 检查类加载器:");
        
        ClassLoader cl = BedrockDependencyDiagnostic.class.getClassLoader();
        System.out.println("  当前类加载器: " + cl);
        
        ClassLoader parent = cl.getParent();
        while (parent != null) {
            System.out.println("  父类加载器: " + parent);
            parent = parent.getParent();
        }
        
        // 检查线程上下文类加载器
        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        System.out.println("  线程上下文类加载器: " + contextCl);
        System.out.println();
    }
    
    private static void printRecommendations() {
        System.out.println("=== 建议 ===");
        System.out.println("1. 确保所有AWS SDK组件版本一致（建议使用BOM）");
        System.out.println("2. 检查是否有多个版本的AWS SDK在类路径中");
        System.out.println("3. 如果使用Spring Boot，检查是否有版本冲突");
        System.out.println("4. 运行 mvn dependency:tree 查看完整的依赖树");
        System.out.println("5. 考虑排除冲突的传递依赖");
    }
}