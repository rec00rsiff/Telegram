package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.vision.Frame;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

public class MediaCalendarActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public boolean isSelectable = false;

    TextPaint selectorTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Paint selectorHolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectorFPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectorInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint transparentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;


    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;
    Callback callback;


    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private int photosVideosTypeFilter;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    TextView bottomOverlayText;
    TextView bottomOverlayTextR;

    MonthView lastTouchedMonth;

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate) {
        super(args);
        this.photosVideosTypeFilter = photosVideosTypeFilter;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 0));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        if(isSelectable) {
            bottomOverlayText = new TextView(context);
            bottomOverlayTextR = new TextView(context);

            FrameLayout bottomOverlay = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int allWidth = MeasureSpec.getSize(widthMeasureSpec);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bottomOverlayText.getLayoutParams();
                    layoutParams.width = allWidth;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                public void onDraw(Canvas canvas) {
                    int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                    Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                    Theme.chat_composeShadowDrawable.draw(canvas);
                    canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.getThemePaint(Theme.key_paint_chatComposeBackground));
                }
            };
            bottomOverlay.setWillNotDraw(false);
            bottomOverlay.setPadding(0, AndroidUtilities.dp(1.5f), 0, 0);
            bottomOverlay.setVisibility(View.VISIBLE);
            bottomOverlay.setClipChildren(false);

            contentView.addView(bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

            bottomOverlayText.setText("SELECT DAYS");
            bottomOverlayText.setTextSize(AndroidUtilities.dp(9));
            bottomOverlayText.setTextColor(getThemedColor(Theme.key_chat_fieldOverlayText));
            bottomOverlayText.setGravity(Gravity.CENTER);
            bottomOverlay.addView(bottomOverlayText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            bottomOverlay.setOnClickListener(view -> {
                switchState();
            });

            bottomOverlayTextR.setText("CLEAR HISTORY");
            bottomOverlayTextR.setTextSize(AndroidUtilities.dp(9));
            bottomOverlayTextR.setTextColor(getThemedColor(Theme.key_chat_reportSpam));
            bottomOverlayTextR.setGravity(Gravity.CENTER);
            bottomOverlay.addView(bottomOverlayTextR, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            bottomOverlayTextR.setVisibility(View.GONE);

        }

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }


        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        return fragmentView;
    }

    public int state = 0;

    public void switchState() {
        if(state == 0) {
            state = 1;
            bottomOverlayText.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    //bottomOverlayText.setVisibility(View.GONE);
                }
            });

            bottomOverlayTextR.setVisibility(View.VISIBLE);
            bottomOverlayTextR.setAlpha(0f);
            bottomOverlayTextR.animate().alpha(1f).setDuration(200);
        }
        else {
            state = 0;
            bottomOverlayTextR.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    //bottomOverlayTextR.setVisibility(View.GONE);
                }
            });
            bottomOverlayText.setVisibility(View.VISIBLE);
            bottomOverlayText.setAlpha(0f);
            bottomOverlayText.animate().alpha(1f).setDuration(200);
        }
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        ArrayList<Integer> selectedDays = new ArrayList<>();
        SparseArray<Pair<RectF, PeriodDay>> cboxesByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }

                if(cboxesByDays != null) {
                    cboxesByDays.clear();
                }

                if(selectedDays != null) {
                    selectedDays.clear();
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;

        int lastTouchedI = -1;
        int startDelFromI = -1;

        /*@Override
        public void onLongPress(MotionEvent event){
            for (int i = 0; i < cboxesByDays.size(); i++) {
                Pair<RectF, PeriodDay> cboxesv = cboxesByDays.valueAt(i);
                if (cboxesv.first.contains(event.getX(), event.getY())) {
                    //ChatActivity fragment=new ChatActivity();
                    //presentFragmentAsPreview(fragment);
                }
            }
        }*/

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                //pressedX = event.getX();
                //pressedY = event.getY();

                if(isSelectable) {
                    for (int i = 0; i < /*imagesByDays*/cboxesByDays.size(); i++) {
                        Pair<RectF, PeriodDay> cboxesv = cboxesByDays.valueAt(i);
                        if (cboxesv.first.contains(event.getX(), event.getY())) {
                            //if i is
                            if(selectedDays.contains(Integer.valueOf(i))) {
                                Log.e("calendar", "startdel set 0");
                                if(i == Collections.min(selectedDays) || i == Collections.max(selectedDays)) {
                                    Log.e("calendar", "startdel set 1, val: " + i);
                                    startDelFromI = i;
                                    return true;
                                }
                            }
                            break;
                        }
                    }

                    startDelFromI = -1;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressed) {
                    for (int i = 0; i < /*imagesByDays*/cboxesByDays.size(); i++) {
                        //pressedX, pressedY
                        Pair<RectF, PeriodDay> cboxesv = cboxesByDays.valueAt(i);
                        if(cboxesv.first.contains(event.getX(), event.getY())) {
                            if(isSelectable) {
                                if(startDelFromI == i && selectedDays.contains(Integer.valueOf(i))) {
                                    Log.e("calendar", "del check pass, value: " + i);
                                    selectedDays.remove(Integer.valueOf(i));
                                    invalidate();
                                    return true;
                                }

                                lastTouchedMonth = this;

                                if(!selectedDays.isEmpty()) {
                                    int smin = Collections.min(selectedDays);
                                    int smax = Collections.max(selectedDays);
                                    if (i < (smin - 1)) {
                                        for (int dxx = i + 1; dxx < smin; ++dxx) {
                                            if (!selectedDays.contains(dxx)) {
                                                selectedDays.add(dxx);
                                            }
                                        }
                                    } else if (i > (smax + 1)) {
                                        for (int dxx = smax + 1; dxx < i; ++dxx) {
                                            if (!selectedDays.contains(dxx)) {
                                                selectedDays.add(dxx);
                                            }
                                        }
                                    }
                                }

                                selectedDays.add(i);
                                invalidate();
                                break;
                            } else {
                                if (callback != null) {
                                    PeriodDay periodDay = cboxesv.second;
                                    if(periodDay != null) {
                                        callback.onDateSelected(periodDay.messageObject.getId(), periodDay.startOffset);
                                        finishFragment();
                                    }

                                    break;
                                }
                            }
                        }
                    }
                }
                pressed = false;
            }
            else if(event.getAction() == MotionEvent.ACTION_MOVE) {
                if(isSelectable) {
                    if (pressed) {
                        for (int i = 0; i < /*imagesByDays*/cboxesByDays.size(); i++) {
                            //keys match, indexes don't
                            Pair<RectF, PeriodDay> cboxesv = cboxesByDays.valueAt(i);
                            if(cboxesv.first.contains(event.getX(), event.getY())) {
                                if(isSelectable) {
                                    if(lastTouchedI == i) {
                                        return true;
                                    }
                                    lastTouchedI = i;

                                    if(startDelFromI != -1 && selectedDays.contains(Integer.valueOf(i))) {
                                        selectedDays.remove(Integer.valueOf(i));
                                        invalidate();
                                        return true;
                                    }

                                    lastTouchedMonth = this;

                                    if(!selectedDays.isEmpty()) {
                                        int smin = Collections.min(selectedDays);
                                        int smax = Collections.max(selectedDays);
                                        if (i < (smin - 1)) {
                                            for (int dxx = i + 1; dxx < smin; ++dxx) {
                                                if (!selectedDays.contains(dxx)) {
                                                    selectedDays.add(dxx);
                                                }
                                            }
                                        } else if (i > (smax + 1)) {
                                            for (int dxx = smax + 1; dxx < i; ++dxx) {
                                                if (!selectedDays.contains(dxx)) {
                                                    selectedDays.add(dxx);
                                                }
                                            }
                                        }
                                    }

                                    selectedDays.add(i);
                                    invalidate();
                                    break;
                                } else {
                                    if (callback != null) {
                                        PeriodDay periodDay = cboxesv.second;
                                        if(periodDay != null) {
                                            callback.onDateSelected(periodDay.messageObject.getId(), periodDay.startOffset);
                                            finishFragment();
                                        }

                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);

                RectF cbox = new RectF(cx - AndroidUtilities.dp(27.5f), cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(27.5f), cy + AndroidUtilities.dp(44) / 2f);
                PeriodDay mdd = null;
                if(messagesByDays != null) {
                    messagesByDays.get(i, null);
                }
                cboxesByDays.put(i, new Pair<RectF, PeriodDay>(cbox, mdd));

                if(selectedDays.contains(i) && !(i == Collections.min(selectedDays) || i == Collections.max(selectedDays))) {
                    selectorInnerPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_checkboxSquareBackground), (int) 59)); //255 full solid
                    canvas.drawRect(cbox, selectorInnerPaint);
                }

                int nowTime = (int) (System.currentTimeMillis() / 1000L);
                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        float img_m = 44;
                        if(selectedDays.contains(i)) {
                            img_m = 38;
                        }
                        else {
                            img_m = 44;
                        }
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(img_m) / 2f, cy - AndroidUtilities.dp(img_m) / 2f, AndroidUtilities.dp(img_m), AndroidUtilities.dp(img_m));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(img_m) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    } else {
                        if(selectedDays.contains(i)) {
                            if(i == Collections.min(selectedDays) || i == Collections.max(selectedDays)) {
                                selectorFPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                                canvas.drawCircle(cx, cy, AndroidUtilities.dp(19), selectorFPaint);
                            }
                        }
                    }

                    if(selectedDays.contains(i)) {
                        if(i == Collections.min(selectedDays) || i == Collections.max(selectedDays)) {
                            selectorHolPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectorHolPaint.setStyle(Paint.Style.STROKE);
                            selectorHolPaint.setStrokeWidth(AndroidUtilities.dp(2));

                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(18) + AndroidUtilities.dp(4) * 1, selectorHolPaint);
                        }
                    }

                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }

                } else {
                    if(selectedDays.contains(i) && (i == Collections.min(selectedDays) || i == Collections.max(selectedDays))) {
                        selectorFPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(19), selectorFPaint);

                        selectorHolPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        selectorHolPaint.setStyle(Paint.Style.STROKE);
                        selectorHolPaint.setStrokeWidth(AndroidUtilities.dp(2));

                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(18) + AndroidUtilities.dp(4) * 1, selectorHolPaint);

                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }
                    else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
        //boolean selected = false;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
