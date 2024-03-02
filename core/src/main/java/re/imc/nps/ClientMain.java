package re.imc.nps;

import lombok.Getter;
import lombok.Setter;
import re.imc.nps.config.NpsConfig;
import re.imc.nps.process.NpsProcess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ClientMain {

    @Getter
    private static NpsProcess process;
    @Getter
    private static NpsConfig config;
    public static String TOKEN;
    public static Path DATA_PATH;

    @Setter
    private static Consumer<NpsProcess> startHandler;

    @Setter
    @Getter
    private static Consumer<String> outHandler;
    @Setter
    @Getter
    private static Consumer<String> logHandler;



    public static void start(Path path) {
        DATA_PATH = path;
        path.toFile().mkdirs();
        readToken();
        if (TOKEN == null) {
            return;
        }
        registerCloseHook();
        config = loadNps();
        startHandler.accept(process);
    }
    public static void readToken() {
        TOKEN = System.getProperty("nps.accesstoken", null);

        if (TOKEN != null) {
            return;
        }
        Path file = DATA_PATH.resolve("token.txt");
        if (!file.toFile().exists()) {

            try {
                InputStream in = ClientMain.class.getClassLoader().getResourceAsStream("token.txt");

                Files.copy(in, file);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            TOKEN = Files.readAllLines(file).get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public static NpsConfig loadNps() {

        File file = new File(DATA_PATH.toFile(), Info.NPS_PATH);

        file.mkdirs();

        Info.SystemType type = Info.checkSystemType();

        String npsName = type == Info.SystemType.WINDOWS ? "npc.exe" : "npc";
        File npsFile = new File(file, npsName);
        InputStream in = ClientMain.class.getClassLoader().getResourceAsStream(npsName);

        if (!npsFile.exists()) {
            try {
                assert in != null;
                Files.copy(in, npsFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        npsFile.setReadable(true);
        npsFile.setExecutable(true);
        npsFile.setWritable(true);
        NpsConfig config = NpsConfig.generateConfig(TOKEN);
        if (config == null) {
            getLogHandler().accept("无法获取npc config,重新连接中...");
            Executors.newSingleThreadScheduledExecutor()
                            .schedule(() -> ClientMain.start(ClientMain.DATA_PATH), 3, TimeUnit.SECONDS); ;
            return null;
        }
        process = new NpsProcess(DATA_PATH + "/" + Info.NPS_PATH, type, config);
        process.start();
        return config;
    }


    //注册关闭钩子
    public static void registerCloseHook() {

        Runtime.getRuntime().addShutdownHook((new Thread(() -> {
            try{
                process.stop();
            }catch (Exception e) {
                e.printStackTrace();
            }
        })));
    }
}