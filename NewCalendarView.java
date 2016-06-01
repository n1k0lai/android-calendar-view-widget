package com.nplusnapps.latr;

import android.app.Service;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * This class is a calendar widget based on <code>android.widget.CalendarView</code> version 4.0.1_r1.
 * The range of dates supported by this calendar is configurable. A user can select a date
 * by taping on it and can scroll and fling the calendar to a desired date.
 */
public class NewCalendarView extends FrameLayout {

    private static final long MILLIS_IN_DAY = 86400000L;
    private static final int DAYS_PER_WEEK = 7;
    private static final long MILLIS_IN_WEEK = DAYS_PER_WEEK * MILLIS_IN_DAY;

    private static final int SCROLL_HYST_WEEKS = 2;
    private static final int GOTO_SCROLL_DURATION = 1000;
    private static final int ADJUSTMENT_SCROLL_DURATION = 500;
    private static final int SCROLL_CHANGE_DELAY = 40;
    private static final float LIST_FRICTION = .05f;
    private static final float LIST_VELOCITY_SCALE = 0.333f;

    private static final String DATE_FORMAT = "MM/dd/yyyy";
    private static final String DATE_FORMAT_LOCALIZED = "EEEE d MMMM yyyy";
    private static final String MONTH_FORMAT_LOCALIZED = "LLLL yyyy";
    private static final String DAY_FORMAT_LOCALIZED = "EEEE d MMMM";

    private static final String DEFAULT_MIN_DATE = "01/01/1970";
    private static final String DEFAULT_MAX_DATE = "01/01/2100";
    private static final int DEFAULT_SHOWN_WEEK_COUNT = 6;
    private static final float DEFAULT_DATE_TEXT_SIZE = 14;
    private static final int DEFAULT_WEEK_DAY_TEXT_APPEARANCE_RES_ID = -1;
    private static final boolean DEFAULT_SHOW_WEEK_NUMBER = true;

    private static final int UNSCALED_SELECTED_DATE_ARROW_WIDTH = 2;
    private static final int UNSCALED_WEEK_MIN_VISIBLE_HEIGHT = 12;
    private static final int UNSCALED_LIST_SCROLL_TOP_OFFSET = 2;
    private static final int UNSCALED_BOTTOM_BUFFER = 20;
    private static final int UNSCALED_WEEK_SEPARATOR_LINE_WIDTH = 1;

    private final int mWeekSeparatorLineWidth;
    private final int mDateTextSize;
    private final int mWeekDayTextAppearance;
    private final int mSelectedDateArrowWidth;
    private final int mSelectedDateArrowsColor;
    private final int mSelectedWeekBackgroundColor;
    private final int mFocusedMonthDateColor;
    private final int mUnfocusedMonthDateColor;
    private final int mWeekSeparatorLineColor;
    private final int mWeekNumberColor;
    private final int mSelectedWeekNumberColor;
    private final int mWeekdayColor;
    private final int mWeekendColor;

    private int mListScrollTopOffset;
    private int mWeekMinVisibleHeight;
    private int mBottomBuffer;
    private int mShownWeekCount;
    private boolean mShowWeekNumber;
    private int mFirstDayOfWeek;
    private int mCurrentMonthDisplayed;
    private long mPreviousScrollPosition;
    private boolean mIsScrollingUp = false;
    private int mPreviousScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private int mCurrentScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    private ScrollStateRunnable mScrollStateChangedRunnable = new ScrollStateRunnable();
    private OnDateChangeListener mOnDateChangeListener;
    private WeeksAdapter mAdapter;
    private ListView mListView;
    private TextView mMonthName;
    private ViewGroup mDayNamesHeader;

    private Calendar mTempDate;
    private Calendar mFirstDayOfMonth;
    private Calendar mMinDate;
    private Calendar mMaxDate;

    private final SimpleDateFormat mDateFormat = new SimpleDateFormat(DATE_FORMAT);
    private final SimpleDateFormat mDateFormatLocalized;
    private final SimpleDateFormat mMonthFormatLocalized;
    private final SimpleDateFormat mDayFormatLocalized;
    private final String[] mDaysOfWeekLocalized;

    private Parcelable mListViewState;
    private Locale mCurrentLocale;
    private Context mContext;

    /**
     * The callback used to indicate the user changes the date.
     */
    public interface OnDateChangeListener {

        /**
         * Called upon change of the selected day.
         *
         * @param view The view associated with this listener.
         * @param year The year that was set.
         * @param month The month that was set [0-11].
         * @param dayOfMonth The day of the month that was set.
         */
        void onSelectedDayChange(NewCalendarView view, int year, int month, int dayOfMonth);
    }

    public NewCalendarView(Context context) {
        this(context, null);
    }

    public NewCalendarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NewCalendarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, 0);

        mContext = context;

        mCurrentLocale = context.getResources().getConfiguration().locale;

        mTempDate = getLocalizedCalendar(mTempDate);
        mFirstDayOfMonth = getLocalizedCalendar(mFirstDayOfMonth);
        mMinDate = getLocalizedCalendar(mMinDate);
        mMaxDate = getLocalizedCalendar(mMaxDate);

        mDateFormatLocalized = new SimpleDateFormat(DATE_FORMAT_LOCALIZED, mCurrentLocale);
        mMonthFormatLocalized = new SimpleDateFormat(MONTH_FORMAT_LOCALIZED, mCurrentLocale);
        mDayFormatLocalized = new SimpleDateFormat(DAY_FORMAT_LOCALIZED, mCurrentLocale);
        mDaysOfWeekLocalized = DateFormatSymbols.getInstance(mCurrentLocale).getShortWeekdays();

        TypedArray attributesArray = context.obtainStyledAttributes(
                attrs, R.styleable.CalendarView, R.attr.calendarViewStyle, 0);
        mShowWeekNumber = attributesArray.getBoolean(
                R.styleable.CalendarView_showWeekNumber, DEFAULT_SHOW_WEEK_NUMBER);
        mFirstDayOfWeek = attributesArray.getInt(
                R.styleable.CalendarView_firstDayOfWeek, Calendar.getInstance().getFirstDayOfWeek());
        mShownWeekCount = attributesArray.getInt(
                R.styleable.CalendarView_shownWeekCount, DEFAULT_SHOWN_WEEK_COUNT);

        String minDate = attributesArray.getString(R.styleable.CalendarView_minDate);
        if (TextUtils.isEmpty(minDate) || !parseDate(minDate, mMinDate)) {
            parseDate(DEFAULT_MIN_DATE, mMinDate);
        }
        String maxDate = attributesArray.getString(R.styleable.CalendarView_maxDate);
        if (TextUtils.isEmpty(maxDate) || !parseDate(maxDate, mMaxDate)) {
            parseDate(DEFAULT_MAX_DATE, mMaxDate);
        }
        if (mMaxDate.before(mMinDate)) {
            throw new IllegalArgumentException("Max date cannot be before min date.");
        }
        boolean setDate = attributesArray.getBoolean(R.styleable.CalendarView_setInitialDate, true);

        mSelectedWeekBackgroundColor = attributesArray.getColor(
                R.styleable.CalendarView_selectedWeekBackgroundColor, 0);
        mFocusedMonthDateColor = attributesArray.getColor(
                R.styleable.CalendarView_focusedMonthDateColor, 0);
        mUnfocusedMonthDateColor = attributesArray.getColor(
                R.styleable.CalendarView_unfocusedMonthDateColor, 0);
        mWeekSeparatorLineColor = attributesArray.getColor(
                R.styleable.CalendarView_weekSeparatorLineColor, 0);
        mWeekNumberColor = attributesArray.getColor(
                R.styleable.CalendarView_weekNumberColor, 0);
        mSelectedWeekNumberColor = attributesArray.getColor(
                R.styleable.CalendarView_selectedWeekNumberColor, 0);
        mSelectedDateArrowsColor = attributesArray.getColor(
                R.styleable.CalendarView_selectedDateArrowsColor, 0);
        mWeekdayColor =  attributesArray.getColor(
                R.styleable.CalendarView_weekdayColor, 0);
        mWeekendColor = attributesArray.getColor(
                R.styleable.CalendarView_weekendColor, 0);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        int dateTextAppearanceResId = attributesArray.getResourceId(
                R.styleable.CalendarView_dateTextAppearance, android.R.style.TextAppearance_Small);
        TypedArray dateTextAppearance = context.obtainStyledAttributes(dateTextAppearanceResId,
                R.styleable.DateTextAppearance);
        mDateTextSize = dateTextAppearance.getDimensionPixelSize(
                R.styleable.DateTextAppearance_textSize, (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, DEFAULT_DATE_TEXT_SIZE, displayMetrics));
        dateTextAppearance.recycle();

        mWeekDayTextAppearance = attributesArray.getResourceId(
                R.styleable.CalendarView_weekDayTextAppearance, DEFAULT_WEEK_DAY_TEXT_APPEARANCE_RES_ID);
        attributesArray.recycle();

        mWeekMinVisibleHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_WEEK_MIN_VISIBLE_HEIGHT, displayMetrics);
        mListScrollTopOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_LIST_SCROLL_TOP_OFFSET, displayMetrics);
        mBottomBuffer = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_BOTTOM_BUFFER, displayMetrics);
        mSelectedDateArrowWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_SELECTED_DATE_ARROW_WIDTH, displayMetrics);
        mWeekSeparatorLineWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_WEEK_SEPARATOR_LINE_WIDTH, displayMetrics);

        LayoutInflater inflater = (LayoutInflater) context.
                getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.calendar_view, this, true);

        mListView = (ListView) findViewById(android.R.id.list);
        mDayNamesHeader = (ViewGroup) findViewById(R.id.day_names);
        mMonthName = (TextView) findViewById(R.id.month_name);

        setUpHeader();
        setUpListView();
        setUpAdapter();

        if (setDate) {
            setDate(mTempDate.getTimeInMillis());
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        if (mListView.getLastVisiblePosition() != -1) {
            mListViewState = mListView.onSaveInstanceState();
        }

        return new SavedState(super.onSaveInstanceState(), mListViewState, getDate(), getFocusedDate());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());

        mListViewState = savedState.getListState();
        if (mListViewState != null) {
            mListView.onRestoreInstanceState(mListViewState);
            setCurrentDate(savedState.getSelectedDate(), savedState.getFocusedDate());
        } else {
            setDate(savedState.getSelectedDate());
        }
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return false;
            default:
                return super.onRequestSendAccessibilityEvent(child, event);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        mListView.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return mListView.isEnabled();
    }

    /**
     * Gets the minimal date supported by this {@link CalendarView} in milliseconds
     * since January 1, 1970 00:00:00 in {@link TimeZone#getDefault()} time
     * zone.
     *
     * @return The minimal supported date.
     */
    public long getMinDate() {
        return mMinDate.getTimeInMillis();
    }

    /**
     * Sets the minimal date supported by this {@link CalendarView} in milliseconds
     * since January 1, 1970 00:00:00 in {@link TimeZone#getDefault()} time
     * zone.
     *
     * @param minDate The minimal supported date.
     */
    public void setMinDate(long minDate) {
        mTempDate.setTimeInMillis(minDate);
        if (isSameDate(mTempDate, mMinDate)) {
            return;
        }
        mMinDate.setTimeInMillis(minDate);

        mAdapter.init();

        if (mAdapter.mSelectedDate.before(mMinDate)) {
            goTo(mMinDate, false, true, false);
        }
    }

    /**
     * Gets the maximal date supported by this {@link CalendarView} in milliseconds
     * since January 1, 1970 00:00:00 in {@link TimeZone#getDefault()} time
     * zone.
     *
     * @return The maximal supported date.
     */
    public long getMaxDate() {
        return mMaxDate.getTimeInMillis();
    }

    /**
     * Sets the maximal date supported by this {@link CalendarView} in milliseconds
     * since January 1, 1970 00:00:00 in {@link TimeZone#getDefault()} time
     * zone.
     *
     * @param maxDate The maximal supported date.
     */
    public void setMaxDate(long maxDate) {
        mTempDate.setTimeInMillis(maxDate);
        if (isSameDate(mTempDate, mMaxDate)) {
            return;
        }
        mMaxDate.setTimeInMillis(maxDate);

        mAdapter.init();

        if (mAdapter.mSelectedDate.after(mMaxDate)) {
            goTo(mMaxDate, false, true, false);
        }
    }

    /**
     * Sets whether to show the week number.
     *
     * @param showWeekNumber True to show the week number.
     */
    public void setShowWeekNumber(boolean showWeekNumber) {
        if (mShowWeekNumber == showWeekNumber) {
            return;
        }
        mShowWeekNumber = showWeekNumber;
        mAdapter.notifyDataSetChanged();
        setUpHeader();
    }

    /**
     * Gets whether to show the week number.
     *
     * @return True if showing the week number.
     */
    public boolean getShowWeekNumber() {
        return mShowWeekNumber;
    }

    /**
     * Gets the first day of week.
     *
     * @return The first day of the week conforming to the {@link CalendarView} APIs.
     *
     * @see Calendar#MONDAY
     * @see Calendar#TUESDAY
     * @see Calendar#WEDNESDAY
     * @see Calendar#THURSDAY
     * @see Calendar#FRIDAY
     * @see Calendar#SATURDAY
     * @see Calendar#SUNDAY
     */
    public int getFirstDayOfWeek() {
        return mFirstDayOfWeek;
    }

    /**
     * Sets the first day of week.
     *
     * @param firstDayOfWeek The first day of the week conforming to the {@link CalendarView} APIs.
     *
     * @see Calendar#MONDAY
     * @see Calendar#TUESDAY
     * @see Calendar#WEDNESDAY
     * @see Calendar#THURSDAY
     * @see Calendar#FRIDAY
     * @see Calendar#SATURDAY
     * @see Calendar#SUNDAY
     */
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (mFirstDayOfWeek == firstDayOfWeek) {
            return;
        }
        mFirstDayOfWeek = firstDayOfWeek;
        mAdapter.init();
        mAdapter.notifyDataSetChanged();
        setUpHeader();
    }

    /**
     * Sets the listener to be notified upon selected date change.
     *
     * @param listener The listener to be notified.
     */
    public void setOnDateChangeListener(OnDateChangeListener listener) {
        mOnDateChangeListener = listener;
    }

    /**
     * Gets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @return The selected date.
     */
    public long getDate() {
        return mAdapter.mSelectedDate.getTimeInMillis();
    }

    /**
     * Gets the focused month in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @return The focused month.
     */
    public long getFocusedDate() {
        return mFirstDayOfMonth.getTimeInMillis();
    }

    /**
     * Sets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @param date The selected date.
     *
     * @throws IllegalArgumentException if the provided date
     * is before the range start of after the range end.
     *
     * @see #setDate(long, boolean, boolean)
     * @see #setMinDate(long)
     * @see #setMaxDate(long)
     */
    public void setDate(long date) {
        setDate(date, false, false);
    }

    /**
     * Sets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link TimeZone#getDefault()} time zone.
     *
     * @param date The date.
     * @param animate Whether to animate the scroll to the current date.
     * @param center Whether to center the current date even if it is already visible.
     *
     * @throws IllegalArgumentException if the provided date
     * is before the range start of after the range end.
     *
     * @see #setMinDate(long)
     * @see #setMaxDate(long)
     */
    public void setDate(long date, boolean animate, boolean center) {
        mTempDate.setTimeInMillis(date);
        if (mTempDate.before(mMinDate)) {
            goTo(mMinDate, animate, true, center);
        } else if (mTempDate.after(mMaxDate)) {
            goTo(mMaxDate, animate, true, center);
        } else {
            goTo(mTempDate, animate, true, center);
        }
    }

    /**
     * Gets the current time zone.
     *
     * @return TimeZone The current time zone.
     */
    public TimeZone getTimeZone() {
        return mTempDate.getTimeZone();
    }

    /**
     * Sets the new time zone.
     *
     * @param zone The new time zone.
     */
    public void setTimeZone(TimeZone zone) {
        Calendar currentMonth = (Calendar)
                mFirstDayOfMonth.clone();

        mAdapter.mSelectedDate.setTimeZone(zone);
        mFirstDayOfMonth.setTimeZone(zone);
        mTempDate.setTimeZone(zone);
        mMaxDate.setTimeZone(zone);
        mMinDate.setTimeZone(zone);

        if (!isSameDate(currentMonth, mFirstDayOfMonth)) {
            mFirstDayOfMonth.set(Calendar.YEAR,
                    currentMonth.get(Calendar.YEAR));
            mFirstDayOfMonth.set(Calendar.MONTH,
                    currentMonth.get(Calendar.MONTH));
            mFirstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        }

        mDateFormat.setTimeZone(zone);
        mDateFormatLocalized.setTimeZone(zone);
        mMonthFormatLocalized.setTimeZone(zone);
        mDayFormatLocalized.setTimeZone(zone);

        parseDate(DEFAULT_MIN_DATE, mMinDate);
        parseDate(DEFAULT_MAX_DATE, mMaxDate);

        mAdapter.init();
        mAdapter.notifyDataSetChanged();

        setCurrentDate(getDate(), getFocusedDate());
    }

    private void setCurrentDate(long selectedDate, long focusedMonth) {
        Calendar currentDate = (Calendar) mTempDate.clone();
        currentDate.clear();
        currentDate.setTimeInMillis(selectedDate);
        if (currentDate.before(mMinDate)) {
            mAdapter.setSelectedDay(mMinDate, false);
            setMonthDisplayed(mMinDate, false);
        } else if (currentDate.after(mMaxDate)) {
            mAdapter.setSelectedDay(mMaxDate, false);
            setMonthDisplayed(mMaxDate, false);
        } else {
            mAdapter.setSelectedDay(currentDate, false);
            currentDate.setTimeInMillis(focusedMonth);
            setMonthDisplayed(currentDate, false);
        }
    }

    private Calendar getLocalizedCalendar(Calendar oldCalendar) {
        if (oldCalendar == null) {
            return Calendar.getInstance(mCurrentLocale);
        } else {
            final long currentTimeMillis = oldCalendar.getTimeInMillis();
            Calendar newCalendar = Calendar.getInstance(mCurrentLocale);
            newCalendar.setTimeInMillis(currentTimeMillis);
            return newCalendar;
        }
    }

    private boolean isSameDate(Calendar firstDate, Calendar secondDate) {
        return (firstDate.get(Calendar.DAY_OF_YEAR) == secondDate.get(Calendar.DAY_OF_YEAR)
                && firstDate.get(Calendar.YEAR) == secondDate.get(Calendar.YEAR));
    }

    private void setUpAdapter() {
        if (mAdapter == null) {
            mAdapter = new WeeksAdapter(mContext);
            mAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    if (mOnDateChangeListener != null) {
                        Calendar selectedDay = mAdapter.getSelectedDay();
                        mOnDateChangeListener.onSelectedDayChange(NewCalendarView.this,
                                selectedDay.get(Calendar.YEAR),
                                selectedDay.get(Calendar.MONTH),
                                selectedDay.get(Calendar.DAY_OF_MONTH));
                    }
                }
            });
            mListView.setAdapter(mAdapter);
        }

        mAdapter.notifyDataSetChanged();
    }

    private void setUpHeader() {
        int[] mDayColors = new int[DAYS_PER_WEEK];
        String[] mDayLabels = new String[DAYS_PER_WEEK];
        for (int i = mFirstDayOfWeek, count = mFirstDayOfWeek + DAYS_PER_WEEK; i < count; i++) {
            int calendarDay = (i > Calendar.SATURDAY) ? i - Calendar.SATURDAY : i;
            mDayLabels[i - mFirstDayOfWeek] = mDaysOfWeekLocalized[calendarDay].substring(0, 1).toUpperCase();
            mDayColors[i - mFirstDayOfWeek] = calendarDay == Calendar.SUNDAY ||
                    calendarDay == Calendar.SATURDAY ? mWeekendColor : mWeekdayColor;
        }

        TextView label = (TextView) mDayNamesHeader.getChildAt(0);
        if (mShowWeekNumber) {
            label.setVisibility(View.VISIBLE);
        } else {
            label.setVisibility(View.GONE);
        }
        for (int i = 1, count = mDayNamesHeader.getChildCount(); i < count; i++) {
            label = (TextView) mDayNamesHeader.getChildAt(i);
            if (mWeekDayTextAppearance > -1) {
                label.setTextAppearance(mContext, mWeekDayTextAppearance);
            }
            if (i < DAYS_PER_WEEK + 1) {
                label.setText(mDayLabels[i - 1]);
                label.setTextColor(mDayColors[i - 1]);
                label.setVisibility(View.VISIBLE);
            } else {
                label.setVisibility(View.GONE);
            }
        }
        mDayNamesHeader.invalidate();
    }

    private void setUpListView() {
        mListView.setItemsCanFocus(true);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setFriction(LIST_FRICTION);
        mListView.setVelocityScale(LIST_VELOCITY_SCALE);
        mListView.setOnScrollListener(new OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                NewCalendarView.this.onScrollStateChanged(view, scrollState);
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                NewCalendarView.this.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
            }
        });
    }

    private void goTo(Calendar date, boolean animate, boolean setSelected, boolean forceScroll) {
        if (date.before(mMinDate) || date.after(mMaxDate)) {
            throw new IllegalArgumentException("Time not between " + mMinDate.getTime()
                    + " and " + mMaxDate.getTime());
        }

        int firstFullyVisiblePosition = mListView.getFirstVisiblePosition();
        View firstChild = mListView.getChildAt(0);
        if (firstChild != null && firstChild.getTop() < 0) {
            firstFullyVisiblePosition++;
        }
        int lastFullyVisiblePosition = firstFullyVisiblePosition + mShownWeekCount - 1;
        if (firstChild != null && firstChild.getTop() > mBottomBuffer) {
            lastFullyVisiblePosition--;
        }

        if (setSelected) {
            mAdapter.setSelectedDay(date, false);
        }

        int position = getWeeksSinceMinDate(date);
        if (position < firstFullyVisiblePosition || position > lastFullyVisiblePosition
                || forceScroll) {
            mFirstDayOfMonth.setTimeInMillis(date.getTimeInMillis());
            mFirstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);

            setMonthDisplayed(mFirstDayOfMonth, false);

            position = getWeeksSinceMinDate(mFirstDayOfMonth);

            mPreviousScrollState = OnScrollListener.SCROLL_STATE_FLING;
            if (animate) {
                mListView.smoothScrollToPositionFromTop(position, mListScrollTopOffset, GOTO_SCROLL_DURATION);
            } else {
                mListView.setSelectionFromTop(position, mListScrollTopOffset);
                onScrollStateChanged(mListView, OnScrollListener.SCROLL_STATE_IDLE);
            }
        } else if (setSelected) {
            setMonthDisplayed(date, false);
        }
    }

    private boolean parseDate(String date, Calendar outDate) {
        try {
            outDate.setTime(mDateFormat.parse(date));
            return true;
        } catch (ParseException e) {
            Log.w(getClass().getSimpleName(), "Date: " + date + " not in format: " + DATE_FORMAT);
            return false;
        }
    }

    private void onScrollStateChanged(AbsListView view, int scrollState) {
        mScrollStateChangedRunnable.doScrollStateChange(view, scrollState);
    }

    private void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        WeekView child = (WeekView) view.getChildAt(0);
        if (child == null) {
            return;
        }

        long currScroll = firstVisibleItem *
                child.getHeight() - child.getBottom();
        if (currScroll < mPreviousScrollPosition) {
            mIsScrollingUp = true;
        } else if (currScroll > mPreviousScrollPosition) {
            mIsScrollingUp = false;
        } else {
            return;
        }

        int offset = child.getBottom() < mWeekMinVisibleHeight ? 1 : 0;
        if (mIsScrollingUp) {
            child = (WeekView) view.getChildAt(SCROLL_HYST_WEEKS + offset);
        } else if (offset != 0) {
            child = (WeekView) view.getChildAt(offset);
        }

        int month;
        if (mIsScrollingUp) {
            month = child.getMonthOfFirstWeekDay();
        } else {
            month = child.getMonthOfLastWeekDay();
        }

        int monthDiff;
        if (mCurrentMonthDisplayed == 11 && month == 0) {
            monthDiff = 1;
        } else if (mCurrentMonthDisplayed == 0 && month == 11) {
            monthDiff = -1;
        } else {
            monthDiff = month - mCurrentMonthDisplayed;
        }

        if ((!mIsScrollingUp && monthDiff > 0) || (mIsScrollingUp && monthDiff < 0)) {
            Calendar firstDay = child.getFirstDay();
            if (mIsScrollingUp) {
                firstDay.add(Calendar.DAY_OF_MONTH, -DAYS_PER_WEEK);
            } else {
                firstDay.add(Calendar.DAY_OF_MONTH, DAYS_PER_WEEK);
            }
            setMonthDisplayed(firstDay, true);
        }
        mPreviousScrollPosition = currScroll;
        mPreviousScrollState = mCurrentScrollState;
    }

    private void setMonthDisplayed(Calendar calendar, boolean send) {
        mMonthName.setText(mMonthFormatLocalized.format(calendar.getTime()));
        mMonthName.invalidate();

        if (send) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mMonthName.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            } else {
                mMonthName.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
        }

        mFirstDayOfMonth.setTimeInMillis(calendar.getTimeInMillis());
        mFirstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);

        mCurrentMonthDisplayed = calendar.get(Calendar.MONTH);
        mAdapter.setFocusMonth(mCurrentMonthDisplayed);
    }

    private int getWeeksSinceMinDate(Calendar date) {
        if (date.before(mMinDate)) {
            return 0;
        }
        long endTimeMillis = date.getTimeInMillis()
                + date.getTimeZone().getOffset(date.getTimeInMillis());
        long startTimeMillis = mMinDate.getTimeInMillis()
                + mMinDate.getTimeZone().getOffset(mMinDate.getTimeInMillis());
        long dayOffsetMillis = (mMinDate.get(Calendar.DAY_OF_WEEK) - mFirstDayOfWeek)
                * MILLIS_IN_DAY;
        return (int) ((endTimeMillis - startTimeMillis + dayOffsetMillis) / MILLIS_IN_WEEK);
    }

    private class ScrollStateRunnable implements Runnable {

        private AbsListView mView;

        private int mNewState;

        public void doScrollStateChange(AbsListView view, int scrollState) {
            mView = view;
            mNewState = scrollState;
            removeCallbacks(this);
            postDelayed(this, SCROLL_CHANGE_DELAY);
        }

        public void run() {
            mCurrentScrollState = mNewState;
            if (mNewState == OnScrollListener.SCROLL_STATE_IDLE
                    && mPreviousScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                View child = mView.getChildAt(0);
                if (child == null) {
                    return;
                }
                int dist = child.getBottom() - mListScrollTopOffset;
                if (dist > mListScrollTopOffset) {
                    if (mIsScrollingUp) {
                        mView.smoothScrollBy(dist - child.getHeight(), ADJUSTMENT_SCROLL_DURATION);
                    } else {
                        mView.smoothScrollBy(dist, ADJUSTMENT_SCROLL_DURATION);
                    }
                }
            }
            mPreviousScrollState = mNewState;
        }
    }

    private class WeeksAdapter extends BaseAdapter implements OnTouchListener {

        private final Calendar mSelectedDate = Calendar.getInstance(mCurrentLocale);

        private GestureDetector mGestureDetector;

        private int mFocusedMonth;
        private int mSelectedWeek;
        private int mTotalWeekCount;

        public WeeksAdapter(Context context) {
            mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
            init();
        }

        private void init() {
            mSelectedWeek = getWeeksSinceMinDate(mSelectedDate);
            mTotalWeekCount = getWeeksSinceMinDate(mMaxDate);
            if (mMinDate.get(Calendar.DAY_OF_WEEK) != mFirstDayOfWeek
                    || mMaxDate.get(Calendar.DAY_OF_WEEK) != mFirstDayOfWeek) {
                mTotalWeekCount++;
            }
        }

        public void setSelectedDay(Calendar calendar, boolean send) {
            if (calendar.compareTo(mSelectedDate) == 0) {
               return;
            }
            mSelectedDate.setTimeInMillis(calendar.getTimeInMillis());
            mSelectedWeek = getWeeksSinceMinDate(mSelectedDate);
            mFocusedMonth = mSelectedDate.get(Calendar.MONTH);
            notifyDataSetChanged();

            if (send) {
                mListView.setContentDescription(mDateFormatLocalized.format(mSelectedDate.getTime()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    mListView.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                } else {
                    mMonthName.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                }
            }
        }

        public Calendar getSelectedDay() {
            return mSelectedDate;
        }

        @Override
        public int getCount() {
            return mTotalWeekCount;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WeekView weekView;
            if (convertView != null) {
                weekView = (WeekView) convertView;
            } else {
                weekView = new WeekView(mContext);
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                weekView.setLayoutParams(params);
                weekView.setClickable(true);
                weekView.setOnTouchListener(this);
            }

            int selectedWeekDay = (mSelectedWeek == position) ?
                    mSelectedDate.get(Calendar.DAY_OF_WEEK) : -1;
            weekView.init(position, selectedWeekDay, mFocusedMonth);

            return weekView;
        }

        public void setFocusMonth(int month) {
            if (mFocusedMonth == month) {
                return;
            }
            mFocusedMonth = month;
            notifyDataSetChanged();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mListView.isEnabled() && mGestureDetector.onTouchEvent(event)) {
                WeekView weekView = (WeekView) v;
                if (!weekView.getDayFromLocation(event.getX(), mTempDate)) {
                    return true;
                }
                if (mTempDate.before(mMinDate) || mTempDate.after(mMaxDate)) {
                    return true;
                }
                mTempDate.set(Calendar.HOUR_OF_DAY, mSelectedDate.get(Calendar.HOUR_OF_DAY));
                mTempDate.set(Calendar.MINUTE, mSelectedDate.get(Calendar.MINUTE));

                onDateTapped(mTempDate);

                return true;
            }
            return false;
        }

        private void onDateTapped(Calendar day) {
            setSelectedDay(day, true);
            setMonthDisplayed(day, false);
        }

        class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        }
    }

    private class WeekView extends View {

        private final Rect mTempRect = new Rect();
        private final Paint mDrawPaint = new Paint();

        private Calendar mFirstDay;

        private String[] mDayNumbers;
        private boolean[] mFocusDay;

        private int mMonthOfFirstWeekDay = -1;
        private int mLastWeekDayMonth = -1;
        private int mWeek = -1;
        private int mWidth;
        private int mHeight;
        private boolean mHasSelectedDay = false;
        private int mSelectedDay = -1;
        private int mNumCells;
        private int mSelectedLeft = -1;
        private int mSelectedRight = -1;

        public WeekView(Context context) {
            super(context);

            mHeight = (mListView.getHeight() - mListView.getPaddingTop() -
                    mListView.getPaddingBottom()) / mShownWeekCount;
            setPaintProperties();
        }

        public void init(int weekNumber, int selectedWeekDay, int focusedMonth) {
            mSelectedDay = selectedWeekDay;
            mHasSelectedDay = mSelectedDay != -1;
            mNumCells = mShowWeekNumber ? DAYS_PER_WEEK + 1 : DAYS_PER_WEEK;
            mWeek = weekNumber;
            mTempDate.setTimeInMillis(mMinDate.getTimeInMillis());

            mTempDate.add(Calendar.WEEK_OF_YEAR, mWeek);
            mTempDate.setFirstDayOfWeek(mFirstDayOfWeek);

            mDayNumbers = new String[mNumCells];
            mFocusDay = new boolean[mNumCells];

            int i = 0;
            String contentDesc = "";
            if (mShowWeekNumber) {
                mDayNumbers[0] = Integer.toString(mTempDate.get(Calendar.WEEK_OF_YEAR));
                contentDesc = mDayNumbers[0] + ": ";
                i++;
            }

            mTempDate.add(Calendar.DAY_OF_MONTH, mFirstDayOfWeek - mTempDate.get(Calendar.DAY_OF_WEEK));

            mFirstDay = (Calendar) mTempDate.clone();
            mMonthOfFirstWeekDay = mTempDate.get(Calendar.MONTH);

            int d = 0;
            for (; i < mNumCells; i++) {
                mFocusDay[i] = mTempDate.get(Calendar.MONTH) == focusedMonth;

                if (mTempDate.before(mMinDate) || mTempDate.after(mMaxDate)) {
                    mDayNumbers[i] = "";
                } else {
                    mDayNumbers[i] = Integer.toString(mTempDate.get(Calendar.DAY_OF_MONTH));
                    contentDesc = contentDesc +
                            mDayFormatLocalized.format(mFirstDay.getTime()) +
                            (i != mNumCells - 1 ? ", " : "");
                    mFirstDay.add(Calendar.DAY_OF_YEAR, 1);
                    d++;
                }
                mTempDate.add(Calendar.DAY_OF_MONTH, 1);
            }
            mFirstDay.add(Calendar.DAY_OF_YEAR, -d);

            if (mTempDate.get(Calendar.DAY_OF_MONTH) == 1) {
                mTempDate.add(Calendar.DAY_OF_MONTH, -1);
            }
            mLastWeekDayMonth = mTempDate.get(Calendar.MONTH);

            setContentDescription(contentDesc);

            updateSelectionPositions();
        }

        private void setPaintProperties() {
            mDrawPaint.setAntiAlias(true);
            mDrawPaint.setStyle(Style.FILL);
            mDrawPaint.setTextSize(mDateTextSize);
            mDrawPaint.setTextAlign(Align.CENTER);
        }

        public int getMonthOfFirstWeekDay() {
            return mMonthOfFirstWeekDay;
        }

        public int getMonthOfLastWeekDay() {
            return mLastWeekDayMonth;
        }

        public Calendar getFirstDay() {
            return mFirstDay;
        }

        public boolean getDayFromLocation(float x, Calendar outCalendar) {
            int dayStart = mShowWeekNumber ? mWidth / mNumCells : 0;
            if (x < dayStart || x > mWidth) {
                outCalendar.clear();
                return false;
            }

            int dayPosition = (int) ((x - dayStart) * DAYS_PER_WEEK / (mWidth - dayStart));
            outCalendar.setTimeInMillis(mFirstDay.getTimeInMillis());
            outCalendar.add(Calendar.DAY_OF_MONTH, dayPosition);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawBackground(canvas);
            drawWeekNumbers(canvas);
            drawWeekSeparators(canvas);
            drawSelectedDateArrows(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (!mHasSelectedDay) {
                return;
            }

            mDrawPaint.setColor(mSelectedWeekBackgroundColor);

            mTempRect.top = mWeekSeparatorLineWidth;
            mTempRect.bottom = mHeight;
            mTempRect.left = 0;
            mTempRect.right = mSelectedLeft;
            canvas.drawRect(mTempRect, mDrawPaint);

            mTempRect.left = mSelectedRight;
            mTempRect.right = mWidth;
            canvas.drawRect(mTempRect, mDrawPaint);
        }

        private void drawWeekNumbers(Canvas canvas) {
            int i = 0;
            int divisor = 2 * mNumCells;
            int y = (int) ((mHeight + mDrawPaint.getTextSize()) / 2) - mWeekSeparatorLineWidth;
            if (mShowWeekNumber) {
                mDrawPaint.setColor(mHasSelectedDay ? mSelectedWeekNumberColor : mWeekNumberColor);
                mDrawPaint.setFakeBoldText(false);
                int x = mWidth / divisor;
                canvas.drawText(mDayNumbers[0], x, y, mDrawPaint);
                i++;
            }

            mDrawPaint.setFakeBoldText(true);
            for (; i < mNumCells; i++) {
                mDrawPaint.setColor(mFocusDay[i] ?
                        mFocusedMonthDateColor : mUnfocusedMonthDateColor);
                int x = (2 * i + 1) * mWidth / divisor;
                canvas.drawText(mDayNumbers[i], x, y, mDrawPaint);
            }
        }

        private void drawWeekSeparators(Canvas canvas) {
            if (mWeek == 0) {
                return;
            }

            mDrawPaint.setColor(mWeekSeparatorLineColor);
            mDrawPaint.setStrokeWidth(mWeekSeparatorLineWidth);

            canvas.drawLine(0, 0, mWidth, 0, mDrawPaint);
        }

        private void drawSelectedDateArrows(Canvas canvas) {
            if (!mHasSelectedDay) {
                return;
            }

            mDrawPaint.setColor(mSelectedDateArrowsColor);

            Point a = new Point(mSelectedLeft, mWeekSeparatorLineWidth);
            Point b = new Point(mSelectedLeft + mSelectedDateArrowWidth,
                    (mHeight - mWeekSeparatorLineWidth) / 2 + mWeekSeparatorLineWidth);
            Point c = new Point(mSelectedLeft, mHeight);

            for (int i = 0; i < 2; i++) {
                if (i == 1) {
                    a.x = mSelectedRight;
                    b.x = mSelectedRight - mSelectedDateArrowWidth;
                    c.x = mSelectedRight;
                }

                Path path = new Path();
                path.moveTo(a.x, a.y);
                path.lineTo(b.x, b.y);
                path.lineTo(c.x, c.y);
                path.lineTo(a.x, a.y);
                path.close();
                canvas.drawPath(path, mDrawPaint);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mWidth = w;
            updateSelectionPositions();
        }

        private void updateSelectionPositions() {
            if (mHasSelectedDay) {
                int selectedPosition = mSelectedDay - mFirstDayOfWeek;
                if (selectedPosition < 0) {
                    selectedPosition += DAYS_PER_WEEK;
                }
                if (mShowWeekNumber) {
                    selectedPosition++;
                }
                mSelectedLeft = selectedPosition * mWidth / mNumCells;
                mSelectedRight = (selectedPosition + 1) * mWidth / mNumCells;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mHeight);
        }
    }

    private static class SavedState extends BaseSavedState {

        private final Parcelable mListState;
        private final long mSelectedDate;
        private final long mFocusedDate;

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SavedState(Parcelable superState, Parcelable listState, long selectedDate, long focusedDate) {
            super(superState);
            mListState = listState;
            mSelectedDate = selectedDate;
            mFocusedDate = focusedDate;
        }

        private SavedState(Parcel in) {
            super(in);
            mListState = in.readParcelable(null);
            mSelectedDate = in.readLong();
            mFocusedDate = in.readLong();
        }

        public long getSelectedDate() {
            return mSelectedDate;
        }

        public long getFocusedDate() {
            return mFocusedDate;
        }

        public Parcelable getListState() {
            return mListState;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(mListState, 0);
            dest.writeLong(mSelectedDate);
            dest.writeLong(mFocusedDate);
        }
    }
}
