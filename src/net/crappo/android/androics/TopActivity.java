package net.crappo.android.androics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
//import android.provider.CalendarContract;
//import android.provider.CalendarContract.Calendars;
//import android.database.Cursor;
//import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;

public class TopActivity extends Activity {
	private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;
	private static final int RL_PADDING_TOP = 5;
	private static final int RL_PADDING_BOTTOM = 10;
	private static final int LV_PADDING_LEFT = 20;
	private static final int LV_PADDING_BOTTOM = 3;
	private static final int BTN_LOAD = 1;	// ButtonClickListenerのswitch用
	private static final int BTN_NEXT = 9;	// ButtonClickListenerのswitch用
	private static final int REQ_CODE_BROWSER = 1;  // onActivityResult内で使うリクエストコード
	private static final int REQ_CODE_FILE_PICKER = 2;  // onActivityResult内で使うリクエストコード
	private static final String ICS_MIME_TYPE = "text/calendar"; // ICSデータのMIMEタイプ

    private String[] pathArray; // アプリ内に取り込み済みICSデータのpathを格納する配列
	private ViewHolderAdapter adapter; // ListViewのadapter
    private ListView lv;
    private ZipInOutMethods zipTool; // icalzipを解凍するツールクラス
	private Button btLoad;
	private Button btNext;
	private String selectedFilePath = ""; // ListViewからTapで選択されたファイルのpathを保持する

	static final Locale locale = new Locale("ja", "JP");
	static String pathOfExternalDir = Environment.getExternalStorageDirectory().getPath();
	static String pathOfDataDir;      // data/Android/packageName/file
	static String pathOfExtractedDir; // pathOfDataDir + "/" + (解凍先のDirectoryName)
	static String appName;     // 本アプリ用のアカウントをAndroidシステムに作成する時に使用
	static String packageName; // 本アプリ用のアカウントをAndroidシステムに作成する時に使用
	Resources resources;
    Model4Top model; // Calendarのデータを保持するモデルクラスのオブジェクト。AsyncTaskで作成する。
	ProgressDialog progressBar;
	TopActivity activityObj = this;
	boolean loadCompleted = false; // ICSファイルをListViewに読み込んだかどうかを示すフラグ

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_top);

		// アプリ内でICSファイルを保持する為に、ストレージの情報を取得。
		if ( pathOfExternalDir != null ) { // SDcardとか
			pathOfDataDir = getExternalFilesDir(null).toString();
		} else { // 内部ストレージ
			ApplicationInfo appinfo = getApplicationInfo();
			pathOfDataDir = appinfo.dataDir;
		}
		zipTool = new ZipInOutMethods(pathOfDataDir);
		pathOfExtractedDir = zipTool.pathToExtractDir;

		// アカウントチェック用変数の値を取得
		resources = getResources();
		packageName = resources.getResourcePackageName(R.layout.activity_top);
		appName = resources.getString(R.string.app_name);
		checkAccount(); // 本アプリ専用アカウントの端末内存在チェック(無ければ作る)

		// ICSファイルをListView表示するための準備(onCreateの時点ではdummy情報をセットするのみ)
		pathArray = new String[]{""};
		adapter = new ViewHolderAdapter(this, 0, pathArray);
		lv = (ListView)findViewById(R.id.top_listview);
		lv.setAdapter(adapter);

		// [読み込み]ボタンにリスナー設定
		btLoad = (Button)findViewById(R.id.top_button_load);
		btLoad.setTag(BTN_LOAD);
		btLoad.setOnClickListener(new ButtonClickListener());
		progressBar = new ProgressDialog(this);

		// [次へ]ボタンにリスナー設定
		btNext = (Button)findViewById(R.id.top_button_next);
		btNext.setEnabled(false);
		btNext.setTag(BTN_NEXT);
		btNext.setOnClickListener(new ButtonClickListener());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.top, menu);
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.top_menu_get_from_google: // Google CalendarからICSファイル(icalzip)を取得するケースで呼ばれる
			Intent intent = new Intent(this, BrowserForDownLoadActivity.class);
			intent.putExtra("debug", "from TopActivity");
			startActivityForResult(intent, REQ_CODE_BROWSER);
    		break;
    	case R.id.top_menu_filepicker: // ユーザが本アプリ以外の手段でストレージに保持したICSファイルを取り込む時に呼ばれる
    		pickerForIcsFile();
    		break;
    	}
    	return super.onOptionsItemSelected(item);
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if(requestCode == REQ_CODE_BROWSER){
		    if(resultCode == RESULT_OK) { // アプリ内ブラウザを使ってGoogle CalendarからICSファイル取得に成功した場合
		    	Toast.makeText(this, R.string.top_toast_download_comp, Toast.LENGTH_LONG).show();
		    	if (zipTool.extractDownloadedZip(data.getExtras().getString("fileName"))) { // zip展開成功の場合
			    	Toast.makeText(this, R.string.top_toast_extract_success, Toast.LENGTH_LONG).show();
					btNext.setEnabled(false); // この後リフレッシュされるListViewから対象を選択するまでは、ボタンをunableにする。
					pathArray = getListFilesOfDir(pathOfExtractedDir).toArray(new String[0]);
					loadCompleted = false; // ICSファイル→ListViewの読み込み状態フラグをfalseにしておく
					new TopIcsListAsync(activityObj).execute(pathArray); // ListViewをリフレッシュする
		    	} else { // zip展開失敗の場合は、その旨をToast表示
			    	Toast.makeText(this, R.string.top_toast_extract_failuer, Toast.LENGTH_LONG).show();
		    	}
		    } else { // ICSファイル取得に失敗した場合、その旨をToast表示
		    	Toast.makeText(this, R.string.top_toast_download_failuer, Toast.LENGTH_LONG).show();
		    }
	    } else if(requestCode == REQ_CODE_FILE_PICKER) { // FilePicker(API 19から実装された)呼び出しの戻り処理
		    if(resultCode == RESULT_OK) {
		    	Uri targetFileUri = data.getData(); // FilePickerが返すIntentから、ファイルのUriオブジェクトを取得
		    	String toPath = pathOfExtractedDir + new File(targetFileUri.getPath()).getName(); // ファイルのコピー先を決定
		    	if(toPath.equals(targetFileUri.getPath())) { // ユーザの選択したファイルがコピー先と同一でないことを確認
		    		Toast.makeText(this, R.string.top_toast_under_management, Toast.LENGTH_LONG).show();
		    	} else { // 問題が無ければコピーメソッドを実行
		    		copyToExtractedDir(targetFileUri, toPath);
		    	}
		    }
	    }
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(loadCompleted) {
	    	pathArray = getListFilesOfDir(pathOfExtractedDir).toArray(new String[0]);
			loadCompleted = false;
			new TopIcsListAsync(activityObj).execute(pathArray);
		}
	}

	/* 本アプリ専用アカウントが既にあるかどうかをチェックする */
	private boolean checkAccount() {
		boolean ret = false;
		AccountManager acntMgr = AccountManager.get(this);
		Account[] accounts = acntMgr.getAccountsByType(packageName);
		// アカウントが無い時は作る。
		if (accounts == null || accounts.length == 0) {
			Account account = new Account(appName, packageName);
			acntMgr.addAccountExplicitly(account, "DummyPasswd", null);
			ret = true;
		} else {
			ret = true;
		}
		return ret;
	}

	/* ListViewに表示する対象ICSファイルの一覧を、所定のディレクトリから取得する */
	private ArrayList<String> getListFilesOfDir(String targetDir) {
		File dirObj = new File(targetDir);
		ArrayList<String> al = new ArrayList<String>();
		if(dirObj.isDirectory()){
			File[] files = dirObj.listFiles();
			if ( files != null ) {
				for (File fobj : files) { al.add(fobj.getPath() ); }
			} else {
				Log.d("getListFilesOfDir", "files == null");
			}
		} else {
			Log.d("getListFilesOfDir", "isDirectory() == false : " + targetDir);
		}
		return al;
	}

	/* ユーザが端末内の任意のディレクトリからICSファイルを選択してアプリ管理下に取り込みたい場合に呼ばれるメソッド */
	private void pickerForIcsFile() {
		Intent intent = null;
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
			// API 19未満の端末の場合は、SimpleFileDialogクラスを使って機能を実現する
			SimpleFileDialog FileOpenDialog =  new SimpleFileDialog(this, "FileOpen",new SimpleFileDialog.SimpleFileDialogListener() {
				String m_chosen;
				@Override
				public void onChosenDir(String chosenFile) {
					// The code in this function will be executed when the dialog OK button is pushed
					m_chosen = chosenFile;
					// 選択されたファイルの拡張子がiCalendar用MIMEタイプ"text/calendar"に合致するかどうかチェック
					String fileName = new File(m_chosen).getName();
					int dotIndex = fileName.lastIndexOf('.');
					String ext = (dotIndex>=0) ? fileName.substring(dotIndex + 1) : null;
					String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
					if(mime != null && mime.equals(ICS_MIME_TYPE)) {
						Uri uri = new Uri.Builder().path(m_chosen).build();
				    	String toPath = pathOfExtractedDir + fileName;
//						Toast.makeText(TopActivity.this, "Chosen File: " + m_chosen, Toast.LENGTH_LONG).show();
						copyToExtractedDir(uri, toPath); // 選択されたファイルをUriオブジェクトで、コピー先のpathをStringで渡す
					} else {
						Toast.makeText(TopActivity.this, R.string.top_toast_picker_badchoice + m_chosen, Toast.LENGTH_LONG).show();
					}
				}
			});
			FileOpenDialog.Default_File_Name = "default.ics";
			FileOpenDialog.chooseFile_or_Dir();

		} else {
			// API 19以降はとても便利なフレームワークが用意されているので、それを使う。
		    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		    intent.addCategory(Intent.CATEGORY_OPENABLE);
		    intent.setType(ICS_MIME_TYPE);
		    startActivityForResult(intent, REQ_CODE_FILE_PICKER);
		}
	}

	/* 下記のいずれかのメソッドから呼ばれるメソッド
	 *   ・pickerForIcsFile()
	 *   ・onActivityResult(int requestCode, int resultCode, Intent data)
	 * ユーザが選択したローカルICSファイルを、本アプリの管理下に取り込む(所定のディレクトリにコピーする)。
	 */
	private void copyToExtractedDir(Uri fromFUri, String toPath) {
    	// pathOfExtractedDir 配下へコピーする。
		InputStream in = null;
		try {
	        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
	        	in = getContentResolver().openInputStream(fromFUri);
	        } else {
	        	in = new FileInputStream(new File(fromFUri.getPath()));
	        }
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			BufferedWriter bw = new BufferedWriter(new FileWriter(toPath));
			String buff = null;
			while((buff = br.readLine()) != null)	bw.write(buff + "\r\n");
			br.close();
			bw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

    	// その後、ListViewをリフレッシュする。
		btNext.setEnabled(false);
    	pathArray = getListFilesOfDir(pathOfExtractedDir).toArray(new String[0]);
		loadCompleted = false;
		new TopIcsListAsync(activityObj).execute(pathArray);
	}

	/* 
	 * レイアウトのListView部分を更新するメソッド。
	 * 所定のディレクトリにICSファイルを取り込む処理が、その終了時にこのメソッドを呼ぶ。
	 * やっていることは、ListViewのadapterを、新たに作り直したadapterオブジェクトに繋ぎ換えるというもの。
	 */
	public void refreshListView(String[] pathArray){
		if(pathArray.length == 0) {
			Toast.makeText(this, R.string.top_toast_load_no_files, Toast.LENGTH_LONG).show();
			pathArray = new String[]{""};
		}

		adapter = new ViewHolderAdapter(this, 0, pathArray);
		lv.setAdapter(adapter);
		lv.setOnItemClickListener(new ItemOnClick());
	}

	/* [読み込む]ボタンと[次へ]ボタンの、タップされた時の処理 */
	private class ButtonClickListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			Intent intent;
			switch((Integer)v.getTag()){
			case BTN_LOAD:  // [読み込む]ボタンが押されたら、[次へ]ボタンを無効にして所定のpathからICSデータを読み込む。
				btNext.setEnabled(false);
				pathArray = getListFilesOfDir(pathOfExtractedDir).toArray(new String[0]);
				new TopIcsListAsync(activityObj).execute(pathArray);
				break;
			case BTN_NEXT: // [次へ]ボタンが押されたら、選択されているICSファイルの情報を次のActivityへ渡す。
				intent = new Intent(TopActivity.this, ShowEventListActivity.class);
				intent.putExtra("SELECTED_FILE_PATH", selectedFilePath);
				startActivity(intent);
				break;
			}
		}
	}

	/* ListView中のICSファイル選択状況のリスナー */
	private class ItemOnClick implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			selectedFilePath = ((ListView)parent).getAdapter().getItem(position).toString();
			clearBackgroundColor(parent); // ListViewの持つ全子要素の背景色をクリアする
			v.setBackgroundColor(0xffbbbbbb); // 選択された子要素の背景だけ、指定の色にする
			if(!selectedFilePath.equals(""))	btNext.setEnabled(true); // [次へ]ボタンを有効にする
		}

		/* ListViewの持つ全子要素の背景色をクリアする */
		void clearBackgroundColor(AdapterView<?> parent) {
	        if (parent instanceof ViewGroup) {
	            ViewGroup layout = (ViewGroup) parent;
	            for (int i = 0; i < layout.getChildCount(); i++) {
	            	layout.getChildAt(i).setBackgroundColor(0xffffffff);
	            }
	        }
	    }
	}

	/* ListViewの子要素になるViewの構成を持つHolder */
	private class ViewHolder {
		ArrayList<TextView> tvArray = new ArrayList<TextView>();
		RelativeLayout rlay;

	    ViewHolder(Context context) {
	    	rlay = new RelativeLayout(context);
	    	rlay.setPadding(0, RL_PADDING_TOP, 0, RL_PADDING_BOTTOM);
	    	rlay.setBackgroundColor(0xffffffff);

	    	// 取得日(Obtained Date)を表示するTextView
			RelativeLayout.LayoutParams lpTrContents = new RelativeLayout.LayoutParams(WC, WC);
			TextView tv1 = new TextView(context);
			tv1.setId(R.id.lv_tv1);
			tvArray.add(tv1);
			rlay.addView(tv1, lpTrContents);
			tv1.setPadding(LV_PADDING_LEFT, 0, 0, LV_PADDING_BOTTOM);

	    	// カレンダー名(Calendar Name)を表示するTextView
			lpTrContents = new RelativeLayout.LayoutParams(WC, WC);
			TextView tv2 = new TextView(context);
			tv2.setId(R.id.lv_tv2);
			tvArray.add(tv2);
			rlay.addView(tv2, lpTrContents);
			lpTrContents.addRule(RelativeLayout.BELOW, R.id.lv_tv1);
			tv2.setPadding(LV_PADDING_LEFT, 0, 0, LV_PADDING_BOTTOM);
	    }
	}

	/* ViewHolderをListViewにpathArrayの要素数分だけ差し込む処理をするadapter */
	private class ViewHolderAdapter extends ArrayAdapter<String> {
		ViewHolder holder;
		public ViewHolderAdapter (TopActivity actObj, int resource, String[] pathArray) {
			super(actObj, resource, pathArray);
		}
        @Override
	      public View getView(int position, View convertView, ViewGroup parent) {
        	// 下記のif文はArrayAdapterにおけるconvertView使い回しの常套句のようなものだと思われる。
        	if (convertView == null) { // 自前のlayoutをconvertViewにsetする
        		holder = new ViewHolder(activityObj);
        		convertView = holder.rlay;
        		convertView.setTag(holder);
        	} else { // 既にあったら再利用する。
        		holder = (ViewHolder) convertView.getTag();
        	}

        	File fileObj = new File((String)getItem(position));
        	if(fileObj.getPath() == null || fileObj.getPath().equals("") ) { // ICSデータへのpathが空の場合
            	for (TextView entry : holder.tvArray) { // Dummyデータをsetする
            		switch(entry.getId()){
            		case R.id.lv_tv1:
            			entry.setText(resources.getString(R.string.top_listview_title_date) + ": "
            					+ resources.getString(R.string.top_listview_value_unknown));
            			break;
            		case R.id.lv_tv2:
            			entry.setText(resources.getString(R.string.top_listview_title_name) + ": "
            					+ resources.getString(R.string.top_listview_value_unknown));
            			break;
            		}
            	}
        	} else { // pathArray[position]が空でない場合
            	for (TextView entry : holder.tvArray) {
            		switch(entry.getId()){
            		case R.id.lv_tv1: // ICSデータの取得日(ファイルのTimeStampをformatしたもの)を表示
            			entry.setText(resources.getString(R.string.top_listview_title_date) + ": "
            					+ new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", locale).format(fileObj.lastModified()));
            			break;
            		case R.id.lv_tv2: // modelオブジェクトから当該のカレンダー名(positionが一致するもの)を取得して表示
            			entry.setText(resources.getString(R.string.top_listview_title_name) + ": "
            					+ model.calNames.get(position).getValue());
            			break;
            		}
            	}
        	}
	        return convertView;
	    }
	}

//    /* for Debug */
//    private void showChild(View v, StringBuilder sbTabs) {
//        Log.d("showChild", sbTabs + v.getClass().getSimpleName());
//        if (v instanceof ViewGroup) {
//            ViewGroup layout = (ViewGroup) v;
//            sbTabs = sbTabs.append("    ");
//            for (int i = 0; i < layout.getChildCount(); i++) {
//                showChild(layout.getChildAt(i), new StringBuilder(sbTabs));
//            }
//        }
//    }
//	// 上記メソッドは、下記のようにして使う。
//	StringBuilder sbTabs;
//	sbTabs = new StringBuilder();
//	showChild(parent, sbTabs);
//	Log.v("itemOnClick", "showChild(parent, sbTabs): " + sbTabs);
//

}
