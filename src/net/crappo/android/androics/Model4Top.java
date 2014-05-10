package net.crappo.android.androics;

import java.io.FileInputStream;
import java.util.ArrayList;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;

public class Model4Top {
    public ArrayList<Property> calNames;

    public Model4Top() {
        calNames = new ArrayList<Property>();
    }

    public void addCalName(String pathStr) {
        Calendar calendar = null;
        try { // TopActivityではカレンダー名しか使わないので、それだけ取る。(他のPropertyやComponentは不要)
            FileInputStream fin = new FileInputStream(pathStr);
            CalendarBuilder builder = new CalendarBuilder();
            calendar = builder.build(fin);
            calNames.add(calendar.getProperties().getProperty("X-WR-CALNAME"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
