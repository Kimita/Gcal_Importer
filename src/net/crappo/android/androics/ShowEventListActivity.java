package net.crappo.android.androics;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ShowEventListActivity extends Activity {

    private static final int RL_PADDING_TOP = 5;
    private static final int RL_PADDING_BOTTOM = 10;
    private static final int CLEARED_CHOICE = -1; // localTargetCal_ChosenPositionをクリアした時に設定される定数
    private static final int DEFAULT_CHECKED = 0; // import先となるローカルカレンダーを選択するダイアログでの初期選択値

    private RelativeLayout rl;
    private ListView lv;
    private String icsFileName;      // import元ICSファイルのファイル名(TopActivityからのintentで受け取る)
    private ArrayList<CalendarDto> localCalDtoList = new ArrayList<CalendarDto>(); // 本アプリ用AccountTypeに属するカレンダーのリスト
    private int localTargetCal_ChosenPosition = CLEARED_CHOICE; // ダイアログで選択されたimport先ローカルカレンダーのposition
    private int icsExist = 0;    // ICSファイルが持っているEventデータの個数 (Panel部分に表示される)
    private int bothExist = 0;   // ICSファイルとローカルカレンダーと共通に持っているEventデータの個数(Panel部分に表示される)
    private int icsOnly = 0;     // ICSファイルだけが持っているEventデータの個数(Panel部分に表示される)

    final Locale locale = new Locale("ja", "JP");
    String appName;     // 本アプリ用アカウントのAccountName
    String packageName; // 本アプリ用アカウントのAccountType
    ProgressDialog progressBar;
    ShowEventListActivity activityObj;
    Model4EventList model; // Eventsのデータを保持するモデルクラスのオブジェクト。AsyncTaskで作成する。
    QueryToProvider queryClass; // Providerに対してdeleteやinsertやqueryなどを行うメソッドだけを持つ内部クラスのオブジェクト
    Resources resources;
    String selectedFilePath;  // 呼び出し元ActivityからIntentでもらう情報(ICSファイルのpath)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_event_list);
        
        // 後で使うことになる変数たちを初期化しておく
        activityObj = this;
        selectedFilePath = getIntent().getExtras().getString("SELECTED_FILE_PATH");
        icsFileName = new File(selectedFilePath).getName();
        resources = getResources();
        packageName = resources.getResourcePackageName(R.layout.activity_top);
        appName = resources.getString(R.string.app_name);
        queryClass = new QueryToProvider();

        // [Import先を選択]ボタンにリスナー設定。押されたら、アプリ用カレンダーの一覧を取得して選択ダイアログを表示する。
        Button calSelectDialogBtn = (Button)findViewById(R.id.showeventlist_bt_selectdialog);
        calSelectDialogBtn.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
                queryClass.getCalendars();
                showSingleSelectDialog(R.string.showeventlist_dialog_choice_calendar_to_import);
            }
        });
        
        // [Import開始]ボタンにリスナー設定。
        Button runImportBtn = (Button) findViewById(R.id.showeventlist_bt_run_import);
        runImportBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(icsOnly == 0) { // 見ているICSファイルのイベントデータが全て端末ローカルカレンダーに登録済みの場合、何も処理しない。
                    Toast.makeText(activityObj, R.string.showeventlist_nothing_for_insert, Toast.LENGTH_LONG).show();
                } else if(localTargetCal_ChosenPosition != CLEARED_CHOICE) { // 上記をクリアしてImpor先が選択されている場合
                    queryClass.insertEvents(); // すぐinsert開始
                    Toast.makeText(activityObj, R.string.showeventlist_import_now, Toast.LENGTH_LONG).show();
                } else { // 対象が選択されてなければ、新しくローカルカレンダーを作ってinsertする
                    localTargetCal_ChosenPosition = queryClass.createNewCalendar(
                          model.calendar.getProperties().getProperty("X-WR-CALNAME").getValue()
                        , model.calendar.getProperties().getProperty("X-WR-CALNAME").getValue()
                        , Color.rgb(0, 0, 0) // 色はとりあえず真っ黒を指定
                        , model.timezone.getID());
                    // 上記で作成が完了したカレンダーをすぐ選択状態にしてinsert実行
                    queryClass.insertEvents();
                    Toast.makeText(activityObj, R.string.showeventlist_import_now, Toast.LENGTH_LONG).show();
                }
            }
        });
        
//        Button testBtn = (Button)findViewById(R.id.showeventlist_bt_test);
//        testBtn.setOnClickListener(new OnClickListener(){
//            @Override
//            public void onClick(View v) {
//                queryClass.getEvents();
//                Toast.makeText(activityObj, "Show Logcat.", Toast.LENGTH_LONG).show();
//            }
//        });

        progressBar = new ProgressDialog(this);
        try { // 呼び出し元Activityから受け取ったICSファイルのpathからデータを読み込んで解析・保持するAsyncTaskを実行する。
            new ShowEventListAsync(activityObj).execute(selectedFilePath);
        } catch (Exception e) {
            Toast.makeText(this, "Error Ocurred. (in ShowEventListAsync)", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        model = null; // やらんでもいいかもしれんけど、いちおう開放しておく。
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { // 本アプリ用カレンダーの作成/削除はこのActivityのメニューから行える
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.showeventlist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
      case R.id.showeventlist_menu_create_new_calendar:
          // 本アプリ用カレンダーを作成する(作成処理はqueryClass.createNewCalendar()メソッドで行う)
          localTargetCal_ChosenPosition = queryClass.createNewCalendar(
        		model.calendar.getProperties().getProperty("X-WR-CALNAME").getValue()
              , model.calendar.getProperties().getProperty("X-WR-CALNAME").getValue()
              , Color.rgb(0, 0, 0) // 色はとりあえず真っ黒を指定
              , model.timezone.getID());
          return true;
      case R.id.showeventlist_menu_del_calendar:
          // 作成済みの本アプリ用カレンダーを、リストから選択して削除する(表示/選択はshowSingleSelectDialog()メソッドで行う)
          queryClass.getCalendars();
          showSingleSelectDialog(R.string.showeventlist_menu_del_calendar);
          return true;
      }
      return super.onOptionsItemSelected(item);
    }

    void showProgressBar() { // クルクル回るだけのprogressBarを表示するためのメソッド。
        progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressBar.setMessage(resources.getString(R.string.showeventlist_dialog_spinner));
        progressBar.show();
    }

    /* 
     * onCreateの最後に実行したAsyncTaskが、その処理を終了したら呼び出されるメソッド。
     * ・ICSファイルの概要を表示するヘッダー部分を作る。
     * ・ICSデータと端末ローカルカレンダーとのEvent差異の情報Panelを更新する。
     * ・ListViewのadapterを生成してsetAdapterする。
     */
    void refreshLayout(){
        makeHeader();
        ComponentList components = model.calendar.getComponents("VEVENT");
        queryClass.compareToCalendarProvider();
        lv = (ListView)findViewById(R.id.showeventlist_listView);
        lv.setAdapter(new ViewHolderAdapter(this, 0, components));
        queryClass.getCalendars();
    }

    /* CompareAsyncの終了処理で呼び出される。 Layoutの真ん中部分(Panel)の情報を更新する。 */
    void refreshPanelView(){
        TextView tvIcs = (TextView)findViewById(R.id.showeventlist_tv_exist_ics_num);
        tvIcs.setText("" + icsExist);
        TextView tvBoth = (TextView)findViewById(R.id.showeventlist_tv_exist_local_num);
        tvBoth.setText("" + bothExist);
        if (bothExist > 0)	tvBoth.setTextColor(Color.BLUE);
        else				tvBoth.setTextColor(Color.BLACK);
        TextView tvIcsOnly = (TextView)findViewById(R.id.showeventlist_tv_exist_ics_only_num);
        tvIcsOnly.setText("" + icsOnly);
        if (icsOnly > 0)	tvIcsOnly.setTextColor(Color.RED);
        else				tvIcsOnly.setTextColor(Color.BLACK);
    }

    /*
     * 画面上部をHeader領域として扱い、import元ICSデータのプロパティ情報を表示するメソッド。
     * まどろっこしいやり方してるけど、showSettingだけで表示/表示の切り替えをしたかったが為である。
     */
    private void makeHeader() {
        ArrayList<String> headerTitles = new ArrayList<String>();
        headerTitles.add("Calendar FileName"); // この項目はPropertiesに無い。TopActivityからintentで受け取ったファイル名。
        PropertyList propList = model.getPropertyList();
        for(int i=0; i<propList.size(); i++) { // propertiesの各項目からNameを取得する
            Property p = (Property)propList.get(i);
            headerTitles.add(p.getName());
        }
        boolean[] showSetting = new boolean[] { // 各項目について、表示するかしないか(true/false)をこの配列で決めておく
                  true    // Calendar FileName
                , false    // propList.getProperty("PRODID").getValue()
                , false    // propList.getProperty("VERSION").getValue()
                , false    // propList.getProperty("CALSCALE").getValue()
                , false    // propList.getProperty("METHOD").getValue()
                , true    // propList.getProperty("X-WR-CALNAME").getValue()
                , false    // propList.getProperty("X-WR-TIMEZONE").getValue()
                , true    // propList.getProperty("X-WR-CALDESC").getValue()
                , false    // for something
                , false    // for something
        };
        rl = (RelativeLayout)findViewById(R.id.showeventlist_relativelayout);
        int childCnt = 0; // RelativeLayoutの子要素を順序良く処理していくためのcounter(headerひとつにつき子要素2つがセット)
        for (int i=0; i<headerTitles.size(); i++) { //
            TextView tvTitle = (TextView)rl.getChildAt(childCnt++); // タイトル列
            HorizontalScrollView hs = (HorizontalScrollView)rl.getChildAt(childCnt++);
            TextView tvValue = (TextView)hs.getChildAt(0); // 値の列
            if(showSetting[i]) { // trueのものだけ表示する
                if(i==0) {
                    tvValue.setText(icsFileName); // 本メソッドの最初に定義した"Calendar FileName"に該当する値をセット
                } else { // 最初以外はPropertyから値を持ってきてセット
                    String value;
                    if((value = propList.getProperty(headerTitles.get(i)).getValue()) != null)    tvValue.setText(value);
                }
            } else { // falseのものはタイトル列、値の列ともに非表示にする
                tvTitle.setHeight(0);
                tvTitle.setVisibility(ViewGroup.INVISIBLE);
                hs.setVisibility(ViewGroup.INVISIBLE);
                tvValue.setHeight(0);
            }
        }
    }

    /* ローカルカレンダーの選択状態をクリアするメソッド */
    private void clearChoice() {
        localTargetCal_ChosenPosition = CLEARED_CHOICE;
        localCalDtoList.clear();
    }

    /* API 4.0からContentProviderのURI取得方法が刷新されたので、それに合わせたURIを返すメソッド */
    private Uri getCalProvider() {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return Calendars.CONTENT_URI;
        } else {
            return Uri.parse("content://com.android.calendar/calendars");
        }
    }

    /* API 4.0からContentProviderのURI取得方法が刷新されたので、それに合わせたURIを返すメソッド */
    private Uri getEventProvider() {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return CalendarContract.Events.CONTENT_URI;
        } else {
            return Uri.parse("content://com.android.calendar/events");
        }
    }

    /*
     * ContentProviderへのクエリ実行に使用するUriを生成するのに使うメソッド。
     * API 14以降は(CALLER_IS_SYNCADAPTER,"true")をqueryにappendする必要があるため、場合分けしている。
     */
    private Uri asSyncAdapter(Uri uri, String account, String accountType) {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return uri.buildUpon()
                    .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER,"true")
                    .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
                    .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType).build();
        } else {
            return uri.buildUpon()
                    .appendQueryParameter("_sync_account", account)
                    .appendQueryParameter("_sync_account_type", accountType).build();
        }
     }

    /*
     * ICSデータの持つDTSTART等の日付文字列をParseしてDateTime型のオブジェクトを返すメソッド。
     * これの返すDateTime型オブジェクトからgetTime()でUNIXタイムスタンプを得る目的で使う。
     */
    private DateTime getDateTime(String strDateTime) {
        DateTime dt = null;
        try { //日付のみだった場合は時刻情報も付け足す
            if(strDateTime.length() == 8)    dt = new DateTime(strDateTime + "T000000");
            else                             dt = new DateTime(strDateTime);
        } catch (ParseException e) { e.printStackTrace(); }
        return dt;
    }

    /*
     *  対象カレンダー選択リストをDialogで表示
     */
    private void showSingleSelectDialog(int stringId) {
        if(localCalDtoList.size() == 0) { // 本アプリ用カレンダーが未作成の場合は「まずは作ってくれ」とToast表示する
            Toast.makeText(this, R.string.showeventlist_choicedialog_first, Toast.LENGTH_LONG).show();
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(ShowEventListActivity.this);
            ArrayList<String> calAttrArray = new ArrayList<String>();
            // daialogでリスト表示するテキストの配列を作っておく
            for(CalendarDto calDto : localCalDtoList) {
                calAttrArray.add(calDto.toString());
            }
            String[] localCalStrs = (String[])calAttrArray.toArray(new String[0]);
            
            // 上記で作った配列をdaialogにセットする
            dialog.setSingleChoiceItems(
                  localCalStrs
                , DEFAULT_CHECKED
                , new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            localTargetCal_ChosenPosition = whichButton;
                        }
                    }
                );
            
            // dialogのタイトルを設定
            final String dialogTitle = resources.getString(stringId);
            dialog.setTitle(dialogTitle);
        
            dialog.setPositiveButton( // OKボタンだけ作る(キャンセルボタンは要らない)
                  "OK"
                , new DialogInterface.OnClickListener() { // リスナーを設定する
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // なにも選択されていない状態でOKされたら、デフォルトの値が選択されている状態にする
                        if(localTargetCal_ChosenPosition == CLEARED_CHOICE)
                            localTargetCal_ChosenPosition = DEFAULT_CHECKED;
                        // [Import先を選択]ボタンから来る場合と、Menuの[カレンダー削除]から来る場合とがありうる。タイトル文字のidで判定する。
                        if(dialogTitle.equals(resources.getString(R.string.showeventlist_menu_del_calendar)))
                            queryClass.delCalendar(); // [カレンダー削除]の場合だけ、左記を実行する
                    }
                  }
                );
            dialog.show();
        }
    }

    /* ListViewの子要素になるViewの構成を持つHolder */
    private class ViewHolder {
        ArrayList<TextView> tvArray = new ArrayList<TextView>();
        RelativeLayout rlay;

        ViewHolder(RelativeLayout rlay) { // xmlで作ったlayoutから子要素を取ってきてtvArrayに入れていくだけ
            this.rlay = rlay;
            rlay.setPadding(0, RL_PADDING_TOP, 0, RL_PADDING_BOTTOM);
            for(int i=0; i<rlay.getChildCount(); i++) {
                TextView tv = (TextView)rlay.getChildAt(i);
                tvArray.add(tv);
            }
        }
    }

    /* ViewHolderをListViewにVEventの数だけ差し込む処理をするadapter */
    private class ViewHolderAdapter extends ArrayAdapter<ComponentList> {
        ViewHolder holder;
        VEvent event;
        @SuppressWarnings("unchecked")
        public ViewHolderAdapter (ShowEventListActivity activityObj, int resource, ComponentList objects) {
            super(activityObj, resource, objects);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // 下記のif文はArrayAdapterにおけるconvertView使い回しの常套句のようなものだと思われる。
            if (convertView == null) {
                holder = new ViewHolder((RelativeLayout)getLayoutInflater().inflate(R.layout.layout_for_showeventlist, null));
                convertView = holder.rlay;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            // eventの開始時刻・終了時刻・タイトル・場所の４項目を１セットにして表示する。
            Object Obj = getItem(position);
            event = (VEvent)Obj;
            if(event == null)    for (TextView entry : holder.tvArray) { setDummyForView(entry); }
            else                 for (TextView entry : holder.tvArray) { setTextToView(entry); }

            return convertView;
        }

        void setDummyForView(TextView entry) { // eventがnullだった場合に呼ばれるメソッド。ダミー文字列だけ表示する。
            switch(entry.getId()){
            case R.id.showeventlist_dtstart:
                entry.setText("開始日時");
                break;
            case R.id.showeventlist_dtend:
                entry.setText("終了日時");
                break;
            case R.id.showeventlist_summary:
                entry.setText("イベントタイトル");
                break;
            case R.id.showeventlist_location:
                entry.setText("場所");
                break;
            }
        }

        void setTextToView(TextView entry) { // holderの持つ各Viewにeventの各種値をセットするメソッド
            if( entry != null) {
                String str;
                switch(entry.getId()){
                case R.id.showeventlist_dtstart: // getTime()でUnixTimeStampにするのがミソだと思っている
                    if(event.getStartDate() != null){
                        str = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", locale).format(getDateTime(event.getStartDate().getValue()).getTime());
                        entry.setText(str);
                    } else { entry.setText(""); }
                    break;
                case R.id.showeventlist_dtend: // getTime()でUnixTimeStampにするのがミソだと思っている
                    if(event.getEndDate() != null){
                        str = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", locale).format(getDateTime(event.getEndDate().getValue()).getTime());
                        entry.setText(str);
                    } else { entry.setText(""); }
                    break;
                case R.id.showeventlist_summary: // eventのタイトル
                    if(event.getProperties().getProperty(Property.SUMMARY) != null) {
                            str = event.getProperties().getProperty(Property.SUMMARY).getValue();
                            entry.setText(str);
                    } else { entry.setText(""); }
                    break;
                case R.id.showeventlist_location: // eventの場所
                    if(event.getProperties().getProperty(Property.LOCATION) != null){
                        str = event.getProperties().getProperty(Property.LOCATION).getValue();
                        entry.setText(str);
                    } else { entry.setText(""); }
                    break;
                }
            } else {
                Log.e("setTextToView", "entry is Null!");
            }
        }
    }

    /*
     * メソッドのみを持つクラス。
     * コンテントプロバイダに対して、シーンに応じた各種queryを発行するための各種メソッドを持つ。
     */
    private class QueryToProvider {

        /*
         * 端末ローカルに持っている(本アプリが作成した)カレンダーをCalendarDtoとして取得する。
         * AccountTypeが本アプリのpackageNameと一致するものだけ抽出すればよい。
         * そうして取得したCalendarDtoを localCalDtoList にaddしていく。
         */
        void getCalendars(){
            clearChoice(); // カレンダーリストを取得しなおすので、選択状態等をクリアする。
            String selection = null;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                selection = "( " + Calendars.ACCOUNT_TYPE + " = ? )";
            } else {
                selection = "( " + "_sync_account_type" + " = ? )";
            }
            String[] selectionArgs = new String[] { packageName }; // AccountTypeを決め打ちで指定する
            Cursor c = null;
            c = getContentResolver().query(getCalProvider(), null, selection, selectionArgs, null);
            if ( c != null && c.moveToFirst()) {
                do { // Hitしたカレンダーの情報をcalDtoに格納していく
                    CalendarDto calDto = null;
                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        calDto = new CalendarDto(c.getString(c.getColumnIndex(Calendars.NAME)), c.getString(c.getColumnIndex(Calendars.CALENDAR_COLOR)));
                        calDto.setOwnerAccount(c.getString(c.getColumnIndex(Calendars.OWNER_ACCOUNT)));
                        calDto.setAccount_type(c.getString(c.getColumnIndex(Calendars.ACCOUNT_TYPE)));
                        calDto.setAccount_name(c.getString(c.getColumnIndex(Calendars.ACCOUNT_NAME)));
                        calDto.setCalendar_displayName(c.getString(c.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)));
                    } else {
                        calDto = new CalendarDto(c.getString(c.getColumnIndex("name")), c.getString(c.getColumnIndex("color")));
                        calDto.setOwnerAccount(c.getString(c.getColumnIndex("ownerAccount")));
                        calDto.setAccount_type(c.getString(c.getColumnIndex("_sync_account_type")));
                        calDto.setAccount_name(c.getString(c.getColumnIndex("_sync_account")));
                        calDto.setCalendar_displayName(c.getString(c.getColumnIndex("displayName")));
                    }
                    calDto.set_id(c.getString(c.getColumnIndex(Calendars._ID)));
                    localCalDtoList.add(calDto);
                } while (c.moveToNext());
            }
            c.close();
        }

        /*
         * 本アプリがimport先とするローカルカレンダーを新規で作成するメソッド。
         * Calendars.ACCOUNT_NAME と Calendars.ACCOUNT_TYPE の値は決め打ちで設定している(処理によって変更されてはならない)。
         * 戻り値は、新規作成されたカレンダーのlocalCalDtoListにおける要素番号。
         */
        int createNewCalendar(String calendarName, String displayName, int color, String timezone) {
        	calendarName = calendarName + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", locale).format(new Date());
            ContentResolver contentResolver = getContentResolver();
            ContentValues calVal = new ContentValues();
            String result = null;
            String strId = null;
            String ownerStr = "owner_" +  new SimpleDateFormat("yyyyMMdd_HHmmss", locale).format(new Date()) + "_" + packageName;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                calVal.put(Calendars.ACCOUNT_NAME, appName);
                calVal.put(Calendars.ACCOUNT_TYPE, packageName);
                calVal.put(Calendars.NAME, calendarName);
                calVal.put(Calendars.CALENDAR_DISPLAY_NAME, displayName);
                calVal.put(Calendars.CALENDAR_COLOR, color);
                calVal.put(Calendars.CALENDAR_ACCESS_LEVEL, 700);
                calVal.put(Calendars.SYNC_EVENTS, 1);
                calVal.put(Calendars.CALENDAR_TIME_ZONE, timezone);
                calVal.put(Calendars.OWNER_ACCOUNT, ownerStr);
            } else {
                calVal.put("_sync_account", appName);
                calVal.put("_sync_account_type", packageName);
                calVal.put("name", calendarName);
                calVal.put("displayName", displayName);
                calVal.put("color", color);
                calVal.put("access_level", 700);
                calVal.put("sync_events", 1);
                calVal.put("timezone", timezone);
                calVal.put("ownerAccount", ownerStr);
            }
            // API 14以降はCalendarProviderへの書き込みの際にSyncAdapterを使わなければならない項目がある。
            // そのため、それ用にUriをbuildするメソッド asSyncAdapter()を用意して使っている。
            result = contentResolver.insert(asSyncAdapter(getCalProvider(), appName, packageName), calVal).toString();
            // 作成したカレンダーに割り当てられた_idを strId に保持する
            strId = result.replaceAll(getCalProvider().toString() + "/", "");
            int index = strId.lastIndexOf('?');
            strId = index < 0 ? strId : strId.substring(0, index);

            localCalDtoList.clear(); // localCalDtoList をリフレッシュする
            queryClass.getCalendars();
            String toastStr = resources.getString(R.string.showeventlist_new_calendar_created);
            toastStr += "\n" + localCalDtoList.get(localCalDtoList.size()-1);
            Toast.makeText(activityObj,  toastStr, Toast.LENGTH_LONG).show();
            int ret = localCalDtoList.size() - 1;
            for(int i=0;i<localCalDtoList.size();i++) { // localCalDtoList内のposition(要素番号)を返す為に、_idで検索
                CalendarDto dto = localCalDtoList.get(i);
                if(dto.get_id().equals(strId)) {
                    ret = i;
                }
            }
            return ret;
        }

        /*
         * 本アプリによって作成されたローカルカレンダーを削除するメソッド。
         * 本メソッド使用の際には削除対象をダイアログにて選択済みであること。
         */
        void delCalendar() {
            String delTargetIdStr = localCalDtoList.get(localTargetCal_ChosenPosition).get_id();
            String delTargetDispName = localCalDtoList.get(localTargetCal_ChosenPosition).getCalendar_displayName();
            ContentResolver cr = getContentResolver();
            Uri calUri = ContentUris.withAppendedId(asSyncAdapter(getCalProvider(), appName, packageName), Long.parseLong(delTargetIdStr));
            cr.delete(calUri, null, null);
            clearChoice();
            queryClass.getCalendars();
            queryClass.compareToCalendarProvider();
            String toastStr = resources.getString(R.string.showeventlist_done_calendar_delete);
            toastStr += "\n" + delTargetIdStr + ":" + delTargetDispName;
            Toast.makeText(activityObj, toastStr, Toast.LENGTH_LONG).show();
        }

        /*
         * ICSデータと端末ローカルカレンダーのイベントを比較するメソッド。 比較結果は外部クラスの変数へ反映する。
         * 呼ばれるタイミングは下記の通り。
         *   ・本Activityが起動された時
         *   ・insertEvents()が実行された時
         *   ・delCalendar()が実行された時
         *   実際の仕事は内部クラスCompareAsyncがやる。
         */
        void compareToCalendarProvider() {
            icsExist = 0;
            bothExist = 0;
            icsOnly = 0;
            ArrayList<VEvent> eventList = new ArrayList<VEvent>();
            ComponentList components = model.calendar.getComponents("VEVENT");
            VEvent event;
            for(Object obj : components) { // 一度ArrayListに入れて、後で配列にする(AsyncTaskの引数にするため)
                if( obj instanceof VEvent) {
                    icsExist++; // VEventの個数分だけインクリメント
                    event = (VEvent)obj;
                    eventList.add(event);
                }
            }
            VEvent[] eventArray = eventList.toArray(new VEvent[0]); // 配列にしてAsyncTaskに渡す
            new CompareAsync().execute(eventArray);
        }

        /*
         * ICSデータと端末ローカルカレンダーのイベントを比較して、
         * ICS側でのみ持っているEventを本アプリ用アカウントが持っているローカルカレンダーにinsertするメソッド。
         */
        void insertEvents() {
            icsExist = 0;
            bothExist = 0;
            icsOnly = 0;
            ArrayList<VEvent> eventList = new ArrayList<VEvent>();
            ComponentList components = model.calendar.getComponents("VEVENT");
            VEvent event;
            for(Object obj : components) { // 一度ArrayListに入れて、後で配列にする(AsyncTaskの引数にするため)
                if( obj instanceof VEvent) {
                    icsExist++; // VEventの個数分だけインクリメント
                    event = (VEvent)obj;
                    eventList.add(event);
                }
            }
            VEvent[] eventArray = eventList.toArray(new VEvent[0]); // 配列にしてAsyncTaskに渡す
            new InsertAsync().execute(eventArray);
        }

//        /*
//         * for Debug
//         * EventProviderから個々のイベントを取得してLogに出力するだけの確認メソッド。
//         */
//        public void getEvents() {
//           Cursor cursor = null;
////           cursor = getContentResolver().query(getEventProvider(), null, null, null, null);
//           cursor = getContentResolver().query(getEventProvider(), null, " (ownerAccount like ?) ", new String[]{"owner_%"}, null);
//
//           if(cursor.moveToFirst()) {
//               do {
//                   int position = cursor.getPosition();
//                   for(int i=0;i<cursor.getColumnCount();i++) {
//                       Log.v("getEvents:" + position, "(" + i + ")" + cursor.getColumnName(i) + ":" + cursor.getString(i));
//                   }
//               } while (cursor.moveToNext());
//           }
//           cursor.close();
//       }
        
    }
    
    /* queryClass の compareToCalendarProvider()メソッドに呼ばれて非同期に処理を行うAsyncTaskクラス */
    private class CompareAsync extends AsyncTask<VEvent, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar();
        }
        
        @Override
        protected Void doInBackground(VEvent... eventArray) {
            String selection = null;
            String[] selectionArgs = null;
            Cursor cursor = null;
            for(VEvent event : eventArray){
                String uid = event.getUid().getValue();
                // EventsProviderから、EventのUIDがICS側のuidと一致するデータだけを抽出する為に、SQL文の条件を組み立てる
                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    selectionArgs = new String[] { uid };
                    selection = "(" + CalendarContract.Events.SYNC_DATA1 + " = ?)";
                } else { // API 14未満の場合、URLに含まれているuidと比較する為に必要部分を切り出してlike句で%検索する。
                    selectionArgs = new String[] { "%/" + uid.split("@")[0] };
                    selection = "( _sync_id like ? ) ";
                }
                cursor = getContentResolver().query(getEventProvider(), null, selection, selectionArgs, null);
                if(cursor != null) { // cursorがnullであってはならない。(Uriが間違っている時などにnullとなったような気がする)
                    if(cursor.moveToFirst()) { // SQLの検索に1件だけHitすることを期待しているコード
                        do {
                            bothExist++; // ICSデータと端末ローカルカレンダーの双方にあるEventなので、bothExistをincrementする
                        } while (cursor.moveToNext());
                    }
                    cursor.close();
                } else {
                    Log.e("compareToCalendarProvider", "cursor is null.");
                }
            }
            return null;
        }
        
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            icsOnly = icsExist - bothExist;
            progressBar.dismiss();
            refreshPanelView(); // layoutのpanel部分をリフレッシュ(比較結果を反映)する
        }
    }
    
    /* queryClass の insertEvents()メソッドに呼ばれて非同期に処理を行うAsyncTaskクラス */
    private class InsertAsync extends AsyncTask<VEvent, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar();
        }
        
        @Override
        protected Void doInBackground(VEvent... eventArray) {
            ContentResolver contentResolver = ShowEventListActivity.this.getContentResolver();
            ContentValues values = new ContentValues();

            String selection = null;
            String[] selectionArgs = null;
            Cursor cursor = null;
            for(VEvent event : eventArray){
                String uid = event.getUid().getValue();
                // EventsProviderから、EventのUIDがICS側のuidと一致するデータだけを抽出する為に、SQL文の条件を組み立てる
                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    selectionArgs = new String[] { uid };
                    selection = "(" + CalendarContract.Events.SYNC_DATA1 + " = ?)";
                } else { // API 14未満の場合、URLに含まれているuidと比較する為に必要部分を切り出してlike句で%検索する。
                    selectionArgs = new String[] { "%/" + uid.split("@")[0] };
                    selection = "( _sync_id like ? ) ";
                }
                cursor = getContentResolver().query(getEventProvider(), null, selection, selectionArgs, null);

                if(cursor != null) { // cursorがnullであってはならない。(Uriが間違っている時などにnullとなったような気がする)
                    if(cursor.moveToFirst())    bothExist++;
                    else { // EventのUIDと同じデータがProvider側に無い時だけinsertする。
                        Property prop = null;
                        long startMillis = 0; // DtStartをミリ秒で受け取る変数
                        long endMillis = 0;   // DtEnd  をミリ秒で受け取る変数
                        // DtStart と DtEnd は、ical4jのVEventから取った値を変換しないといけない
                        if( (prop = event.getProperties().getProperty(Property.DTSTART)) != null ) {
                            startMillis = getDateTime(prop.getValue()).getTime();
                            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                values.put(CalendarContract.Events.DTSTART, startMillis);
                            } else {
                                values.put("dtstart", startMillis);
                            }
                        }
                        if( (prop = event.getProperties().getProperty(Property.DTEND)) != null ) {
                            endMillis = getDateTime(prop.getValue()).getTime();
                            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                                values.put(CalendarContract.Events.DTEND, endMillis);
                            } else {
                                values.put("dtend", endMillis);
                            }
                        }

                        // DtStartとDtEnd以外は、ホイホイとputしていく。
                        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            if( (prop = event.getProperties().getProperty(Property.SUMMARY)) != null )        values.put(CalendarContract.Events.TITLE, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.LOCATION)) != null )        values.put(CalendarContract.Events.EVENT_LOCATION, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.DESCRIPTION)) != null )    values.put(CalendarContract.Events.DESCRIPTION, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.DURATION)) != null )        values.put(CalendarContract.Events.DURATION, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.RDATE)) != null )        values.put(CalendarContract.Events.RDATE, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.RRULE)) != null )        values.put(CalendarContract.Events.RRULE, prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.UID)) != null )            values.put(CalendarContract.Events.SYNC_DATA1, prop.getValue());
                            values.put(CalendarContract.Events.EVENT_TIMEZONE, model.timezone.getID());

                            values.put(CalendarContract.Events.CALENDAR_ID, localCalDtoList.get(localTargetCal_ChosenPosition).get_id());
                        } else {
                            if( (prop = event.getProperties().getProperty(Property.SUMMARY)) != null )        values.put("title", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.LOCATION)) != null )        values.put("eventLocation", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.DESCRIPTION)) != null )    values.put("description", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.DURATION)) != null )        values.put("duration", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.RDATE)) != null )        values.put("rdate", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.RRULE)) != null )        values.put("rrule", prop.getValue());
                            if( (prop = event.getProperties().getProperty(Property.UID)) != null )            values.put("_sync_id", "http://dummy.crappo.net/calendar/feeds/account/private/full/" + prop.getValue().split("@")[0]);
                            values.put("eventStatus", 1); // 検証に使った環境では既存データに全て1がセットされたので、真似た。この設定値の根拠はそれだけ。API 14未満はAPI非公開だしどうしようもない。
                            values.put("eventTimezone", model.timezone.getID());

                            values.put("calendar_id", localCalDtoList.get(localTargetCal_ChosenPosition).get_id());
                        }

                        // AccountName と AccountTypeは本アプリ用の値で決め打ち
                        contentResolver.insert(asSyncAdapter(getEventProvider(), appName, packageName), values);
                        values.clear();
                        bothExist++;
                    }
                    cursor.close();
                } else {
                    Log.e("insertEvents", "cursor is null.");
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            icsOnly = icsExist - bothExist;
            progressBar.dismiss();
            refreshPanelView(); // layoutのpanel部分をリフレッシュ(insert結果を反映)する
        }

    }

    class CalendarDto {
        String ownerAccount;
        String _id;
        String name;
        String account_type;
        String account_name;
        String calendar_displayName;
        String calendar_color;
        public CalendarDto(String name, String calendar_color) {
            this.name = name;
            this.calendar_color = calendar_color;
            ownerAccount = "owner_" +  new SimpleDateFormat("yyyyMMdd_HHmmss", locale).format(new Date()) + "_" + packageName;
            account_type = packageName;
            account_name = appName;
        }
        public String getOwnerAccount() {
            return ownerAccount;
        }
        public void setOwnerAccount(String ownerAccount) {
            this.ownerAccount = ownerAccount;
        }
        public String get_id() {
            return _id;
        }
        public void set_id(String _id) {
            this._id = _id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getAccount_type() {
            return account_type;
        }
        public void setAccount_type(String account_type) {
            this.account_type = account_type;
        }
        public String getAccount_name() {
            return account_name;
        }
        public void setAccount_name(String account_name) {
            this.account_name = account_name;
        }
        public String getCalendar_displayName() {
            return calendar_displayName;
        }
        public void setCalendar_displayName(String calendar_displayName) {
            this.calendar_displayName = calendar_displayName;
        }
        public String getCalendar_color() {
            return calendar_color;
        }
        public void setCalendar_color(String calendar_color) {
            this.calendar_color = calendar_color;
        }

        @Override
        public String toString() {
            String str = "ID :" + _id;
            str += "\n(Name:" + name + ")";
            return str;
        }

//        @Override
//        public String toString() {
//            String str = "ownerAcnt:" + ownerAccount;
//            str += "\n_id:" + _id;
//            str += "\nname:" + name;
//            str += "\nacntType:" + account_type;
//            str += "\nacntName:" + account_name;
//            str += "\ndisplayName:" + calendar_displayName;
//            str += "\ncolor:" + calendar_color;
//            return str;
//        }

    }

}
