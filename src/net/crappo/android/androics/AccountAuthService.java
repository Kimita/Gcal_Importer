package net.crappo.android.androics;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/* 
 * 本アプリ専用のアカウントをAndroid内に作成するためのサービス。
 * 本アプリTopActivityが起動時ににこいつを呼んでアカウント作成するが、
 * これがあることでAndroidのアカウント管理からアカウントを作成することもできる。
 */
public class AccountAuthService extends Service {
	private AndroIcsAuthenticator andAuthenticator = null;

	@Override
	public void onCreate() {
		super.onCreate();
		andAuthenticator = new AndroIcsAuthenticator(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return andAuthenticator.getIBinder();
	}
}