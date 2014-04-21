package net.crappo.android.androics;

import android.app.ProgressDialog;
import android.os.AsyncTask;

/*
 * ICSデータを順次読み込んで、表示に必要なプロパティをmodelに保持させるAsyncTask。
 */
public class TopIcsListAsync extends AsyncTask<String, Void, Void>{
	Model4Top model;
	String[] pathList;
	TopActivity activityObj;

	/* コンストラクタ */
	public TopIcsListAsync(TopActivity actObj) {
		this.activityObj = actObj; // progress表示や変数のセットの為にActivityのオブジェクトを受け取っておく
		model = new Model4Top();   // modelオブジェクトを初期化しておく
	}

	@Override
	protected void onPreExecute() { // ICS読み込み処理に入る前にprogressBarを表示しておく
		super.onPreExecute();
        activityObj.progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        activityObj.progressBar.setMessage(activityObj.resources.getString(R.string.top_async_progress_message));
        activityObj.progressBar.show();
	}

	@Override
	protected Void doInBackground(String... pathList) { // ICSデータ読み込み処理
		this.pathList = pathList;
		activityObj.loadCompleted = false;
		for(String pathStr : pathList) { model.addCalName(pathStr); }
		activityObj.model = model;
		return null;
	}

	@Override
	protected void onPostExecute(Void result) { // 後処理
		super.onPostExecute(result);
		activityObj.progressBar.dismiss();
		activityObj.refreshListView(pathList); // ActivityのListViewを更新させる
		activityObj.loadCompleted = true;      // 読み込み済みフラグを立てる
	}
}
