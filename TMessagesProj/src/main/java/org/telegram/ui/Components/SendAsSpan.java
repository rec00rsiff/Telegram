/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ShareDialogCell;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;

public class SendAsSpan extends View {

    private long uid;
    private String key;
    private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable deleteDrawable;
    private RectF rect = new RectF();
    private ImageReceiver imageReceiver;
    private StaticLayout nameLayout;
    private AvatarDrawable avatarDrawable;
    private ContactsController.Contact currentContact;
    public int currentAccount = UserConfig.selectedAccount;
    public Context icontext;
    private int textWidth;
    private float textX;
    private float progress;
    private boolean deleting;
    private long lastUpdateTime;
    private int[] colors = new int[8];

    public Activity parentActivity;
    public Theme.ResourcesProvider resourcesProvider;
    public View vv;

    private ArrayList<TLRPC.Peer> s_peers = new ArrayList<TLRPC.Peer>();
    TLRPC.Peer def_peer;

    private ActionBarPopupWindow popupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;

    public SendAsSpan(Context context, Object object) {
        this(context, object, null);
    }

    public SendAsSpan(Context context, ContactsController.Contact contact) {
        this(context, null, contact);
    }

    public SendAsSpan(Context context, Object object, ContactsController.Contact contact) {
        super(context);

        icontext = context;
        currentContact = contact;
        deleteDrawable = getResources().getDrawable(R.drawable.delete);
        textPaint.setTextSize(AndroidUtilities.dp(14));

        String firstName;

        ImageLocation imageLocation;
        Object imageParent;

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        if (object instanceof String) {
            imageLocation = null;
            imageParent = null;
            String str = (String) object;
            avatarDrawable.setSmallSize(true);
            switch (str) {
                case "contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS);
                    uid = Integer.MIN_VALUE;
                    firstName = LocaleController.getString("FilterContacts", R.string.FilterContacts);
                    break;
                case "non_contacts":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS);
                    uid = Integer.MIN_VALUE + 1;
                    firstName = LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts);
                    break;
                case "groups":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
                    uid = Integer.MIN_VALUE + 2;
                    firstName = LocaleController.getString("FilterGroups", R.string.FilterGroups);
                    break;
                case "channels":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS);
                    uid = Integer.MIN_VALUE + 3;
                    firstName = LocaleController.getString("FilterChannels", R.string.FilterChannels);
                    break;
                case "bots":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_BOTS);
                    uid = Integer.MIN_VALUE + 4;
                    firstName = LocaleController.getString("FilterBots", R.string.FilterBots);
                    break;
                case "muted":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_MUTED);
                    uid = Integer.MIN_VALUE + 5;
                    firstName = LocaleController.getString("FilterMuted", R.string.FilterMuted);
                    break;
                case "read":
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_READ);
                    uid = Integer.MIN_VALUE + 6;
                    firstName = LocaleController.getString("FilterRead", R.string.FilterRead);
                    break;
                case "archived":
                default:
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED);
                    uid = Integer.MIN_VALUE + 7;
                    firstName = LocaleController.getString("FilterArchived", R.string.FilterArchived);
                    break;
            }
        } else if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            uid = user.id;
            if (UserObject.isReplyUser(user)) {
                firstName = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                avatarDrawable.setSmallSize(true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                imageLocation = null;
                imageParent = null;
            } else if (UserObject.isUserSelf(user)) {
                firstName = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                avatarDrawable.setSmallSize(true);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                imageLocation = null;
                imageParent = null;
            } else {
                avatarDrawable.setInfo(user);
                firstName = UserObject.getFirstName(user);
                imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
                imageParent = user;
            }
        } else if (object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            avatarDrawable.setInfo(chat);
            uid = -chat.id;
            firstName = chat.title;
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            imageParent = chat;
        } else {
            avatarDrawable.setInfo(0, contact.first_name, contact.last_name);
            uid = contact.contact_id;
            key = contact.key;
            if (!TextUtils.isEmpty(contact.first_name)) {
                firstName = contact.first_name;
            } else {
                firstName = contact.last_name;
            }
            imageLocation = null;
            imageParent = null;
        }

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        int maxNameWidth;
        if (AndroidUtilities.isTablet()) {
            maxNameWidth = AndroidUtilities.dp(530 - 32 - 18 - 57 * 2) / 2;
        } else {
            maxNameWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(32 + 18 + 57 * 2)) / 2;
        }

        CharSequence name = TextUtils.ellipsize(firstName.replace('\n', ' '), textPaint, maxNameWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(name, textPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        if (nameLayout.getLineCount() > 0) {
            textWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
            textX = -nameLayout.getLineLeft(0);
        }
        imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1);
        updateColors();


    }

    public boolean last_kayboard_shown = false;

    public void initPopup()
    {
        if (popupLayout == null) {
            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider, 1);
            popupLayout.setAnimationEnabled(true);
            popupLayout.setShownFromBotton(true);
            popupLayout.setFitItems(true);

            popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            popupWindow.setAnimationEnabled(false);
            popupWindow.setAnimationStyle(R.style.PopupAnimation);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupWindow.getContentView().setFocusableInTouchMode(true);

            popupWindow.c_dlistener = () -> cancelDeleteAnimation();

            vv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
                public void onGlobalLayout(){
                    android.graphics.Rect r = new android.graphics.Rect();
                    vv.getRootView().getWindowVisibleDisplayFrame(r);
                    int heightDiff = r.height() - vv.getRootView().getHeight();

                    boolean keyboard_shown = false;
                    InputMethodManager imm = (InputMethodManager) parentActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(imm.isAcceptingText()) {
                        keyboard_shown = true;
                    }
                    else {
                        keyboard_shown = false;
                    }

                    if(last_kayboard_shown != keyboard_shown) {
                        popupWindow.dismiss();
                    }
                    last_kayboard_shown = keyboard_shown;
                }
            });
        }
    }

    public void updateColors() {
        int color = avatarDrawable.getColor();
        int back = Theme.getColor(Theme.key_groupcreate_spanBackground);
        int delete = Theme.getColor(Theme.key_groupcreate_spanDelete);
        colors[0] = Color.red(back);
        colors[1] = Color.red(color);
        colors[2] = Color.green(back);
        colors[3] = Color.green(color);
        colors[4] = Color.blue(back);
        colors[5] = Color.blue(color);
        colors[6] = Color.alpha(back);
        colors[7] = Color.alpha(color);
        deleteDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
        backPaint.setColor(back);
    }

    public boolean isDeleting() {
        return deleting;
    }

    public TLRPC.Peer getDefPeer()
    {
        return def_peer;
    }

    public TLRPC.InputPeer getSendAsPeer() {
        if(getDefPeer() != null) {
            long vchann_id = getDefPeer().channel_id;
            long vchat_id = getDefPeer().chat_id;
            long vuser_id = getDefPeer().user_id;

            if(vchann_id != 0) {
                return MessagesController.getInstance(currentAccount).getInputPeer(-vchann_id);
            }
            else if(vchat_id != 0) {
                return MessagesController.getInstance(currentAccount).getInputPeer(-vchat_id);
            }
            else if(vuser_id != 0) {
                return MessagesController.getInstance(currentAccount).getInputPeer(vuser_id);
            }
        }
        else {
            return null;
        }

        return null;
    }

    public void setDefPeer(TLRPC.Peer peer)
    {
        if(s_peers.isEmpty()) {
            setPeers(new ArrayList<TLRPC.Peer>(Arrays.asList(peer)));

            int new_index = 0;
            for(TLRPC.Peer p : s_peers) {
                if(MessageObject.getPeerId(peer) == MessageObject.getPeerId(p)) {
                    break;
                }
                new_index++;
            }

            GroupCreateUserCell new_cell = (GroupCreateUserCell) popupLayout.getvItemAt(new_index + 1);
            if(new_cell != null) {
                new_cell.setChecked(true, true);
            }
        }
        else {
            int prev_index = 0;
            for(TLRPC.Peer p : s_peers) {
                if(MessageObject.getPeerId(def_peer) == MessageObject.getPeerId(p)) {
                    break;
                }
                prev_index++;
            }

            int new_index = 0;
            for(TLRPC.Peer p : s_peers) {
                if(MessageObject.getPeerId(peer) == MessageObject.getPeerId(p)) {
                    break;
                }
                new_index++;
            }


            GroupCreateUserCell prev_cell = (GroupCreateUserCell) popupLayout.getvItemAt(prev_index + 1);
            GroupCreateUserCell new_cell = (GroupCreateUserCell) popupLayout.getvItemAt(new_index + 1);

            if(prev_cell != null) {
                prev_cell.setChecked(false, true);
            }
            if(new_cell != null) {
                new_cell.setChecked(true, true);
            }
        }

        def_peer = peer;

        TLRPC.InputPeer ipeer = getSendAsPeer();
        if(ipeer != null) {
            MessagesController.getInstance(currentAccount).saveDefSendAs(ipeer);
        }

        ImageLocation imageLocation;
        Object imageParent;

        long did = MessageObject.getPeerId(peer);
        TLObject object;
        String status;
        if (did > 0) {
            object = MessagesController.getInstance(currentAccount).getUser(did);
            status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
        } else {
            object = MessagesController.getInstance(currentAccount).getChat(-did);
            status = null;
        }


        avatarDrawable.setInfo(object);
        imageLocation = ImageLocation.getForUserOrChat(object, ImageLocation.TYPE_SMALL);
        imageParent = object;

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        int maxNameWidth;
        if (AndroidUtilities.isTablet()) {
            maxNameWidth = AndroidUtilities.dp(530 - 32 - 18 - 57 * 2) / 2;
        } else {
            maxNameWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(32 + 18 + 57 * 2)) / 2;
        }
        imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1);
        updateColors();
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void setPeers(ArrayList<TLRPC.Peer> peers)
    {
        popupLayout.removeInnerViews();

        GroupCreateUserCell hcell = new GroupCreateUserCell(icontext, 2, 0, false, false);
        hcell.currentName = "@_@";
        popupLayout.addView(hcell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        s_peers = peers;

        for(TLRPC.Peer p : peers)
        {
            if(p == null){
                continue;
            }
            long did = MessageObject.getPeerId(p);
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }

            GroupCreateUserCell cell = new GroupCreateUserCell(icontext, 2, 0, false, false);
            cell.setObject(object, null, status, false/*position != getItemCount() - 1*/);
            cell.setChecked(MessageObject.getPeerId(def_peer) == MessageObject.getPeerId(p), true);

            cell.setOnClickListener(v -> {
                GroupCreateUserCell cv = (GroupCreateUserCell)v;
                setDefPeer(p);
            });

            popupLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        requestLayout();
    }

    public void startDeleteAnimation() {
        if (deleting) {
            return;
        }
        deleting = true;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();

        if (popupLayout == null) {
            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider, 1);
            popupLayout.setAnimationEnabled(false);
            popupLayout.setShownFromBotton(false);
            popupLayout.setFitItems(false);

            popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            popupWindow.setAnimationEnabled(false);
            popupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupWindow.getContentView().setFocusableInTouchMode(true);

            popupWindow.c_dlistener = () -> cancelDeleteAnimation();
        }

        int[] loc = new int[2];
        this.getLocationInWindow(loc);

        int dh = Resources.getSystem().getDisplayMetrics().heightPixels;
        int du_y = dh - loc[1];
        int f_y = du_y + (int)Math.floor(this.getHeight() * 2.82);

        popupWindow.setHeight(AndroidUtilities.dp(399));
        if(AndroidUtilities.dp(399) > (dh - f_y - 1))
        {
            popupWindow.setHeight(dh - f_y - 1);
        }
        popupWindow.setFocusable(true);
        popupWindow.showAtLocation(this, Gravity.LEFT | Gravity.BOTTOM, 0, f_y);
        popupWindow.dimBehind();
    }

    public void cancelDeleteAnimation() {
        if (!deleting) {
            return;
        }
        deleting = false;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();

        popupWindow.dismiss();
    }

    public long getUid() {
        return uid;
    }

    public String getKey() {
        return key;
    }

    public ContactsController.Contact getContact() {
        return currentContact;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(32 ), AndroidUtilities.dp(32));

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (deleting && progress != 1.0f || !deleting && progress != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if (deleting) {
                progress += dt / 120.0f;
                if (progress >= 1.0f) {
                    progress = 1.0f;
                }
            } else {
                progress -= dt / 120.0f;
                if (progress < 0.0f) {
                    progress = 0.0f;
                }
            }
            invalidate();
        }
        canvas.save();
        rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(32));
        backPaint.setColor(Color.argb(colors[6] + (int) ((colors[7] - colors[6]) * progress), colors[0] + (int) ((colors[1] - colors[0]) * progress), colors[2] + (int) ((colors[3] - colors[2]) * progress), colors[4] + (int) ((colors[5] - colors[4]) * progress)));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backPaint);
        imageReceiver.draw(canvas);
        if (progress != 0) {
            int color = avatarDrawable.getColor();
            float alpha = Color.alpha(color) / 255.0f;
            backPaint.setColor(color);
            backPaint.setAlpha((int) (255 * progress * alpha));
            canvas.drawCircle(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), backPaint);
            canvas.save();
            canvas.rotate(45 * (1.0f - progress), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            deleteDrawable.setBounds(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            deleteDrawable.setAlpha((int) (255 * progress));
            deleteDrawable.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(nameLayout.getText());
        if (isDeleting() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.getId(), LocaleController.getString("Delete", R.string.Delete)));
    }
}
