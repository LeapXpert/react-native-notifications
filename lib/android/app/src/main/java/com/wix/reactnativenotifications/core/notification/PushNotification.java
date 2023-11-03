package com.wix.reactnativenotifications.core.notification;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ProxyService;
import com.wix.reactnativenotifications.core.PushHeadlessTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.leolin.shortcutbadger.ShortcutBadger;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.LOGTAG;

import org.json.JSONObject;

public class PushNotification implements IPushNotification {

    static class SoundChannelConfig {
        private final String soundName;
        private final String channelId;
        private final String channelName;

        private SoundChannelConfig(String soundName, String channelName) {
            this.soundName = soundName;
            this.channelId = channelName;
            this.channelName = channelName;
        }

        private static final HashMap<String, SoundChannelConfig> soundConfigMap = new HashMap<String, SoundChannelConfig>() {{
            put("incoming_message_1.wav", new SoundChannelConfig("incoming_message_1", "Incoming message"));
            put("colleague_alert_5.wav", new SoundChannelConfig("colleague_alert_5", "Incoming AM message"));
            put("client_alert_2.wav", new SoundChannelConfig("client_alert_2", "Incoming client message"));
            put("", new SoundChannelConfig("silence", "Silent message"));
        }};

        public String getSoundName() {
            return soundName;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getChannelName() {
            return channelName;
        }

        public static SoundChannelConfig getSoundConfig(String soundName) {
            SoundChannelConfig config = soundConfigMap.get(soundName);
            return config == null ? soundConfigMap.get("") : config;
        }

    }

    final protected Context mContext;
    final protected AppLifecycleFacade mAppLifecycleFacade;
    final protected AppLaunchHelper mAppLaunchHelper;
    final protected JsIOHelper mJsIOHelper;
    final protected PushNotificationProps mNotificationProps;
    final protected AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {
        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };
    final private String DEFAULT_CHANNEL_ID = "channel_01";
    final private String DEFAULT_CHANNEL_NAME = "Channel Name";

    public static IPushNotification get(Context context, Bundle bundle) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getPushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }
        return new PushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper(), new JsIOHelper());
    }

    protected PushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper JsIOHelper) {
        mContext = context;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = JsIOHelper;
        mNotificationProps = createProps(bundle);
        initDefaultChannel(context);
    }

    @Override
    public void onReceived() throws InvalidNotificationException {
        Log.w("onReceived", "is app:" + mAppLifecycleFacade.isAppVisible() + ", isAppDestroyed: " + mAppLifecycleFacade.isAppDestroyed() + ", isReactInitialized: " + mAppLifecycleFacade.isReactInitialized());
        try {
            String raw = mNotificationProps.asBundle().getString("raw");
            JSONObject obj = new JSONObject(raw);
            if (!mAppLifecycleFacade.isAppVisible() && "call_received".equalsIgnoreCase(obj.getString("type"))) {
                handleCallEvent();
                return;
            }
        } catch (Exception e) {
            //
        }
        if (!mAppLifecycleFacade.isAppVisible()) {
            postNotification(null);
            notifyReceivedBackgroundToJS();
        } else {
            notifyReceivedToJS();
        }
    }

    void handleCallEvent() {
        Intent service = new Intent(mContext, PushHeadlessTask.class);
        service.putExtras(mNotificationProps.asBundle());
        try {
//            ComponentName name;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                name = mContext.startForegroundService(service);
//            } else {
//                name = mContext.startService(service);
//            }
            ComponentName name = mContext.startService(service);
            if (name != null) {
                HeadlessJsTaskService.acquireWakeLockNow(mContext);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onOpened() {
        digestNotification();
    }

    @Override
    public int onPostRequest(Integer notificationId) {
        return postNotification(notificationId);
    }

    @Override
    public PushNotificationProps asProps() {
        return mNotificationProps.copy();
    }

    protected int postNotification(Integer notificationId) {
        if (mNotificationProps.isDataOnlyPushNotification()) {
            return -1;
        }
        final PendingIntent pendingIntent = NotificationIntentAdapter.createPendingNotificationIntent(mContext, mNotificationProps);;
        final Notification notification = buildNotification(pendingIntent);
        return postNotification(notification, notificationId);
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }

        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else if (mAppLifecycleFacade.isAppDestroyed()) {
            launchOrResumeApp();
        } else {
            dispatchUponVisibility();
        }
    }

    protected PushNotificationProps createProps(Bundle bundle) {
        return new PushNotificationProps(bundle);
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        notifyOpenedToJS();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected Notification buildNotification(PendingIntent intent) {
        return getNotificationBuilder(intent).build();
    }

    private int getResourceId(String name) {
        return mContext.getResources().getIdentifier(name, "raw", mContext.getPackageName());
    }

    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        String soundName = mNotificationProps.getSound();
        SoundChannelConfig soundChannelConfig = SoundChannelConfig.getSoundConfig(soundName);
        // create channel
        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + mContext.getPackageName() + "/" + getResourceId(soundChannelConfig.getSoundName()));
        createChannel(mContext, soundChannelConfig.getChannelId(), soundChannelConfig.getChannelName(), soundUri);
        final Notification.Builder notification = new Notification.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setContentIntent(intent)
                .setSound(soundUri)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        setUpIcon(notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = soundChannelConfig.getChannelId();
            notification.setChannelId(channelId);
        }

        return notification;
    }

    private void setUpIcon(Notification.Builder notification) {
        int iconResId = getAppResourceId("notification_icon", "drawable");
        if (iconResId != 0) {
            notification.setSmallIcon(iconResId);
        } else {
            notification.setSmallIcon(mContext.getApplicationInfo().icon);
        }

        setUpIconColor(notification);
    }

    private void setUpIconColor(Notification.Builder notification) {
        int colorResID = getAppResourceId("colorAccent", "color");
        if (colorResID != 0) {
            int color = mContext.getResources().getColor(colorResID);
            notification.setColor(color);
        }
    }

    protected int postNotification(Notification notification, Integer notificationId) {
        int id = notificationId != null ? notificationId : createNotificationId(notification);
        postNotification(id, notification);
        return id;
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
        ShortcutBadger.applyCount(mContext, mNotificationProps.getBadge());
    }

    protected int createNotificationId(Notification notification) {
        return (int) System.nanoTime();
    }

    private void notifyReceivedToJS() {
        try {
            Bundle bundle = mNotificationProps.asBundle();
            mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, bundle, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyReceivedToJS: Null pointer exception");
        }
    }

    private void notifyReceivedBackgroundToJS() {
        try {
            Bundle bundle = mNotificationProps.asBundle();
            mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_BACKGROUND_EVENT_NAME, bundle, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyReceivedBackgroundToJS: Null pointer exception");
        }
    }

    private void notifyOpenedToJS() {
        Bundle response = new Bundle();

        try {
            response.putBundle("notification", mNotificationProps.asBundle());
            mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, response, mAppLifecycleFacade.getRunningReactContext());
        } catch (NullPointerException ex) {
            Log.e(LOGTAG, "notifyOpenedToJS: Null pointer exception");
        }
    }

    protected void launchOrResumeApp() {
        if (NotificationIntentAdapter.canHandleTrampolineActivity(mContext)) {
            final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
            mContext.startActivity(intent);
        }
    }

    private int getAppResourceId(String resName, String resType) {
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }

    private void initDefaultChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager.getNotificationChannels().size() == 0) {
                NotificationChannel defaultChannel = new NotificationChannel(
                    DEFAULT_CHANNEL_ID,
                    DEFAULT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                notificationManager.createNotificationChannel(defaultChannel);
            }
        }
    }

    private void createChannel(Context context, String channelId, String channelName, Uri soundUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel defaultChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
        defaultChannel.setSound(soundUri, audioAttributes);
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(defaultChannel);
    }
}
