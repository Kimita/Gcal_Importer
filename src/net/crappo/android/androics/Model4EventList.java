package net.crappo.android.androics;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Map.Entry;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;

public class Model4EventList {
	public Calendar calendar;
	public Locale locale;
	public TimeZone timezone;

	private LinkedMapContainer linkedMapContainer;
    private ComponentList componentBuffer;

	public Model4EventList() { // コンストラクタ
		calendar = null;
		locale = new Locale("ja", "JP");
		timezone = TimeZone.getTimeZone("Asia/Tokyo");
	}

	public void readIcs(String pathStr) { // ICSファイルを解析してデータを保持する(全部ical4jまかせ)
		FileInputStream fin;
		try {
			fin = new FileInputStream(pathStr);
			CalendarBuilder builder = new CalendarBuilder();
			calendar = builder.build(fin);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserException e) {
			e.printStackTrace();
		}
	}

	public PropertyList getPropertyList() { return calendar.getProperties(); }

	public ComponentList getComponentList() { return calendar.getComponents(); }

	/*
	 * カレンダーのイベントデータを日付でソートするメソッド。
	 * 1.VEventを、(key=Uid / value=DtStart) としたMapに格納し、Map.entryのArrayListにしてvalueで降順ソートする。
	 * 2.entryのArrayListからkey/valueを順序良く取り出してLinkedMapにする。
	 * 3.元のcalendarのVEVENTからLinkedMapのkey(uid)でEVENTを取り出していって新しくcalendarを作り、置き換える。
	 */
	public void mapSort() {
		Map<String, String> mapUidDtstart = new HashMap<String, String>();
		if(calendar.getComponents("VEVENT").size() == 0)  return; // VEVENTが無ければ何もしない
		
		ComponentList components = calendar.getComponents();
		Component component;
		VEvent event;
		componentBuffer = new ComponentList();
		List<Map.Entry<String,String>> entries = null;
		for(Object obj : components) {
			if( obj instanceof VEvent)	{ // VEVENTを  uid と dtstart のセットにしてMapにputしていく
				event = (VEvent)obj;
				mapUidDtstart.put(event.getUid().getValue(), event.getProperties().getProperty(Property.DTSTART).getValue());
			} else { // VEVENT以外のComponentはバッファにとっておく
				component = (Component)obj;
				componentBuffer.add(component);
			}
		}
		// Mapにputし終わったら、entrySetとしてArrayListに与えてCollection使ってValue比較で降順sort(日付の新しい順)する
		entries = new ArrayList<Map.Entry<String,String>>(mapUidDtstart.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String,String>>() {
            @Override
            public int compare(Entry<String,String> entry1, Entry<String,String> entry2) {
                return ((String)entry2.getValue()).compareTo((String)entry1.getValue());
            }
        });

		// entryのArrayListからkey/valueを順序良く取り出してLinkedMapにする。
		linkedMapContainer = new LinkedMapContainer();
		for (Entry<String,String> entry : entries) { linkedMapContainer.putLinkedMap(entry.getKey(), entry.getValue()); }
		entries = null; // 要らなくなったデータはさっさと開放する

		Calendar sortedCalendar = new Calendar(); // 入れ物を用意しておく
		for(Object propObj : calendar.getProperties()) { // まずはVCALENDARのProperty部分を入れ物に詰める
			Property calHeader = (Property)propObj;
			sortedCalendar.getProperties().add(calHeader);
		}
		for(Object compObj : componentBuffer) { // バッファにとっておいたVEVENT以外の要素を入れ物に詰める
			component = (Component)compObj;
			sortedCalendar.getComponents().add(component);
		}
		for(Entry<String, String> entry: linkedMapContainer.linkedMap.entrySet() ) { // sortされたLinkedMapを使ってEVENTを日時順に拾っていく
			Component compoTmp = null;
			for(Object eventObj : calendar.getComponents("VEVENT")) {
				event = (VEvent)eventObj;
				if(entry.getKey().equals(event.getUid().getValue())) { // LinkedMapのuidとVEVENTのuidが一致したら、VEventを入れ物に詰める
		            compoTmp = (Component)eventObj;
		            try { sortedCalendar.getComponents().add(compoTmp.copy()); }
		            catch (Exception e) { e.printStackTrace(); }
				}
			}
		}
		calendar = sortedCalendar; // VEventが日時順にソートされたカレンダーで、calendarオブジェクトを置き換える。
	}

	class LinkedMapContainer { // sort用に用意した入れ物クラス
		Map<String, String> linkedMap;
		LinkedMapContainer() {
			linkedMap = new LinkedHashMap<String, String>();
		}
		public Map<String, String> getLinkedMap() {
			return linkedMap;
		}
		public void putLinkedMap(String key, String value) {
			linkedMap.put(key, value);
		}

	}


}
