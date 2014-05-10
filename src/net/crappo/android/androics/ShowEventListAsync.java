package net.crappo.android.androics;

import java.util.TimeZone;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import android.os.AsyncTask;

public class ShowEventListAsync extends AsyncTask<String, Void, Void>{
    Model4EventList model;
    String[] pathList;
    ShowEventListActivity activityObj;
    public ShowEventListAsync(ShowEventListActivity actObj) {
        this.activityObj = actObj;
        model = new Model4EventList();
    }

    @Override
    protected void onPreExecute() { // 処理開始前にprogressBarを表示する
        super.onPreExecute();
        activityObj.showProgressBar();
    }

    @Override
    protected Void doInBackground(String... pathList) {
        this.pathList = pathList;
        for(String pathStr : pathList) { model.readIcs(pathStr); } // Modelクラス側でICSデータを解析・保持する
        model.mapSort(); // 保持したEventデータをDtStartでソートする
        // ICSデータに記述されているTimeZoneを取得
        if(model.calendar.getComponent("VTIMEZONE") != null) { // TimeZoneがVTIMEZONEが定義されている場合
            Object obj = model.calendar.getComponents("VTIMEZONE").get(0);
            if(obj instanceof Component) {
                model.timezone = TimeZone.getTimeZone(((Component)obj).getProperty(Property.TZID).getValue());
            }
        } else { // TimeZoneがVCALENDARのPropertyの一つ(?)として定義されている場合
            for(Object tmpObj: model.calendar.getProperties()) {
                Object obj = ((Property)tmpObj).getValue();
                if(obj instanceof TimeZone) {
                    model.timezone = (TimeZone)obj;
                } else if ( ((Property)tmpObj).getName().equals("X-WR-TIMEZONE") ){
                    model.timezone = TimeZone.getTimeZone(((Property)tmpObj).getValue());
                }
            }
        }
        activityObj.model = model;
        return null;
    }

    @Override
    protected void onPostExecute(Void result) { // 処理終了時にprogressBarを止めてListViewをリフレッシュする
        super.onPostExecute(result);
        activityObj.progressBar.dismiss();
        activityObj.refreshLayout();
    }
}
