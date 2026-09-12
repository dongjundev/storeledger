package com.storeledger.desktop;

import com.storeledger.StoreLedgerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.awt.desktop.AppReopenedListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * jpackage로 만든 Mac 앱의 실행 모드. {@code -Dapp.desktop=true}면 {@link StoreLedgerApplication#main}이 여기로 위임한다.
 * <ul>
 *   <li>데이터·로그는 {@code ~/StoreLedger} 아래 (application-desktop.properties 의 {@code app.home})</li>
 *   <li>서버가 뜨면 기본 브라우저를 열고, Dock 아이콘을 다시 클릭하면 브라우저를 다시 연다</li>
 *   <li>Cmd+Q / Dock의 종료 → 서버를 정리하고 끝낸다. 자동 시작은 하지 않는다</li>
 *   <li>실행에 실패하면 로그만 남기지 않고 대화상자로 알린다</li>
 *   <li>같은 사용자가 복사본을 또 켜면 먼저 켠 앱의 화면만 연다 (~/StoreLedger/app.lock)</li>
 * </ul>
 */
public final class DesktopLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);
    private static final Path HOME = Path.of(System.getProperty("user.home"), "StoreLedger");
    /** 개발용 8080과 겹치지 않는 포트. 같은 컴퓨터에서만 접속된다 (application-desktop.properties 의 server.address). */
    private static final int PORT = 28080;
    private static final String URL = "http://localhost:" + PORT + "/";

    /** 앱이 켜져 있는 동안 쥐고 있는 잠금. 프로세스가 끝나면 OS가 푼다. */
    private static FileLock instanceLock;

    private DesktopLauncher() {
    }

    public static void launch(String[] args) {
        System.setProperty("app.home", HOME.toString());
        System.setProperty("java.awt.headless", "false"); // Spring Boot 기본값은 headless. 브라우저 열기·Dock 처리·오류 대화상자에 AWT가 필요하다

        // 다른 위치의 복사본을 또 켠 경우: 먼저 켠 쪽이 준비될 때까지 기다렸다가 화면만 연다.
        // HTTP 응답만 보면 켜지는 중인 앱을 놓치고, 다른 사용자 계정의 앱을 자기 것으로 착각하므로 사용자별 잠금 파일로 판단한다
        if (!acquireInstanceLock()) {
            if (waitUntilResponding(Duration.ofSeconds(30))) {
                openBrowser(URL);
                System.exit(0);
            }
            showErrorAndExit("이미 켜져 있는 앱이 응답하지 않습니다. Dock에서 앱을 종료한 뒤 다시 열어 주세요.", null);
        }

        ConfigurableApplicationContext context;
        try {
            context = new SpringApplicationBuilder(StoreLedgerApplication.class)
                    .profiles("desktop")
                    .properties("server.port=" + PORT)
                    .run(args);
        } catch (Exception e) {
            handleStartupFailure(e);
            return;
        }

        installMacHandlers(context);
        openBrowser(URL);
    }

    private static void installMacHandlers(ConfigurableApplicationContext context) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> {
                log.info("종료 요청: 서버를 정리합니다");
                context.close();
                response.performQuit();
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
            desktop.addAppEventListener((AppReopenedListener) event -> openBrowser(URL));
        }
    }

    private static void openBrowser(String url) {
        try {
            log.info("브라우저 열기: {}", url);
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | RuntimeException e) {
            log.warn("브라우저를 열지 못했습니다. 직접 여세요: {}", url, e);
        }
    }

    private static void handleStartupFailure(Exception e) {
        String message = findCause(e, PortInUseException.class) != null
                ? "포트 " + PORT + "을(를) 다른 프로그램이나 다른 사용자 계정에서 켠 이 앱이 쓰고 있어 실행할 수 없습니다."
                : "실행 중 문제가 생겼습니다.\n\n" + rootCause(e).getMessage();
        showErrorAndExit(message, e);
    }

    private static void showErrorAndExit(String message, Exception cause) {
        log.error("실행 실패: {}", message, cause);
        JOptionPane.showMessageDialog(null,
                message + "\n\n자세한 내용: " + HOME.resolve("logs").resolve("app.log"),
                System.getProperty("app.store-name", "Store Ledger"), JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    /** ~/StoreLedger/app.lock 을 잠근다. 같은 사용자의 다른 복사본이 켜져 있거나 켜지는 중이면 false. */
    private static boolean acquireInstanceLock() {
        try {
            Files.createDirectories(HOME);
            FileChannel channel = FileChannel.open(HOME.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            instanceLock = channel.tryLock();
            if (instanceLock == null) {
                channel.close();
            }
            return instanceLock != null;
        } catch (IOException | OverlappingFileLockException e) {
            log.warn("실행 잠금을 확인하지 못해 그대로 시작합니다", e); // DB 파일 잠금이 마지막 안전장치다
            return true;
        }
    }

    private static boolean waitUntilResponding(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isStoreLedger(PORT)) {
                return true;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 해당 포트에서 이 앱이 응답하는지 확인한다 (다른 프로그램이 포트를 쓰는 경우와 구분). */
    private static boolean isStoreLedger(int port) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/settings")
                    .toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            if (connection.getResponseCode() != 200) {
                return false;
            }
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("storeName");
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
