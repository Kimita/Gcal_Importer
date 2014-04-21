package net.crappo.android.androics;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

import android.os.Bundle;
import android.os.StrictMode;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

/*
 * Webkitを使ってgoogleアカウントの「カレンダーをエクスポート」のURLへアクセスするActivity。
 * 当然、認証処理が発生するのでユーザにgmailアドレスとパスワードを入力してもらう必要がある。
 * (2段階認証もWebkitが勝手に上手く処理してくれるらしい)
 */
@SuppressLint("SetJavaScriptEnabled")
public class BrowserForDownLoadActivity extends Activity {
	private static final String TAG = "BrowserForDownLoad";
	private static final String firstUrl = "file:///android_asset/redirect.html";
	private static String obtainedName = "tmp.zip"; // 後の処理で書き換わる

	private WebView webView;
    private Intent intent;
    private String downloadStatus = ""; // 成功したか失敗したかの情報を格納する。
    private BrowserForDownLoadActivity activityObj;

    private CookieManager cookMgr;
    private CookieStore cookStore;
	private HttpClient client;
	private URL urlObj;
	private HttpGet httpGet;
	private ProgressDialog loading;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_browser_for_download);
		intent = getIntent();

		Log.v(TAG, "onCreate() here.");
		activityObj = this; // for inner class's Toast
		RelativeLayout rl = (RelativeLayout)findViewById(R.id.browser_layout);
		loading = new ProgressDialog(this);

		// WebViewをlayoutに組み込んで設定をいくつかする
		webView = new WebView(this);
		webView.setVerticalScrollbarOverlay(true);
		webView.setWebViewClient(new CustomWebViewClient());
		rl.addView(webView);
		WebSettings webSettings = webView.getSettings();
		webSettings.setSupportMultipleWindows(true);
		webSettings.setLoadsImagesAutomatically(true);
		webSettings.setJavaScriptEnabled(true);

		// assetにあるHTMLデータから目的のgoogleカレンダーエクスポートURLへアクセスする
		webView.loadUrl(firstUrl);
		
		// WebViewにプログレスバーとダウンロードリスナーをセットする。
		webView.setWebChromeClient(new ShowProgressBar());
		webView.setDownloadListener(new WebDownloadListener());

	}

	@Override
	protected void onResume() { // 何か上手くいってなかったらとりあえずダウンロード失敗扱いにして戻る
		super.onResume();
		Log.v(TAG, "onResume() here.");
		intent = getIntent();
		if (downloadStatus.equals("Download FAILD")) {
        	setResult(RESULT_CANCELED, intent);
        	finish();
        }
	}

	@Override
	protected void onDestroy() { // このActivityを終える時には余計なデータを全部消す
		Log.v(TAG, "onDestroy() here.");
    	webView.clearHistory();
    	if(cookStore != null)	cookStore.clear();
    	cookMgr.removeAllCookie();
		super.onDestroy();
	}

	/* xmlレイアウト内でButtonオブジェクトに対して android:onClick で定義した名前と対応付けたメソッド。 */
	public void onClickReloadBtn(View v) {
		webView.reload();
	}
	/* xmlレイアウト内でButtonオブジェクトに対して android:onClick で定義した名前と対応付けたメソッド。 */
	public void onClickFinish(View v) {
    	setResult(RESULT_CANCELED, intent);
		finish();
	}

	/* icalzipの取得が完了してTopActivityに戻る時に呼ばれるメソッド */
	private void successFinish() {
		Log.v(TAG, "successFinish() here.");
		Intent intent = getIntent();
		setResult(RESULT_OK, intent);
    	intent.putExtra("fileName", obtainedName);
		finish();
	}

	/*
	 * onCreate中にwebViewへsetWebChromeClientする時にnewされるクラス。
	 * progressBar表示/表示のタイミングはこいつにまかせるのみ。
	 */
	class ShowProgressBar extends WebChromeClient {
		  @Override
		  public void onProgressChanged(WebView view, int newProgress) {
		    View pBarWrapper = findViewById(R.id.browser_rogress_bar_wrapper);
		    ProgressBar pBar = (ProgressBar) findViewById(R.id.browser_progress_bar);
		    pBar.setProgress(newProgress);
		    if (newProgress == 100)	pBarWrapper.setVisibility(View.GONE);
		    else					pBarWrapper.setVisibility(View.VISIBLE);
		  }
	}

	/*
	 * Google Calendarからicalzipをダウンロードするリクエストのタイミングで呼ばれてるリスナー。
	 * call判定についてはWebViewClientまかせなのでよく解らない。
	 */
	class WebDownloadListener implements DownloadListener {
	  @Override
	  public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
		Log.v("onDownloadStart", "start");
		// 以下の処理をHttpClientではなくDownloadManagerにさせることも検討したのだが、
		// googleの認証をDownloadManagerに渡すことが上手くできずにいた。
		// あれこれ試して下記のやり方で上手くいったので、このままにしている。

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // cookieを取り扱う準備
        String cookStr = "";
		cookMgr = CookieManager.getInstance();
	    cookStr = cookMgr.getCookie(url);
		Log.v("onDownloadStart", "Cookie: " + cookStr);

        // HTTP通信を実行してresponseからheaderを読み取りストリームから内容を読み出す
        httpGet = new HttpGet(url);
        httpGet.setHeader("Cookie", cookStr);
        client = new DefaultHttpClient();
        try {
            client.execute(httpGet, new ResponseHandler<String>(){
                @Override
                public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                	int resCode = response.getStatusLine().getStatusCode();
        			Log.v("handleResponse", "resCode: " + resCode);
                    switch (response.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_OK:
                    	InputStream inContent = response.getEntity().getContent();
                    	String[] str = null;
                    	for (Header header : response.getAllHeaders()) {
                    		String name = header.getName();
                    		String val  = header.getValue();
                    		// ファイル名だけを抜き出すための処理
                    		if(name.equals("Content-disposition")) {
                    			str = val.split(";");
                    			str = str[1].split("=");
                    			obtainedName = str[1];
                    		}
                    	}
                    	Log.d("handleResponse", "filename: " + obtainedName);

                    	// 取得したicalzipを所定のpathに置く処理。(ここでは置くだけ。zipを展開するのはTopActivityに戻ってから)
                    	DataInputStream dataInStream =new DataInputStream(inContent);
                    	String toPath = TopActivity.pathOfDataDir + ZipInOutMethods.outputDir + obtainedName;
                    	FileOutputStream outStream = new FileOutputStream(toPath);
        				Log.v("handleResponse", "file: " + toPath);
        				DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(outStream));
        		        // Read/Write Data
        		        byte[] b= new byte[4096];
        		        int readByte = 0;
        				while(-1 != (readByte = dataInStream.read(b))){
        					dataOutStream.write(b, 0, readByte);
        				}
        				dataInStream.close();
        			    dataOutStream.close();
        			    downloadStatus = "Success";
                        return null;
                    case HttpStatus.SC_NOT_FOUND:
                        throw new RuntimeException("HTTP Status : 404 Not Found.");
                    default:
                        throw new RuntimeException("HTTP Status : Error.");
                    }
                }
            });
        } catch (ClientProtocolException e) {
        	e.printStackTrace();
		    downloadStatus = "Download FAILD";
        } catch (IOException e) {
        	e.printStackTrace();
		    downloadStatus = "Download FAILD";
        } finally {	client.getConnectionManager().shutdown(); }
	  }
	}

	class CustomWebViewClient extends WebViewClient {
		String fileName = "";
        String pathName = "";
        String hostName = "";
        String protocolName = "";

        @Override
        public void onPageStarted(WebView webView, String urlStr, Bitmap favicon) {
        	super.onPageStarted(webView, urlStr, favicon);
            Log.v("onPageStarted", urlStr);
            urlParse(urlStr);
            loading.show(); // for old devices.(maybe single core)
//          dispCookie(urlStr); // for Debug
        }

		@Override
		public void onPageFinished(WebView view, String urlStr) {
			super.onPageFinished(view, urlStr);
            Log.v("onPageFinished", urlStr);
            saveCookie(urlStr);
            if (loading.isShowing()) { loading.dismiss(); } // for old devices.(maybe single core)
            if(downloadStatus.equals("Success")) {
                Log.v("onPageFinished", "Download finished successfully.");
                successFinish();
            }
		}

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.v("shouldOverrideUrlLoading", "OverrideUrl here.");
            urlParse(url);
            if ( hostName.equals("accounts.google.com") && pathName.equals("/SignUp")) {
                // ユーザが余計な画面遷移(アカウントの作成)を行った時には「お断り」をする。
                view.loadUrl(BrowserForDownLoadActivity.firstUrl);
                Log.v("shouldOverrideUrlLoading", "'Create New Account' is not Supported this application.");
                Toast.makeText(activityObj, R.string.browser_toast, Toast.LENGTH_LONG).show();
         		return false;
			} else if ( hostName.equals("www.google.com") && pathName.equals("/accounts/recovery")) {
	            // ユーザが余計な画面遷移(パスワードを忘れた場合のrecovery画面)を行った時には「お断り」をする。
				webView.loadUrl("file:///android_asset/announce.html");
        		Log.v("shouldOverrideUrlLoading", "'Account Recovery' is not Supported this application.");
        		return false;
        	}
            view.loadUrl(url);
            return false;
        }

        /* 後の処理で使うためにURLをparseした結果をそれぞれの変数に格納しておく */
        private void urlParse(String url) {
            try {
                urlObj = new URL(url);
                fileName = urlObj.getFile();
                pathName = urlObj.getPath();
                hostName = urlObj.getHost();
                protocolName = urlObj.getProtocol();
                Log.v("urlParse", protocolName + " - " + hostName + " - " + pathName + " - " + fileName);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
        }

        /* DownloadListenerのHttpClientでicalzipをダウンロードするために、アカウント認証のcookieを保持しておく */
        private void saveCookie(String urlStr) {
            urlParse(urlStr);

		    // WebViewのCookieを取得
    		cookMgr = CookieManager.getInstance();
		    String cookie = cookMgr.getCookie(urlStr);
//		    Log.v("saveCookie", "cookie - " + cookie);
		    if (cookie != null) {
			    String[] cookies = cookie.split(";");
			    for (String keyValue : cookies) {
					keyValue = keyValue.trim();
					int index = keyValue.indexOf("=");
					String[] cookieSet = new String[] { keyValue.substring(0, index), keyValue.substring(index + 1) };
//				    Log.v("saveCookie", "cookieSet - " + cookieSet[0] + " = " + cookieSet[1]);
					// Cookieを作成
					BasicClientCookie bCookie = new BasicClientCookie(cookieSet[0], cookieSet[1]);
					bCookie.setDomain(urlObj.getHost());
					bCookie.setPath("/");
					// CookieStoreを取得
					DefaultHttpClient defHttpClient;
					if (client != null)	defHttpClient = (DefaultHttpClient)client;
					else				defHttpClient = new DefaultHttpClient();
					cookStore = defHttpClient.getCookieStore();
					// Cookieを追加
					cookStore.addCookie(bCookie);
			    }
    		}
        }

//      private void dispCookie(String urlStr) {
//  		cookMgr = CookieManager.getInstance();
//		    String cookie = cookMgr.getCookie(urlStr);
//		    Log.v("dispCookie", "cookie - " + cookie);
//		    if (cookie != null) {
//			    String[] cookies = cookie.split(";");
//			    for (String keyValue : cookies) {
//					keyValue = keyValue.trim();
//					int index = keyValue.indexOf("=");
//					String[] cookieSet = new String[] { keyValue.substring(0, index), keyValue.substring(index + 1) };
//				    Log.v("dispCookie", "cookieSet - " + cookieSet[0] + " = " + cookieSet[1]);
//			    }
//		    }
//      }

	}


}
