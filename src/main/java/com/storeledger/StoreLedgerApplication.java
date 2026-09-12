package com.storeledger;

import com.storeledger.desktop.DesktopLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StoreLedgerApplication {

	public static void main(String[] args) {
		if (Boolean.getBoolean("app.desktop")) { // jpackage로 만든 Mac 앱 (-Dapp.desktop=true)
			DesktopLauncher.launch(args);
			return;
		}
		SpringApplication.run(StoreLedgerApplication.class, args);
	}

}
