package top.soyask.calendarii.ui.fragment.month;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import top.soyask.calendarii.R;
import top.soyask.calendarii.database.dao.EventDao;
import top.soyask.calendarii.domain.Birthday;
import top.soyask.calendarii.domain.Day;
import top.soyask.calendarii.domain.Event;
import top.soyask.calendarii.global.GlobalData;
import top.soyask.calendarii.ui.adapter.month.MonthAdapter;
import top.soyask.calendarii.ui.fragment.main.MainFragment;
import top.soyask.calendarii.ui.fragment.setting.SettingFragment;
import top.soyask.calendarii.utils.DayUtils;
import top.soyask.calendarii.utils.LunarUtils;
import top.soyask.calendarii.utils.MonthUtils;

import static top.soyask.calendarii.global.Global.MONTH_COUNT;
import static top.soyask.calendarii.global.Global.YEAR_START_REAL;

public class MonthFragment extends Fragment implements MonthAdapter.OnItemClickListener {


    private static final String CALENDAR = "calendar";
    private static final String POSITION = "position";
    private static final String TAG = "MonthFragment";
    private static final String DATE_SP = "-";

    private View mContentView;
    private List<Day> mDays = new ArrayList<>();
    private MonthAdapter mMonthAdapter;
    private Calendar mCalendar;
    private int mYear;
    private int mMonth;
    private OnDaySelectListener mOnDaySelectListener;
    private EventDao mEventDao;
    private MonthReceiver mMonthReceiver;

    public MonthFragment() {

    }

    public static MonthFragment newInstance(Calendar calendar, int position) {
        MonthFragment fragment = new MonthFragment();
        Bundle args = new Bundle();
        args.putSerializable(CALENDAR, calendar);
        args.putInt(POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    private synchronized void setupData() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, mYear);
        calendar.set(Calendar.MONTH, mMonth - 1);
        int dayCount = DayUtils.getMonthDayCount(mMonth - 1, mYear);

        mDays.clear();
        for (int i = 0; i < dayCount; i++) {
            int dayOfMonth = i + 1;
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            boolean isToday = isToday(dayOfMonth);

            String lunar = MonthUtils.getLunar(calendar);
            String lunarDay = LunarUtils.getLunar(calendar);
            List<Birthday> birthday0 = GlobalData.BIRTHDAY.get(lunarDay);
            List<Birthday> birthday1 = GlobalData.BIRTHDAY.get(mMonth + "月" + dayOfMonth + "日");
            Day day = new Day(mYear, mMonth, lunar, isToday, dayOfMonth, dayOfWeek);
            day.addBirthday(birthday0);
            day.addBirthday(birthday1);
            String str = new StringBuffer()
                    .append(mYear)
                    .append(DATE_SP)
                    .append(mMonth)
                    .append(DATE_SP)
                    .append(dayOfMonth)
                    .toString();
            day.setHoliday(GlobalData.HOLIDAY.contains(str));
            try {
                List<Event> events = mEventDao.query(day.getYear() + "年" + day.getMonth() + "月" + day.getDayOfMonth() + "日");
                day.setEvents(events);
            } catch (Exception e) {
                e.printStackTrace();
                day.setEvents(new ArrayList<Event>());
            }
            mDays.add(day);
        }
    }

    private boolean isToday(int i) {
        return mCalendar.get(Calendar.DAY_OF_MONTH) == i
                && mCalendar.get(Calendar.YEAR) == mYear
                && mCalendar.get(Calendar.MONTH) + 1 == mMonth;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initArguments();
        }
        mEventDao = EventDao.getInstance(getActivity());
    }

    private void initArguments() {
        int position = getArguments().getInt(POSITION);
        mYear = position / MONTH_COUNT + YEAR_START_REAL; //position==0时 1910/1
        mMonth = position % MONTH_COUNT + 1;
        mCalendar = (Calendar) getArguments().getSerializable(CALENDAR);
    }

    private void setupReceiver() {
        mMonthReceiver = new MonthReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(EventDao.ADD);
        filter.addAction(EventDao.UPDATE);
        filter.addAction(EventDao.DELETE);
        filter.addAction(MainFragment.SKIP);
        filter.addAction(SettingFragment.WEEK_SETTING);
        getActivity().registerReceiver(mMonthReceiver, filter);
        Log.d(TAG, "onCreate and registerReceiver");
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupReceiver();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView and unregisterReceiver");
        getActivity().unregisterReceiver(mMonthReceiver);
    }

    public class MonthReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (mMonthAdapter != null) {
                switch (intent.getAction()) {
                    case MainFragment.SKIP:
                        int year = intent.getIntExtra("year", 0);
                        int month = intent.getIntExtra("month", 0);
                        int day = intent.getIntExtra("day", 0);
                        if (year == mYear && month == mMonth) {
                            mMonthAdapter.setSelectedDay(day);
                        }
                        break;
                    case SettingFragment.WEEK_SETTING:
                        mMonthAdapter.updateStartDate();
                    case EventDao.UPDATE:
                    case EventDao.ADD:
                    case EventDao.DELETE:
                        setupData();
                        mMonthAdapter.notifyDataSetChanged();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.fragment_month, container, false);
        return mContentView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mEventDao = EventDao.getInstance(getActivity());
        setupData();
        setupUI();
    }


    private void setupUI() {
        initDateView();
    }


    private void initDateView() {
        mMonthAdapter = new MonthAdapter(mDays, this);

        RecyclerView recyclerView = find(R.id.rv_date);
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 7));
        recyclerView.setAdapter(mMonthAdapter);
        recyclerView.setItemAnimator(null);
    }


    <T extends View> T find(@IdRes int id) {
        return (T) mContentView.findViewById(id);
    }

    @Override
    public void onDayClick(int position, Day day) {
        if (mOnDaySelectListener != null) {
            mOnDaySelectListener.onSelected(day);
        }
    }

    public void setOnDaySelectListener(OnDaySelectListener onDaySelectListener) {
        this.mOnDaySelectListener = onDaySelectListener;
    }

    public interface OnDaySelectListener {
        void onSelected(Day day);
    }
}