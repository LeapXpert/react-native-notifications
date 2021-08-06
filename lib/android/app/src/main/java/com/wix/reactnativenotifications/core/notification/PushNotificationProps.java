package com.wix.reactnativenotifications.core.notification;

import android.os.Bundle;
import java.util.ArrayList;

public class PushNotificationProps {

    protected Bundle mBundle;

    public PushNotificationProps(Bundle bundle) {
        mBundle = bundle;
    }

    public String getTitle() {
        return getBundleStringFirstNotNull("gcm.notification.title", "title");
    }

    public String getBody() {
        return getBundleStringFirstNotNull("gcm.notification.body", "body");
    }

    public String getChannelId() {
        return getBundleStringFirstNotNull("gcm.notification.android_channel_id", "android_channel_id");
    }

    public Boolean getIsOngoing() { return mBundle.getBoolean("isOngoing"); }

    public ArrayList<String> getActions() { return mBundle.getStringArrayList("actions"); }

    public void setAction(String action) { mBundle.putString("action", action); }

    public String getAction() { return mBundle.getString("action"); }

    public Bundle asBundle() {
        return (Bundle) mBundle.clone();
    }

    public boolean isFirebaseBackgroundPayload() {
        return mBundle.containsKey("google.message_id");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (String key : mBundle.keySet()) {
            sb.append(key).append("=").append(mBundle.get(key)).append(", ");
        }
        return sb.toString();
    }

    protected PushNotificationProps copy() {
        return new PushNotificationProps((Bundle) mBundle.clone());
    }

    private String getBundleStringFirstNotNull(String key1, String key2) {
        String result = mBundle.getString(key1);
        return result == null ? mBundle.getString(key2) : result;
    }
}
